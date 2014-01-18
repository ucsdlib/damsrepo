package edu.ucsd.library.dams.solr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.SolrServerException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import edu.ucsd.library.dams.util.HttpUtil;

/**
 * Solr indexer utility, using ConcurrentUpdateSolrServer for efficient 
 * background interaction with the Solr server.  This indexer has two modes:
 * It can either take a list of IDs to index from a file (one ID per line), or
 * it can listen to an ActiveMQ queue and extract IDs from the messages.
 * By default, a buffer size of 10MB and two worker threads are used.  These
 * can be overridden using the SolrIndexer.bufSize and SolrIndexer.threadCount
 * System properties.
 * @author escowles
**/
public class SolrIndexer implements MessageListener
{
	private String xmlBaseURL;
	private String solrBaseURL;
	private SolrServer solr;
	private static int BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB buffer
	private static int THREAD_COUNT = 2; // worker threads
	private static SimpleDateFormat dateFormat = new SimpleDateFormat(
		"yyyy-MM-dd'T'hh:mm:ssZ"
	);

	/**
	 * Constructor with supplied base URLs for Solr and retrieving the XML for
	 * each object to be indexed.
	 * @param xmlBaseURL Base URL for retrieving Solr XML for a record.  The
	 *   URL is formed by appending the ID to the base URL.
	 * @param solrBaseURL Base URL for the Solr server.  For multi-core
	 *   indexing, this base URL should include the core name.
	**/
	public SolrIndexer( String xmlBaseURL, String solrBaseURL )
	{
		this.xmlBaseURL = xmlBaseURL;
		this.solrBaseURL = solrBaseURL;
		this.solr = new ConcurrentUpdateSolrServer(
			solrBaseURL, BUFFER_SIZE, THREAD_COUNT
		);

		// look for system properties to override buffer/thread defaults
		BUFFER_SIZE = intProperty("SolrIndexer.bufSize",BUFFER_SIZE);
		THREAD_COUNT = intProperty("SolrIndexer.threadCount",THREAD_COUNT);
	}
	private static int intProperty( String name, int defaultValue )
	{
		int i = defaultValue;
		String val = System.getProperty(name);
		if ( val != null )
		{
			try { i = Integer.parseInt(val); }
			catch ( Exception ex )
			{
				System.err.println("Error parsing " + name + " value: " + val);
			}
		}
		return i;
	}

