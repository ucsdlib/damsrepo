package edu.ucsd.library.dams.solr;

import java.io.InputStreamReader;
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
	private static int BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB
	private static int THREAD_COUNT = 3; // worker threads
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
		String solrxml = HttpUtil.get( damspas + "/" + pid + "/solr" );

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

		// temporarily force commits
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
				deleteObject(pid);
			}
			else
			{
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
		String queueUrl = args[0];
		String queueName = args[1];
		String damspas = args[2];
		String solr = args[3];

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
