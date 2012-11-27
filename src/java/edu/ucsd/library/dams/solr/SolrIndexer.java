package edu.ucsd.library.dams.solr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

// dom4j imports
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

// apache imports
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;

// solr import
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.UpdateResponse;

// local imports
import edu.ucsd.library.dams.util.DateParser;
import edu.ucsd.library.dams.util.PDFParser;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreUtil;
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Application to index triples in a Lucene/Solr index, retrieving data from
 * a triplestore.  Uses XSL for all indexing beyond basic values
 * (id, datasource, date indexed).
 * @author escowles@ucsd.edu
**/
public class SolrIndexer
{
	// log
	private static Logger log = Logger.getLogger( SolrIndexer.class );

	// connection info
	protected TripleStore ts;
	protected String datasource;
	private SolrHelper solr;
	private String solrBase;
	private String solrCore;
	private StringBuffer postBuffer;
	private HttpSolrServer streamingServer = null;
	private boolean streaming = false;

	// namespace/collection caches
	private Map<String,String> nsmap;
	private Map<String,HashMap<String,String>> collectionData;
	private Map<String,String> predicates;
	private Map<String,String> colNames;
	private Map<String,String> colHier;

	// status tracking and reporting
	private long start;
	private static int batchesIndexed = 0;
	private int recordsIndexed;
	private NumberFormat numFmt;
	private static SimpleDateFormat gmtFormat = new SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
	);
	private static SimpleDateFormat enteredFormat = new SimpleDateFormat(
		"yyyy-MM-dd'T'HH:mm:ssZ"
	);

	// options
	private boolean autoCommit = true;
	private boolean debug = false;
	private boolean status = false;
	private boolean autoOptimize = true;
	private static int postLimit = 2400000;
	private static int batchLimit = 100;

	// xsl
	private List<File> xslFiles = null;
	private XSLIndexer xslIndexer = null;
	private XMLWriter xmlWriter = null;

	// file i/o
	private File fulltextDir = null;
	private File exportDir = null;
	private FileStore fs = null;

	// error reporting
	private boolean throwExceptions = true;

	// rights md tracking
	private boolean rightsFound = false;

	/**
	 * Constructor which used TripleStore.getName() for both the datasource
	 * and Solr core name.
	 * @param ts TripleStore object to retrieve data from.
	 * @param solrBase Base URL for Solr web service.
	**/
	public SolrIndexer( TripleStore ts, String solrBase,
		Map<String,String> nsmap) throws MalformedURLException
	{
		this( solrBase, ts.name(), nsmap );
		setDatasource( ts, ts.name(), ts.name() );
	}
	/**
	 * Constructor with datasource info.
	 * @param ts TripleStore object to retrieve data from.
	 * @param solrBase Base URL for Solr web service.
	 * @param solrCore Solr core name.
	 * @param datasource Datasource name (e.g., "jdbc/dams")
	**/
	public SolrIndexer( TripleStore ts, String solrBase, String solrCore,
		String datasource, Map<String,String> nsmap )
		throws MalformedURLException
	{
		this( solrBase, solrCore, nsmap );
		setDatasource( ts, datasource, solrCore );
	}

	/**
	 * Constructor without datasource info.
	 * @param solrBase Base URL for Solr web service.
	 * @param solrCore Solr core name.
	**/
	public SolrIndexer( String solrBase, String solrCore,
		Map<String,String> nsmap ) throws MalformedURLException
	{
		// start timer
		start = System.currentTimeMillis();
		recordsIndexed = 0;

		// variables
		this.solrBase = solrBase;
		this.solrCore = solrCore;
		this.nsmap = nsmap;
		this.postBuffer = new StringBuffer("<add>");

		// initialize Solr
		if ( !debug ) { solr = new SolrHelper( solrBase ); }

		// namespace/collection caches
		predicates = new HashMap<String,String>();
		collectionData = new HashMap<String,HashMap<String,String>>();
		colNames = new HashMap<String,String>();
		colHier = new HashMap<String,String>();

		// reporting
		numFmt = NumberFormat.getNumberInstance();
		numFmt.setMaximumFractionDigits(2);
		numFmt.setMinimumFractionDigits(2);

		// xsl
		xslFiles = new ArrayList<File>();
		try
		{
			OutputFormat of = new OutputFormat();
			of.setSuppressDeclaration(true);
			xmlWriter = new XMLWriter(of);
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}

		// update streaming server
		if ( streaming ) { updateStreamingServer(); }
	}
	public int getRecordsIndexed() { return recordsIndexed; }

	/*** options ************************************************************/

	/**
	 * Change to a new datasource.
	 * @param agraph AllegroGraph object to retrieve data from.
	 * @param datasource Solr datasource name.
	**/
	public void setDatasource( TripleStore ts, 
		String datasource, String solrCore ) throws MalformedURLException
	{
		// change if ds params have changed
		if ( this.ts == null      || this.ts.equals( ts )
			|| datasource == null || datasource.equals( datasource )
			|| solrCore == null   || solrCore.equals( solrCore )   )
		{
			// update variables
			this.ts = ts;
			this.datasource = datasource;
			this.solrCore = solrCore;

			// clear predicate/collection caches
			predicates.clear();
			collectionData.clear();
			colNames.clear();
			colHier.clear();

			// update streaming server
			if ( streaming ) { updateStreamingServer(); }
		}
	}
	public String getSolrBase() { return solrBase; }
	public String getSolrCore() { return solrCore; }
	public void setSolrCore(String solrCore) throws MalformedURLException
	{
		if ( this.solrCore == null || !this.solrCore.equals(solrCore) )
		{
			this.solrCore = solrCore;
			if ( streaming ) { updateStreamingServer(); }
		}
	}

	private void updateStreamingServer() throws MalformedURLException
	{
		int threads = 5;
		streamingServer = new HttpSolrServer(
			solrBase + solrCore
		);
		streamingServer.setDefaultMaxConnectionsPerHost( threads );
	}

	/**
	 * Set streaming on or off.
	 * @param b If true, use SolrJ's HttpSolrServer to send data to
	 * Solr.  If false, data is sent as XML using normal HTTP calls.
	**/
	public void setStreaming( boolean b )
	{
		this.streaming = b;
	}
	/**
	 * Set auto-commit on or off.
	 * @param b If true, automatically commit after each batch is posted.  If
	 * false, updates must be commited by calling the commit() method.
	**/
	public void setAutoCommit( boolean b )
	{
		this.autoCommit = b;
	}
	/**
	 * Set auto-optimize on or off.
	 * @param b If true, automatically optimize after 50 batches are posted.  If
	 * false, the index must be optimized manually.
	**/
	public void setAutoOptimize( boolean b )
	{
		this.autoOptimize = b;
	}

	/**
	 * Enable or disable debug output.  When debug output is enabled, no data
	 * is sent to the Solr server.
	 * @param b If true, debug output is enabled.
	**/
	public void setDebug( boolean b )
	{
		this.debug = b;
	}
	public void setStatus( boolean b )
	{
		this.status = b;
	}

	/**
	 * Enable or disable throwing exceptions for individual indexing errors.
	**/
	public void setThrowExceptions( boolean b )
	{
		throwExceptions = b;
	}

	/**
	 * Set the export directory -- when this directory is set, output will be
	 * written to XML files in this directory instead of being sent to the Solr
	 * server.
	 * @param f The directory to write files in.
	**/
	public void setExportDir( File f )
	{
		this.exportDir = f;
	}

	/**
	 * Set the fulltext content directory -- when this directory is set, the
	 * indexer will look for files in this directory with the same name as the
	 * subject ark, and read text from that file if found.
	 * @param f Directory containing fulltext content files.
	**/
	public void setFulltextDir( File f )
	{
		this.fulltextDir = f;
	}

	/**
	 * Set the FileStore object to retrieve PDFs from.
	**/
	public void setFileStore( FileStore fs )
	{
		this.fs = fs;
	}


	/**
	 * Set the POST limit -- when the POST buffer exceeds this number, a batch
	 * will be sent to the Solr server.
	 * @param i The POST buffer in characters.
	**/
	public void setPostLimit( int i )
	{
		this.postLimit = i;
	}
	/**
	 * Set the batch limit -- at most this many records will be sent to the
	 * Solr server in a single batch.
	 * @param i The batch limit in records.
	**/
	public void setBatchLimit( int i )
	{
		this.batchLimit = i;
	}

	/**
	 * Add an XSL file to the XSL indexer.
	 * @param f An XSL stylesheet file.
	**/
	public void addXslFile( File f )
	{
		if ( f != null && f.isFile()
			&& f.getName().toLowerCase().endsWith(".xsl") )
		{
			this.xslFiles.add( f );
		}
	}

	/**
	 * Add all XSL files in a directory to the XSL indexer.
	 * @param f Directory containing XSL stylesheet files.
	**/
	public void addXslDir( File f )
	{
		File[] files = f.listFiles();
		for ( int i = 0; i < files.length; i++ )
		{
			addXslFile( files[i] );
		}
	}


	/*** indexing ************************************************************/

	/**
	 * Index an object.
	 * @param agraph AllegroGraph object to retrieve data from.
	 * @param datasource Solr datasource name.
	 * @param ark Subject ARK for the object.
	**/
	public void indexSubject( TripleStore ts, 
		String datasource, String solrCore, String ark ) throws Exception
	{
		setDatasource( ts, datasource, solrCore );
		indexSubject( ark );
	}

	/**
	 * Index an object.
	 * @param ark Subject ARK for the object.
	**/
	public void indexSubject( String ark ) throws Exception
	{
		// skip blank arks
		if ( ark == null || ark.equals("") )
		{
			return;
		}

		status( "indexSubject: " + ark );
		DAMSObject obj = new DAMSObject( ts, ark, nsmap );
		if ( predicates.size() == 0 && collectionData.size() == 0 
			&& colNames.size() == 0 )
		{
			populateCaches( obj );
			try
			{
				colNames = collectionNames(ts);
				colHier = collectionHierarchy(ts);
			}
			catch ( Exception ex )
			{
				colHier = new HashMap<String,String>();
				ex.printStackTrace();
			}
		}

		if ( streamingServer != null )
		{
			// add the record to server
			try
			{
				List<SolrInputDocument> docs = toSolrDoc(
					ark, toIndexXML(ark,obj)
				);
				streamingServer.add( docs );
				recordsIndexed++;
			}
			catch ( Exception ex )
			{
				log.warn("Error indexing: " + ark);
				if ( throwExceptions ) { throw ex; }
			}
		}
		else
		{
			// add the XML to our buffer
			String xml = null;
			try
			{
				xml = docToString( ark, toIndexXML(ark,obj) );
				postBuffer.append( xml );
				recordsIndexed++;
			}
			catch ( Exception ex )
			{
				log.warn("Error indexing: " + ark);
				if ( throwExceptions ) { throw ex; }
			}
	
			// if our buffer is getting too large, post to Solr
			if ( (postLimit > 0 && postBuffer.length() >= postLimit)
					|| recordsIndexed % batchLimit == 0 )
			{
				debug("SolrIndexer postBuffer: " + postBuffer.length());
				flush();
			}
		}
	}

	/**
	 * Convert a Document to a String, suppressing the XML declaration.
	**/
	private String docToString( String ark, Document doc ) throws IOException
	{
		StringWriter sw = new StringWriter();
		xmlWriter.setWriter(sw);
		xmlWriter.write( doc );

		// check for complex objects
		String s = doc.getRootElement().attributeValue("components");
		if ( s != null && !s.equals("") )
		{
			try
			{
				int numComponents = Integer.parseInt(s);
				for ( int i = 1; i <= numComponents; i++ )
				{
					String cSub = ark + "-1-" + i;
					DAMSObject cObj = new DAMSObject( ts, cSub, nsmap );
					Document cDoc = toIndexXML( cSub, cObj );
					// add "rdf:item_of = ark" to link to parent record
					addField( cDoc.getRootElement(), "bb2765355h", ark );
					addChild(
						cDoc.getRootElement(), "field", "component",
						"name", "category"
					);
					xmlWriter.write( cDoc );
				}
			}
			catch ( Exception ex )
			{
				log.warn(ex.toString() );
			}
		}

		// return all xml stringified
		return sw.toString();
	}

	/**
	 * Convert a Document to a SolrInputDocument.
	**/
	private List<SolrInputDocument> toSolrDoc( String ark, Document doc )
		throws IOException
	{
		List<SolrInputDocument> list = new ArrayList<SolrInputDocument>();
		list.add( toSolrDoc(doc) );

		// check for complex objects
		String s = doc.getRootElement().attributeValue("components");
		if ( s != null && !s.equals("") )
		{
			try
			{
				int numComponents = Integer.parseInt(s);
				for ( int i = 1; i <= numComponents; i++ )
				{
					String cSub = ark + "-1-" + i;
					DAMSObject cObj = new DAMSObject( ts, cSub, nsmap );
					Document cDoc = toIndexXML( cSub, cObj );
					// add "rdf:item_of = ark" to link to parent record
					addField( cDoc.getRootElement(), "bb2765355h", ark );
					addChild(
						cDoc.getRootElement(), "field", "component",
						"name", "category"
					);
					list.add( toSolrDoc(doc) );
				}
			}
			catch ( Exception ex )
			{
				log.warn(ex.toString() );
			}
		}

		// return list of solr docs
		return list;
	}
	private SolrInputDocument toSolrDoc( Document doc )
	{
		SolrInputDocument sdoc = new SolrInputDocument();
		Iterator it = doc.getRootElement().elementIterator("field");
		while ( it.hasNext() )
		{
			Element e = (Element)it.next();
			float boost = 0f;
			try
			{
				String boostVal = e.attributeValue("boost");
				if ( boostVal != null )
				{
					boost = Float.parseFloat( boostVal );
				}
			}
			catch ( Exception ex ) { }

			String field = e.attributeValue("field");
			String value = e.getStringValue();
			if ( boost != 0f )
			{
				sdoc.addField( field, value, boost );
			}
			else
			{
				sdoc.addField( field, value );
			}
		}
		return sdoc;
	}

	/**
	 * Send a batch of records to Solr.
	**/
	public boolean flush() throws Exception
	{
		if ( debug ) { return true; }
		else if ( streamingServer != null )
		{
			UpdateResponse resp = streamingServer.commit();
			return (resp.getStatus() == 200); // XXX: assume this is HTTP status code...
		}
		else if ( postBuffer.length() == 5 )
		{
			// don't post an empty buffer
			return true;
		}

		// index save
		boolean success = false;
		try
		{
			postBuffer.append("</add>");
			if ( exportDir != null )
			{
				success = writeFile( postBuffer, batchesIndexed );
			}
			else
			{
				success = solr.add(solrCore,postBuffer.toString());
			}
			batchesIndexed++;
			if ( !success )
			{
				log.error("SolrIndexer.flush(): Index add failed!");
				System.out.println("solrxml: " + postBuffer.toString());
				if (throwExceptions) {throw new IOException("Solr add failed");}
			}
			else if ( autoCommit )
			{
				success = commit();
			}

			// clear the buffer
			postBuffer.setLength(0);
			postBuffer.append("<add>");
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			throw ex;
		}

		// optimize occasionally
		debug("batchesIndexed: " + batchesIndexed );
		if ( exportDir == null && !debug &&  batchesIndexed % 50 == 0
			&& autoOptimize ) 
		{
			optimize();
		}

		// timer
		float dur = ((float)(System.currentTimeMillis() - start))/1000;
		float avg = ((float)recordsIndexed)/(dur);
		status("\n" + summary() );

		return success;
	}
	public String summary()
	{
		float dur = ((float)(System.currentTimeMillis() - start))/1000;
		float avg = ((float)recordsIndexed)/(dur);
		return("SolrIndexer: " + recordsIndexed + " records indexed in "
			+ numFmt.format(dur) + " secs (" + numFmt.format(avg) + "/sec)");
	}

	/**
	 * Retrieve namesapce and collection data from the triplestore.
	**/
	private void populateCaches( DAMSObject obj )
	{
		// predicate mapping
		try
		{
			StringReader nameReader = new StringReader(
				obj.getSolrNamespaceMap()
			);
			BufferedReader buf = new BufferedReader( nameReader );
			for ( String line = null; (line=buf.readLine()) != null; )
			{
				try
				{
					String[] tokens = line.split("\\t");
					if ( tokens != null && tokens.length == 2 )
					{
						String name = tokens[1].replaceAll("\\\"","");
						if ( name.startsWith("dc:")
							|| name.startsWith("file:")
							|| name.startsWith("mets:")
							|| name.startsWith("mix:")
							|| name.startsWith("mods:")
							|| name.startsWith("pre:")
							|| name.startsWith("rdf:")
							|| name.startsWith("rts:")
							|| name.startsWith("xdre:") )
						{
							predicates.put( tokens[0], name );
						}
						else
						{
							//colNames.put( tokens[0], name );
						}
					}
				}
				catch ( Exception ex )
				{
					log.warn("pre: unable to parse: " + line);
				}
			}
			buf.close();
		}
		catch ( Exception ex )
		{
			log.warn("Error loading name data");
			//ex.printStackTrace();
		}

		// collection info
		try
		{
			StringReader collectionReader = new StringReader(
				obj.getSolrCollectionData()
			);
			BufferedReader buf = new BufferedReader( collectionReader );
			for ( String line = null; (line=buf.readLine()) != null; )
			{
				try
				{
					if ( !line.equals("") )
					{
						String[] tokens = line.split("\\t");
						HashMap<String,String> data = collectionData.get(
							tokens[0]
						);
						if ( data == null )
						{
							data = new HashMap<String,String>();
						}
						data.put( tokens[1], tokens[2] );
						collectionData.put( tokens[0], data );
						status("colData: " + tokens[1] + " = " + tokens[2] );
					}
				}
				catch ( Exception ex )
				{
					status("colData: unable to parse: " + line );
				}
			}
			buf.close();
		}
		catch ( Exception ex )
		{
			log.warn("Error loading collection data" );
			//ex.printStackTrace();
		}
	}

	/**
	 * Commit changes already sent to the Solr server.
	**/
	public boolean commit() throws IOException
	{
		if ( exportDir == null && !debug )
		{
			debug("commit start");
			boolean success = solr.commit(solrCore);
			debug("commit end");
			if ( !success )
			{
				log.warn("Entity.indexCommit(): Index commit failed");
			}
			return success;
		}
		return true;
	}
	public boolean optimize() throws IOException
	{
		debug("optimize start");
		boolean success = solr.optimize(solrCore);
		debug("optimize end");
		return success;
	}

	private Map<String,String> collectionHierarchy( TripleStore ts )
		throws TripleStoreException
	{
		// collection hierarchy
		// ?parent <rdf:has_item> ?child . ?parent <xdre:category> <xdre:collection>
		String hierQuery = "select ?parent ?child where { ?parent <http://libraries.ucsd.edu/ark:/20775/bb1502546x> ?child . ?parent <http://libraries.ucsd.edu/ark:/20775/bb98644023> 'bb36527497' }";
		BindingIterator it = ts.sparqlSelect( hierQuery );
		Map<String,String> m = new HashMap<String,String>();
		while ( it.hasNext() )
		{
			Map<String,String> bindings = it.nextBinding();
			String child = bindings.get("child");
			String parent = bindings.get("parent");
			parent =  parent.replaceAll(".*/","");
			m.put( child, parent );
		}
		it.close();
		return m;
	}
	private Map<String,String> collectionNames( TripleStore ts )
		throws TripleStoreException
	{
		// collection names
		// ?subject <xdre:collectionName> ?name . ?subject <xdre:category> <xdre:collection>
		String hierQuery = "select ?subject ?name where { ?subject <http://libraries.ucsd.edu/ark:/20775/bb26288486> ?name . ?subject <http://libraries.ucsd.edu/ark:/20775/bb98644023> 'bb36527497' }";
		BindingIterator it = ts.sparqlSelect( hierQuery );
		Map<String,String> m = new HashMap<String,String>();
		while ( it.hasNext() )
		{
			Map<String,String> bindings = it.nextBinding();
			String subj = bindings.get("subject");
			subj = subj.replaceAll(".*/","");
			String name = bindings.get("name");
			m.put( subj, name );
		}
		it.close();
		return m;
	}
	
	private List<String> findParents( String cat )
	{
		List<String> parents = new ArrayList<String>();
		String child = cat;
		for ( String parent = null; (parent = colHier.get(child)) != null; )
		{
			parent = parent.replaceAll(".*/","");
			parents.add( parent );
			child = parent;
		}
		return parents;
	}

	/*** xml generation ******************************************************/
	private Document toIndexXML( String subject, DAMSObject obj )
		throws Exception
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("doc");

		// json md
		String json = obj.getSolrJsonData();
		rightsFound = false;
		String cat = null;

		// add object metadata
		if ( json != null )
		{
			BufferedReader buf = new BufferedReader( new StringReader(json) );
			for ( String line = null; (line=buf.readLine()) != null; )
			{
				String[] vals = line.split("\\t");
				String key = vals[1];
				String val = vals[2];
				val = val.trim();
				addField( root, key, val );
	
				if ( key.equals("bb0239751d") )
				{
 					// predicates.get(key).equals("xdre:numComponents") ???
					val = val.replaceAll("\\\"","");
					root.addAttribute("components",val);
				}
				else if ( key.equals("bb98644023") )
				{
 					// predicates.get(key).equals("xdre:category") ???
					cat = val.replaceAll("\"", "");
				}
			}
		}

		// add collection metadata
		if ( cat !=  null )
		{
			if ( cat.startsWith("[") )
			{
				cat = cat.replaceAll(",.*","");
				cat = cat.replaceAll("\\[","");
			}
			String catName = colNames.get(cat);
			if ( catName != null )
			{
				addChild(root, "field", catName+" "+cat, "name", "category");
				addChild(root, "field", catName, "name", "Facet_Collection" );

				List<String> parents = findParents( cat );
				for ( int i = 0; i < parents.size(); i++ )
				{
					String parent = parents.get(i);
					String pName = colNames.get(parent);
					if ( pName != null )
					{
						addChild(
							root, "field", pName,
							"name", "Facet_Collection"
						);
					}
					else
					{
						log.info(
							"parent not found: " + parent
						);
					}
				}
			}
			else
			{
				addChild( root, "field", cat, "name", "category" );
				addChild( root, "field", cat, "name", "Facet_Collection" );
			}

			HashMap<String,String> colData = collectionData.get(cat);
			if ( colData != null )
			{
				Iterator colIt = colData.keySet().iterator();
				while ( colIt.hasNext() )
				{
					String pred = (String)colIt.next();
					String valx = colData.get(pred);

					// supress xdre:pasDisplay from collection if present in
					// item metadata
					if ( !pred.equals("bb00693046") || !rightsFound )
					{
						addField( root, pred, valx );
					}
				}
			}
		}

		// system md
		addChild( root, "field", subject,	"name", "id");
		addChild( root, "field", datasource, "name", "ts" );
		String datestring = enteredFormat.format( new Date() );
		addChild( root, "field", datestring, "name", "entereddate");

		// filestore content
		if ( fs != null )
		{
			String fulltext = null;
			String[] components = fs.listComponents(subject);
			for ( int i = 0; i < components.length; i++ )
			{
				String compid = components[i];
				String[] files = fs.listFiles( subject, compid );
				for ( int j = 0; j < files.length; j++ )
				{
					String fn = files[j];
					if ( fn != null && fn.endsWith(".pdf") )
					{
						// read to string 
						InputStream is = null;
						try
						{
							is = fs.getInputStream( subject, compid, fn);
							fulltext = PDFParser.getContent( is, subject );
							fulltext = StringEscapeUtils.unescapeXml(fulltext);
							fulltext = escapeText(fulltext);
							fulltext = StringEscapeUtils.escapeXml(fulltext);
						}
						catch ( Exception ex )
						{
							log.warn(
								"Error reading or parsing FileStore content: "
									+ subject + "/" + fn
							);
						}
						finally
						{
							try
							{
								if( is != null ) { is.close(); }
							}
							catch ( Exception e2 ) { log.warn(e2); }
						}
					}
				}
			}
			if ( fulltext != null )
			{
				addChild(
					root, "field", fulltext, "name", "doctext", "boost", "0.5"
				);
			}
		}
		if ( fulltextDir != null )
		{
			String fulltext = null;
			File dataFile = new File( fulltextDir, subject );
			if ( dataFile != null && dataFile.exists() )
			{
				// read to string 
				try
				{
					StringBuffer ft = new StringBuffer();
					BufferedReader in = new BufferedReader(
						new FileReader(dataFile)
					);
					String line = null;
					while ( (line=in.readLine()) != null )
					{
						ft.append( line );
					}
					fulltext = ft.toString();
					in.close();
				}
				catch ( Exception ex )
				{
				}
			}
			if ( fulltext != null )
			{
				addChild(
					root, "field", fulltext, "name", "doctext", "boost", "0.5"
				);
			}
		}

		// XSL indexes
		if ( xslFiles != null && xslFiles.size() > 0 )
		{
			if ( xslIndexer == null )
			{
				xslIndexer = XSLIndexer.fromFiles( xslFiles );
			}
			String rdf = obj.getRDFXML(true);
			if ( rdf != null )
			{
				try
				{
					Date datesort = null;
					List<Document> xslDocs = xslIndexer.indexRDF( rdf );
					for ( int i = 0; i < xslDocs.size(); i++ )
					{
						Document xslDoc = xslDocs.get(i);
						List xslFields = xslDoc.selectNodes("/add/doc/field");
						for ( int j = 0; j < xslFields.size(); j++ )
						{
							Element xslElem = (Element)xslFields.get(j);
							Element xslElem2 = (Element)xslElem.detach();

							// unescape text
							String txt = xslElem2.getText();
							txt = StringEscapeUtils.unescapeJava(txt);
							xslElem2.setText( txt );

							String name = xslElem2.attributeValue("name");
							if ( name != null && datesort == null
								&& (name.equals("date_start")
									|| name.equals("date_end")) )
							{
								// Facet_Date post-processing
								// parse dates and try to get something usable
								Date d = DateParser.parseDate( txt );
								if ( d == null )
								{
									d = DateParser.parseFirst(txt);
								}
								if ( d != null )
								{
									// format in iso8601 format, GMT timezone
									datesort = d;
								}
							}
							else if ( name != null && name.equals("titlesort") )
							{
								// strip leading numbers, punctuation
								txt = txt.replaceAll("\\d*\\. ","");
								txt = txt.replaceAll("^#?\\d{1,3}[:)] ","");
								txt = txt.replaceAll("[.\\[\\]()\\-+$#!`:\\?]","");
								txt = txt.replaceAll("^\\s","");
								xslElem2.setText( txt );
							}

							root.add( xslElem2 );
						}
					}

					// date sort
					if ( datesort != null )
					{
						// make sure dates are CE, then format
						Date datesort2 = cram( datesort );
						String dstr = gmtFormat.format(datesort2);
						addChild( root, "field", dstr, "name", "datesort" );
					}
				}
				catch ( Exception ex )
				{
					ex.printStackTrace();
				}
			}
		}

		return doc;
	}

	/**
	 * Cram BCE dates into the milliseconds of 0001-01-01.  This allows dates
	 * up to 86,400,000 BCE to be accommodated in CE-only dates, with correct
	 * sorting.  All times on 1 CE will be modified to be one second before
	 * midnight.  Other CE dates will be returned as-is.
	 * @author escowles@ucsd.edu
	**/
	private static Date cram( Date oldDate ) throws IllegalArgumentException
	{
		// base calendar == midnight 0001-01-01
		GregorianCalendar newCal = new GregorianCalendar( 1, 0, 1, 23, 59, 59 );

		// parse date
		GregorianCalendar oldCal = new GregorianCalendar();
		oldCal.setTime( oldDate );

		int year = oldCal.get(Calendar.YEAR);
		int mon = oldCal.get(Calendar.MONTH);
		int day = oldCal.get(Calendar.DATE);
		int era = oldCal.get( Calendar.ERA );

		// check dates
		if ( era == GregorianCalendar.AD )
		{
			if ( year == 1 && mon == 0 && day == 1 )
			{
				// return midnight on 1/1/1 so actual 1 AD dates sort at end
				return newCal.getTime();
			}
			else
			{
				// return other AD dates as-is
				return oldDate;
			}
		}

		// cram years into milliseconds
		newCal.add( Calendar.MILLISECOND, 999 );
		newCal.add( Calendar.MILLISECOND, -1*oldCal.get(Calendar.YEAR) );
		return newCal.getTime();
	}

 	/**
	 * Convert a string to a set of slash-separated character pairs.
	**/
	private static String pairPath( String s )
	{
		StringBuffer s2 = new StringBuffer();
		for ( int i = 0; i < (s.length() -1); i+=2 )
		{
			s2.append( s.substring( i, i+2 ) );
			s2.append( "/" );
		}
		return s2.toString();
	}

	private void addField( Element parent, String predicate, String object )
	{
		String preName = predicates.get(predicate);
		String objName = predicates.get(object);

		if ( predicate != null && object != null )
		{
			// get values
			StringBuffer val = new StringBuffer();
			if ( predicate != null )
			{
				val.append( predicate );

				// mark xdre:pasDisplay as found in item metadata
				if ( predicate.equals("bb00693046") )
				{
					rightsFound = true;
				}
			}
			if ( preName != null && !predicate.equals(preName) )
			{
				val.append( " " + preName );
			}
			val.append("|||");
			if ( object != null ) { val.append( object ); }
			if ( objName != null && !object.equals(objName) )
			{
				val.append( " " + objName );
			}

			// build field element
			Element field = parent.addElement("field");
			field.addAttribute("name","attrib");
			field.setText( val.toString() );
		}
	}
	private void addChild( Element parent, String name,
		Object value, String attName, Object attValue )
	{
		addChild( parent, name, value, attName, attValue, null, null );
	}
	private void addChild( Element parent, String name, Object value,
		String attName1, Object attValue1, String attName2, Object attValue2 )
	{
		Element e = parent.addElement( name );
		String a1 = "";
		String a2 = "";
		if ( value != null )
		{
			e.setText( value.toString() );
		}
		if ( attName1 != null && attValue1 != null )
		{
			e.addAttribute( attName1, attValue1.toString() );
			if ( debug )
			{
				a1 = " " + attName1 + "=\"" + attValue1.toString() + "\"";
			}
		}
		if ( attName2 != null && attValue2 != null )
		{
			e.addAttribute( attName2, attValue2.toString() );
			if ( debug )
			{
				a2 = " " + attName2 + "=\"" + attValue2.toString() + "\"";
			}
		}
		debug("<" + name + a1 + a2 + ">" + value.toString() + "</" + name +">");
	}
	private boolean writeFile( StringBuffer buf, int batchNumber )
	{
		boolean success = false;
		try
		{
			File f = new File( exportDir, "batch-" + batchNumber + ".xml" );
			PrintWriter fout = new PrintWriter( new FileWriter(f) );
			fout.println( buf.toString() );
			fout.close();
			success = true;
			debug("wrote: " + f.getName() );
		}
		catch( Exception ex )
		{
			success = false;
			ex.printStackTrace();
		}
		return success;
	}

	/*** command-line operation **********************************************/
	public static void main( String[] args ) throws Exception
	{
		// variables
		String tsConf = null;
		String arks = null;
		String solrBase = null;
		String solrCore = null;
		String solrDS = null;

		String commit = null;
		String debug = null;
		String stream = null;
		String status = null;
		String optimize = null;

		String exportDir = null;
		String fulltextDir = null;
		String postLimit = null;
		ArrayList<String> xslDirs = new ArrayList<String>();
		ArrayList<String> xslFiles = new ArrayList<String>();
		String fsconfig = null;
		FileStore fs = null;

		// namespaces
		Map<String,String> nsmap = new HashMap<String,String>();

		// command-line arguments
		for ( int i = 0; i < args.length; i += 2 )
		{
			if ( args[i] == null || args[i].equals("") ) { /* skip */ }
			else if ( args[i].equals("tsConf") )	 { tsConf = args[i+1]; }
			else if ( args[i].equals("arks") )       { arks = args[i+1]; }
			else if ( args[i].equals("solrBase") )   { solrBase = args[i+1]; }
			else if ( args[i].equals("solrCore") )   { solrCore = args[i+1]; }
			else if ( args[i].equals("solrDS") )     { solrDS = args[i+1]; }

			else if ( args[i].equals("commit") )     { commit = args[i+1]; }
			else if ( args[i].equals("debug") )      { debug = args[i+1]; }
			else if ( args[i].equals("stream") )     { stream = args[i+1]; }
			else if ( args[i].equals("status") )     { status = args[i+1]; }
			else if ( args[i].equals("optimize") )   { optimize = args[i+1]; }

			else if ( args[i].equals("exportDir") )  { exportDir = args[i+1]; }
			else if ( args[i].equals("fulltextDir") ){ fulltextDir = args[i+1];}
			else if ( args[i].equals("postLimit") )  { postLimit = args[i+1]; }
			else if ( args[i].equals("xslDir") )     { xslDirs.add(args[i+1]); }
			else if ( args[i].equals("xslFile") )    { xslFiles.add(args[i+1]);}
			else if ( args[i].equals("fs") )         { fsconfig = args[i+1]; }
			else if ( args[i].startsWith("ns.") )
			{
				nsmap.put( args[i].substring(3), args[i+1] );
			}
			else
			{
				log.warn("Unknown param: " + args[i]);
			}
		}

		// get TripleStore instance
		Properties props = new Properties();
		props.load( new FileInputStream(tsConf) );
		TripleStore ts = TripleStoreUtil.getTripleStore( props );
		
		SolrIndexer indexer = null;
		ArrayList<String> bad = new ArrayList<String>();
		try
		{
			// setup solr indexer
			indexer = new SolrIndexer( ts, solrBase, solrCore, solrDS, nsmap );

			// disable exceptions for individual indexing errors
			indexer.setThrowExceptions(false);
			if ( commit != null && commit.equals("true") )
			{
				indexer.setAutoCommit( false );
			}
			if ( status != null && status.equals("true") )
			{
				indexer.setStatus( true );
			}
			if ( debug != null && debug.equals("true") )
			{
				indexer.setDebug( true );
			}
			if ( stream == null || stream.trim().equals("")
				|| stream.toLowerCase().equals("false") )
			{
				indexer.setStreaming( false );
			}
			else
			{
				indexer.setStreaming( true );
			}
			if ( optimize == null || optimize.trim().equals("")
				|| optimize.toLowerCase().equals("false") )
			{
				indexer.setAutoOptimize( false );
			}
			else
			{
				indexer.setAutoOptimize( true );
			}
			if ( exportDir != null )
			{
				indexer.setExportDir( new File(exportDir) );
			}
			if ( fulltextDir != null )
			{
				File f = new File(fulltextDir);
				if ( f != null && f.exists() && f.isDirectory() )
				{
					indexer.setFulltextDir( f );
				}
			}
			if ( fsconfig != null )
			{
				try
				{
					Properties fsprops = new Properties();
					fsprops.load( new FileInputStream(fsconfig) );
					fs = FileStoreUtil.getFileStore( fsprops );
					indexer.setFileStore( fs );
				}
				catch ( Exception ex )
				{
					ex.printStackTrace();
				}
			}
			if ( postLimit != null )
			{
				indexer.setPostLimit( Integer.parseInt(postLimit) );
			}
			if ( xslDirs != null && xslDirs.size() > 0 )
			{
				for ( int i = 0; i < xslDirs.size(); i++ )
				{
					String dir = xslDirs.get(i);
					if ( dir != null && !dir.equals("none") )
					{
						indexer.addXslDir( new File(dir) );
					}
				}
			}
			if ( xslFiles != null && xslFiles.size() > 0 )
			{
				for ( int i = 0; i < xslFiles.size(); i++ )
				{
					indexer.addXslFile( new File(xslFiles.get(i)) );
				}
			}

			// output debugging info
			System.out.println("solr base URL: " + indexer.solrBase);
			System.out.println("core.........: " + indexer.solrCore);
			System.out.println("streaming....: " + indexer.streaming);
			System.out.println("auto-commit..: " + indexer.autoCommit);
			System.out.println("auto-optimize: " + indexer.autoOptimize);
			System.out.println("---------------------------------------------");
	
			// read arks from a file and index them
			File f = new File(arks);
			BufferedReader buf = new BufferedReader( new FileReader(f) );
			for ( String ark = null; (ark=buf.readLine()) != null; )
			{
				try
				{
					indexer.indexSubject( ark );
				}
				catch ( Exception ex )
				{
					ex.printStackTrace();
					bad.add( ark );
					System.exit(1);
				}
			}
			buf.close();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		finally
		{
			// cleanup
			try { ts.close();  } catch ( Exception ex2 ) { }
			try { fs.close(); } catch ( Exception ex2 ) { }

			// commit any remaining documents
			indexer.flush();
		}

		// report failed records
		if ( bad.size() > 0 )
		{
			log.warn("Failed Records: " + bad.size() + ":");
			for ( int i = 0; i < bad.size(); i++ )
			{
				log.warn( bad.get(i) );
			}
		}
	}
	int dotCount = 0;
	private void dot()
	{
		if ( status )
		{
			dotCount++;
			System.out.print(".");
			if ( dotCount % 50 == 0 )
			{
				System.out.println();
			}
		}
	}
	private void status( String s )
	{
		if ( status )
		{
			System.out.println( s );
			dotCount = 0;
		}
	}
	private void debug( String s )
	{
		if ( debug )
		{
			long time = System.currentTimeMillis();
			log.debug( time + ": " + s );
		}
	}

/*
	private static final String illegalRegex = "[\u0000\u0001\u0002\u0003\u0004"
		+ "\u0005\u0006\u0007\u0008\u000B\u000C\u000E\u000F\u0010\u0011\u0012"
		+ "\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D"
		+ "\u001E\u001F\uFFFE\uFFFF]"; 
	private static final Pattern illegalPattern = Pattern.compile(illegalRegex);
	private static String escapeText( String s )
	{
		String res = s;
		if ( res != null )
		{
			Matcher m = illegalPattern.matcher(res);
			if( m.matches() )
			{
				res = m.replaceAll(" ");
			}
		}
		return res;
	}
*/
	private static final List<Character> illegalChars = Arrays.asList(
		'\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006',
		'\u0007', '\u0008', '\u000B', '\u000C', '\u000E', '\u000F', '\u0010', 
		'\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016', '\u0017', 
		'\u0018', '\u0019', '\u001A', '\u001B', '\u001C', '\u001D', '\u001E', 
		'\u001F', '\uFFFE', '\uFFFF' );
	private static String escapeText( String s )
	{
		char[] chars = s.toCharArray();
		for ( int i = 0; i < chars.length; i++ )
		{
			if ( illegalChars.contains(chars[i]) )
			{
				chars[i] = ' ';
			}
		}
		return new String(chars);
	}

	private static final String unicodeRegex = "\\\\u([0-9A-F]{4})";
	private static final Pattern unicodePattern = Pattern.compile(unicodeRegex);
	public static String unescapeUnicode( String s )
	{
		String res = s;
		if ( res != null )
		{
			Matcher m = unicodePattern.matcher(res);
			while( m.find() )
			{
				int i = Integer.parseInt( m.group(1), 16 );
				res = res.replaceAll(
					"\\" + m.group(0), Character.toString( (char)i )
				);
			}
		}
		return res;
	}
}