	/**
	 * Delete a record from the Solr index.
	 * @param pid The record ID.
     * @throws SolrServerException When there is an error in the remote Solr
	 *  server.
	 * @throws IOException When there is a low-level I/O error, e.g. unable to
	 *  connect to the Solr server.
	**/
	public void deleteObject( String pid )
		throws SolrServerException, IOException
	{
		solr.deleteById(pid);
	}
	/**
	 * Retrieve the Solr XML for a record and update the Solr index.
	 * @param pid The record ID.
	 * @throws SolrServerException When there is an error in the remote Solr
	 *  server.
	 * @throws IOException When there is a low-level I/O error, e.g. unable to
	 *  connect to the Solr server, or if the Solr XML cannot be retrieved.
	 * @throws DocumentException When there is an error parsing the Solr XML.
	**/
	public void updateObject( String pid )
		throws SolrServerException, IOException, DocumentException
	{
		// fetch solr xml
		HttpUtil http = new HttpUtil( xmlBaseURL + pid );
		int status = http.exec(60);
		if ( status != 200 )
		{
			throw new IOException(
				"Error retrieving " + pid + ": "
					+ http.response().getStatusLine().toString()
			);
		}

		String solrxml = http.contentBodyAsString();

		// parse & build SolrInputDocument
		SolrInputDocument solrdoc = new SolrInputDocument();
		Document xmldoc = DocumentHelper.parseText(solrxml);
		List fields = xmldoc.selectNodes( "/add/doc/field" );
		for ( int i = 0; i < fields.size(); i++ )
		{
			Element n = (Element)fields.get(i);
			String name = n.attributeValue("name");
			solrdoc.addField( name, n.getText() );
			if ( name.equals("id") )
			{
				solrdoc.addField( "id_t", n.getText() );
			}
		}

		// add the doc to solr
		solr.add( solrdoc );
	}
	/**
	 * Commit changes to the Solr server.
	 * @throws SolrServerException When there is an error in the remote Solr
	 *  server.
	 * @throws IOException When there is a low-level I/O error, e.g. unable to
	 *  connect to the Solr server.
	**/
	public void commit() throws SolrServerException, IOException
	{
		solr.commit();
	}
	/**
	 * Handle JMS messages.
	 * @param message JMS message, assumed to be in Fedora 3 format: with the
	 *  record ID in the property "pid" and the type of operation in the 
	 *  property "methodName".
	**/
	public void onMessage( Message message )
	{
		try
		{
			String pid = message.getStringProperty("pid");
			String method = message.getStringProperty("methodName");
			long start = System.currentTimeMillis();
			if ( method.equals("purgeObject") )
			{
				System.out.print( timestamp() + " delete: " + pid);
				deleteObject(pid);
			}
			else
			{
				System.out.print( timestamp() + " update: " + pid);
				updateObject(pid);
			}
			long dur = System.currentTimeMillis() - start;
			System.out.println(" OK (" + dur + "ms)");
		}
		catch ( Exception ex )
		{
			System.out.println(" ERR: " + ex.toString());
			ex.printStackTrace();
		}
	}
	/**
 	 * Command-line operation.
	 * Usage:
	 * SolrIndexer [xmlBaseURL] [solrBaseURL] [idFile]
	 * SolrIndexer [xmlBaseURL] [solrBaseURL] [jmsQueueURL] [jmsQueueName]
	**/
	public static void main( String[] args ) throws Exception
	{
		String xmlBaseURL = args[0];
		String solr = args[1];
		SolrIndexer indexer = new SolrIndexer(xmlBaseURL,solr);

		File idList = new File( args[2] );
		if ( idList.exists() )
		{
			// read record ids from a file
			List<String> ids = new ArrayList<String>();
			BufferedReader buf = new BufferedReader( new FileReader(idList) );
			for ( String ark = null; (ark = buf.readLine()) != null; )
			{
				ids.add(ark);
			}
			indexer.batchIndex(ids);
			System.exit(0);
		}
		else if ( args[2] != null && args[2].startsWith("tcp://") )
		{
			// listen for records on JMS queue
			String queueUrl = args[2];
			String queueName = args[3];

			// connect
			ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
				queueUrl
			);
			Connection con = factory.createConnection();
			con.start();
			Session session = con.createSession( false, Session.AUTO_ACKNOWLEDGE );

			// setup listener
			ActiveMQTopic topic = new ActiveMQTopic( queueName );
			MessageConsumer consumer = session.createConsumer(topic);
			consumer.setMessageListener( new SolrIndexer(xmlBaseURL,solr) );

			System.out.println(
				timestamp() + " SolrIndexer listening for events..."
			);
			InputStreamReader in = new InputStreamReader( System.in );
			while ( ((char)in.read()) != 'c' ) { }
		}
		else
		{
			// treat the rest of the args as ids and index them
			List<String> ids = new ArrayList<String>();
			for ( int i = 2; i < args.length; i++ )
			{
				ids.add(args[i]);
			}
			indexer.batchIndex(ids);
			System.exit(0);
		}
	}
	private void batchIndex( List<String> ids )
	{
		// read record ids from a file
		long totalDur = 0L;
		ArrayList<String> errors = new ArrayList<String>();
		for ( int i = 0; i < ids.size(); i++ )
		{
			long start = System.currentTimeMillis();
			String ark = ids.get(i);
			System.out.print( timestamp() + "SolrIndexer: " + ark );
			boolean success = false;
			Exception ex = null;
			try
			{
				updateObject( ark );
				success = true;
			}
			catch ( Exception e )
			{
				success = false;
				errors.add(ark);
				ex = e;
			}
			long dur = System.currentTimeMillis() - start;
			totalDur += dur;
			if ( success )
			{
				System.out.print(" OK");
			}
			else
			{
				System.out.print(" ERR");
			}
			System.out.println(
				" (" + (i+1) + " of " + ids.size() + "), "
				+ errors.size() + " errors, " + ((float)dur/1000) + " sec"
			);
			if ( ex != null ) { System.out.println( ex.getMessage() ); }
		}

		// commit updates
		try
		{
			long start = System.currentTimeMillis();
			commit();
			long dur = System.currentTimeMillis() - start;
			totalDur += dur;
		}
		catch ( Exception ex )
		{
			System.err.println("Error committing updates");
			ex.printStackTrace();
		}
		System.out.println(
			"indexing time: " + ((float)totalDur/1000) + " sec"
		);
		if ( errors.size() > 0 ) { System.out.println("errors:"); }
		for ( int i = 0; i < errors.size(); i++ )
		{
			System.out.println( errors.get(i) );
		}
	}
	private static String timestamp()
	{
		return dateFormat.format( new Date() );
	}
}
