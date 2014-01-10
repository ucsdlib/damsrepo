package edu.ucsd.library.dams.solr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import edu.ucsd.library.dams.util.HttpUtil;

/**
 * ActiveMQ event listever that indexes DAMS objects in Solr.
 * @author escowles
**/
public class SolrIndexer implements MessageListener
{
	private String damspas;
	private String solrUrl;
	private SolrServer solr;
	private static int BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB buffer
	private static int THREAD_COUNT = 2; // worker threads
	public SolrIndexer( String damspas, String solrUrl )
	{
		this.damspas = damspas;
		this.solrUrl = solrUrl;
		this.solr = new ConcurrentUpdateSolrServer(
			solrUrl, BUFFER_SIZE, THREAD_COUNT
		);
	}
	public void deleteObject( String pid ) throws Exception
	{
		solr.deleteById(pid);
	}
	public void updateObject( String pid ) throws Exception
	{
		// fetch solr xml from damspas
		HttpUtil http = new HttpUtil( damspas + "/solrdoc/" + pid );
		int status = http.exec();
		if ( status != 200 )
		{
			throw new Exception(
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
	public void commit() throws Exception
	{
		solr.commit();
	}
	public void onMessage( Message message )
	{
		try
		{
			String pid = message.getStringProperty("pid");
			String method = message.getStringProperty("methodName");
			if ( method.equals("purgeObject") )
			{
				System.out.println("delete: " + pid);
				deleteObject(pid);
			}
			else
			{
				System.out.println("update: " + pid);
				updateObject(pid);
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
	}
	public static void main( String[] args ) throws Exception
	{
		String damspas = args[0];
		String solr = args[1];
		SolrIndexer indexer = new SolrIndexer(damspas,solr);

		File idList = new File( args[2] );
		if ( idList.exists() )
		{
			// read record ids from a file
			
			BufferedReader buf = new BufferedReader( new FileReader(idList) );
			long totalDur = 0L;
			int indexed = 0;
			ArrayList<String> errors = new ArrayList<String>();
			for ( String ark = null; (ark = buf.readLine()) != null; )
			{
				long start = System.currentTimeMillis();
				System.out.print( "SolrIndexer: " + ark );
				boolean success = false;
				Exception ex = null;
				try
				{
					indexed++;
					indexer.updateObject( ark );
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
					" (" + indexed + "), " + errors.size() + " errors, "
					+ ((float)dur/1000) + " sec"
				);
				if ( ex != null ) { System.out.println( ex.getMessage() ); }
			}
			indexer.commit();
			System.out.println(
				"indexing time: " + ((float)totalDur/1000) + " sec"
			);
			if ( errors.size() > 0 ) { System.out.println("errors:"); }
			for ( int i = 0; i < errors.size(); i++ )
			{
				System.out.println( errors.get(i) );
			}
			System.exit(0);
		}
		else
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
			consumer.setMessageListener( new SolrIndexer(damspas,solr) );

			System.out.println("SolrIndexer listening for events...");
			InputStreamReader in = new InputStreamReader( System.in );
			while ( ((char)in.read()) != 'c' ) { }
		}
	}
}
