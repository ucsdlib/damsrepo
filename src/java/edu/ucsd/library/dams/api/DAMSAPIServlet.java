package edu.ucsd.library.dams.api;

// java core api
import java.io.File;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.net.URLEncoder;

// naming
import javax.activation.FileDataSource;
import javax.activation.MimetypesFileTypeMap;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NameNotFoundException;

// servlet api
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// jms queue
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

// logging
import org.apache.log4j.Logger;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

// post/put file attachments
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

// commons-lang
import org.apache.commons.lang3.StringEscapeUtils;

// dom4j
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

// xsl
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

// xml streaming
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

// json
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.xml.sax.ContentHandler;

// dams
import edu.ucsd.library.dams.file.Checksum;
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;
import edu.ucsd.library.dams.file.ImageMagick;
import edu.ucsd.library.dams.file.Ffmpeg;
import edu.ucsd.library.dams.file.impl.LocalStore;
import edu.ucsd.library.dams.jhove.JhoveInfo;
import edu.ucsd.library.dams.jhove.MyJhoveBase;
import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.model.Event;
import edu.ucsd.library.dams.triple.ArkTranslator;
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
import edu.ucsd.library.dams.triple.Validator;
import edu.ucsd.library.dams.triple.edit.Edit;
import edu.ucsd.library.dams.util.Ezid;
import edu.ucsd.library.dams.util.HttpUtil;
import edu.ucsd.library.dams.util.LDAPUtil;
import edu.ucsd.library.dams.util.PDFParser;
import edu.ucsd.library.dams.util.OutputStreamer;
import edu.ucsd.library.dams.util.JSONOutputStreamer;
import edu.ucsd.library.dams.util.XMLOutputStreamer;

/**
 * Servlet implementing the DAMS REST API.
 * @author escowles@ucsd.edu
 * @author lsitu@ucsd.edu
**/
public class DAMSAPIServlet extends HttpServlet
{
	//========================================================================
	// Servlet init and shared state
	//========================================================================

	// logging
	private static Logger log = Logger.getLogger(DAMSAPIServlet.class);

	private Properties props;      // config
	private String damsHome;       // config file location
	private String appVersion;     // application (user) version
	private String srcVersion;     // source code version
	private String buildTimestamp; // timestamp application was built

	// default output format
	private String formatDefault; // output format to use when not specified

	// default data stores
	protected String fsDefault;	// FileStore to be used when not specified
	protected String fsStaging; // local staging directory
	protected Map<String,String> fsUseMap; // default use values
	protected String tsDefault;	// TripleStore to be used when not specified
	protected String tsEvents;	// TripleStore to be used for events

	// identifiers and namespaces
	protected String minterDefault;	      // ID series when not specified
	private Map<String,String> idMinters; // ID series name=>url map
	protected Map<String,String> nsmap;   // URI/name to URI map
	protected String idNS;                // Prefix for unqualified identifiers
	protected String prNS;                // Prefix for unqualified predicates
	protected String rdfNS;               // Prefix for RDF predicates
	protected String madsNS;              // Prefix for MADS ontology predicates
	protected Set<String> validClasses;   // valid class URIs
	protected Set<String> validProperties;// valid predicate URIs

	// uploads
	private int uploadCount = 0; // current number of uploads being processed
	private int maxUploadCount;  // number of concurrent uploads allowed
	private long maxUploadSize;  // largest allowed upload size
	private String backupDir;    // directory to store temporary edit backups

	// solr
	private String solrBase;		// base URL for solr webapp
	protected String xslBase;	    // base dir for server-side XSL stylesheets
	private String encodingDefault; // default character encoding
	private String mimeDefault;     // default output mime type
	private File solrXslFile;       // default solr xsl stylesheet

	// ip address mapping
	protected String roleSuper;           // superuser role
	protected String roleLocal;           // local-user role
	protected String roleAdmin;           // default admin role
	protected String roleAdmin2;          // secondary admin role
	protected String roleDefault;         // default role if not matching
	protected String localCopyright;      // local copyright owner
	private SortedMap<String,String[]> roleMap; // map of roles to IP addresses

	// fedora compat
	protected String fedoraObjectDS;  // datastream id that maps to object RDF
	protected String fedoraRightsDS;  // datastream id that maps to rights
	protected String fedoraLinksDS;   // datastream id that maps to links
	protected String fedoraSystemDS;  // datastream id that maps to system info
	protected String sampleObject;    // sample object for fedora demo
	protected String adminEmail;      // email address of system admin
	protected String fedoraCompat;    // fedora version emulated
	protected boolean fedoraDebug;    // debug output of ds PUT/POST content

	// derivatives creation
	private Map<String, String> derivativesRes; // derivatives resolution map
	private Map<String, String> derivativesUse; // derivatives use value map
	private String derivativesExt;              // extension for derivs
	private String magickCommand; 				// ImageMagick command
	private String ffmpegCommand; 				// ffmpeg command
	private long jhoveMaxSize;                  // local file cache size limit

	// number detection
	private static Pattern numberPattern = null;
	private static SimpleDateFormat dateFormat = new SimpleDateFormat(
		"yyyy-MM-dd'T'hh:mm:ssZ"
	);

	// ldap for group lookup
	private LDAPUtil ldaputil;

	// activemq for solrizer
	boolean queueEnabled = true;
	private String queueUrl;
	private String queueName;
	private ActiveMQConnectionFactory queueConnectionFactory;
	private Connection queueConnection;
	private Session queueSession;
	MessageProducer queueProducer;

    // stats tracking
	protected List<Long> pstTimes = new ArrayList<Long>();
	protected List<Long> getTimes = new ArrayList<Long>();
	protected List<Long> putTimes = new ArrayList<Long>();
	protected List<Long> delTimes = new ArrayList<Long>();

	// object rdf/xml caching
	private static HashMap<String,String> cacheContent = new HashMap<String,String>();
	private static LinkedList<String> cacheAccess = new LinkedList<String>();
	private static int cacheSize = 10; // max objects in cache, 0 = disabled

	// doi minting
	private Ezid ezid;

	protected static void cacheAdd( String objid, String content )
	{
		if ( cacheSize > 0 )
		{
			if ( !cacheContent.containsKey(objid) )
			{
				while ( cacheContent.size() >= cacheSize )
				{
					cacheContent.remove(cacheAccess.pop());
				}
			}
			cacheContent.put(objid,content);
			cacheAccess.add(objid);
		}
	}
	protected static String cacheGet( String objid )
	{
		if ( cacheSize > 0 )
		{
			cacheAccess.remove(objid);
			cacheAccess.add(objid);
			return cacheContent.get(objid);
		}
		else
		{
			return null;
		}
	}
	protected String cacheUpdate( String objid, TripleStore ts, TripleStore es )
	{
		// Q: serialize with events? performance implications?
		String rdfxml = null;
		Map info = objectShow( objid, ts, es ); // static???
		if ( info.get("obj") != null )
		{
			DAMSObject obj = (DAMSObject)info.get("obj");
			try
			{
				rdfxml = obj.getRDFXML(true);
				if ( rdfxml != null )
				{
					cacheAdd( objid, rdfxml );
				}
			}
			catch ( Exception ex )
			{
				log.warn( "Error retrieving RDF/XML", ex );
			}
		}
		return rdfxml;
	}
	protected static void cacheRemove( String objid )
	{
		if ( cacheSize > 0 )
		{
			cacheContent.remove(objid);
			cacheAccess.remove(objid);
		}
	}
	protected static void cacheClear()
	{
		if ( cacheSize > 0 )
		{
			cacheContent.clear();
			cacheAccess.clear();
		}
	}

	// initialize servlet parameters
	public void init( ServletConfig config ) throws ServletException
	{
		ServletContext ctx = config.getServletContext();
		config(ctx);
		appVersion     = ctx.getInitParameter("app-version");
		srcVersion     = ctx.getInitParameter("src-version");
		buildTimestamp = ctx.getInitParameter("build-timestamp");
		super.init(config);
	}
	protected String config(ServletContext context)
	{
		String error = null;
		try
		{
			InitialContext ctx = new InitialContext();
			try
			{
				damsHome = (String)ctx.lookup("java:comp/env/dams/home");
			}
			catch ( Exception ex )
			{
				damsHome = "dams";
			}
			File f = new File( damsHome, "dams.properties" );
			props = new Properties();
			props.load( new FileInputStream(f) );

			// default output format
			formatDefault = props.getProperty( "format.default");

			// editor backup save dir
			backupDir = props.getProperty("edit.backupDir");

			// identifiers/namespaces
			minterDefault = props.getProperty("minters.default");
			idMinters = new HashMap<String,String>();
			String minterList = props.getProperty("minters.list");
			String[] minterNames = minterList.split(",");
			for ( int i = 0; i < minterNames.length; i++ )
			{
				idMinters.put(
					minterNames[i], props.getProperty("minters."+minterNames[i])
				);
			}
			nsmap = TripleStoreUtil.namespaceMap(props);
			idNS = nsmap.get("damsid");
			prNS = nsmap.get("dams");
			rdfNS = nsmap.get("rdf");
			madsNS = nsmap.get("mads");

			// load valid class/predicate lists
			validClasses = loadSet( context, "/WEB-INF/valid-classes.txt" );
			validProperties = loadSet( context, "/WEB-INF/valid-properties.txt" );

			// solr
			solrBase = props.getProperty("solr.base");
			mimeDefault = props.getProperty("solr.mimeDefault");
			encodingDefault = props.getProperty("solr.encoding");
			xslBase = props.getProperty("solr.xslDir");
			solrXslFile = new File( xslBase, "solrindexer.xsl" );

			// access control
			localCopyright = props.getProperty("role.localCopyright");
			roleDefault = props.getProperty("role.default");
			roleLocal = props.getProperty("role.local");
			roleAdmin = props.getProperty("role.admin");
			roleAdmin2 = props.getProperty("role.admin2");
			roleSuper = props.getProperty("role.super");
			String roleList = props.getProperty("role.list");
			String[] roles = roleList.split(",");
			roleMap = new TreeMap<String,String[]>();
            try
			{
				for ( int i = 0; i < roles.length; i++ )
				{
					String ipList = props.getProperty(
						"role." + roles[i] + ".iplist"
					);
					String[] ipArray = ipList.split(",");
					roleMap.put( roles[i], ipArray );
				}
			}
			catch ( Exception ex )
			{
				System.err.println("Error parsing roles: " + ex.toString());
			}

			// triplestores
			tsDefault = props.getProperty("ts.default");
			tsEvents  = props.getProperty("ts.events");

			// files
			fsDefault = props.getProperty("fs.default");
			fsStaging = props.getProperty( "fs.staging" );
			maxUploadCount = getPropInt(props, "fs.maxUploadCount", -1 );
			maxUploadSize  = getPropLong(props, "fs.maxUploadSize", -1L );
			fsUseMap = new HashMap<String,String>();
			for ( Enumeration e = props.propertyNames(); e.hasMoreElements(); )
			{
				String key = (String)e.nextElement();
				if ( key != null && key.startsWith("fs.usemap.") )
				{
					String ext = key.substring(10);
					String use = props.getProperty(key);
					if ( use != null )
					{
						fsUseMap.put( ext, use );
					}
				}
			}

			// fedora compat
			fedoraObjectDS = props.getProperty("fedora.objectDS");
			fedoraRightsDS = props.getProperty("fedora.rightsDS");
			fedoraLinksDS  = props.getProperty("fedora.linksDS");
			fedoraSystemDS  = props.getProperty("fedora.systemDS");
			sampleObject = props.getProperty("fedora.samplePID");
			adminEmail = props.getProperty("fedora.adminEmail");
			fedoraCompat = props.getProperty("fedora.compatVersion");
			if ( props.getProperty("fedora.debug") != null )
			{
				fedoraDebug = props.getProperty("fedora.debug").equals("true");
			}

			// derivative list
			derivativesExt  = props.getProperty("derivatives.ext");
			String derList = props.getProperty("derivatives.list");
			derivativesRes = new HashMap<String, String>();
			derivativesUse = new HashMap<String, String>();
			if(derList != null)
			{
				String[] d = derList.split(",");
				for ( int i = 0; i < d.length; i++ )
				{
					String res = props.getProperty(
						"derivatives." + d[i] + ".resolution"
					);
					String use = props.getProperty(
						"derivatives." + d[i] + ".use"
					);
					derivativesRes.put( d[i], res );
					derivativesUse.put( d[i], use );
				}
			}

			// ImageMagick convert command
			magickCommand = props.getProperty("magick.convert");
			if ( magickCommand == null )
				magickCommand = "convert";

			// Ffmpeg convert command
			ffmpegCommand = props.getProperty("ffmpeg");
			if ( ffmpegCommand == null )
				ffmpegCommand = "ffmpeg";
			
			// Jhove configuration
			String jhoveConf = props.getProperty("jhove.conf");
			if ( jhoveConf != null )
				MyJhoveBase.setJhoveConfig(jhoveConf);
			jhoveMaxSize = getPropLong( props, "jhove.maxSize", -1L );

			// ldap for group lookup
			ldaputil = new LDAPUtil( props );

			// cache size
			cacheSize = getPropInt(props, "ts.cacheSize", 0 );
			cacheClear(); // clear cache

			// queue
			queueUrl = props.getProperty("queue.url");
			queueName = props.getProperty("queue.name");
			if ( queueEnabled && queueUrl != null )
			{
				queueConnectionFactory = new ActiveMQConnectionFactory(
					queueUrl
				);
				queueConnection = queueConnectionFactory.createConnection();
				queueConnection.start();
				queueSession = queueConnection.createSession(
					false, Session.AUTO_ACKNOWLEDGE
				);
				queueProducer= queueSession.createProducer(
					queueSession.createTopic(queueName)
				);
				queueProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
				log.info("JMS Queue: " + queueUrl + "/" + queueName);
			}

			// doi minter
			String ezidHost = props.getProperty("ezid.host");
			String ezidShoulder = props.getProperty("ezid.shoulder");
			String ezidUser = props.getProperty("ezid.user");
			String ezidPass = props.getProperty("ezid.pass");
			ezid = new Ezid( ezidHost, ezidShoulder, ezidUser, ezidPass );
		}
		catch ( Exception ex )
		{
			log.error( "Error initializing", ex );
			error = ex.toString();
		}

		return error;
	}
	protected Map systemInfo( HttpServletRequest req )
	{
		String baseURL = req.getScheme() + "://" + req.getServerName() + ":"
			+ req.getServerPort() + req.getContextPath();

		Map info = new LinkedHashMap();
		info.put( "sampleObject", sampleObject );
		info.put( "adminEmail",   adminEmail );
		info.put( "fedoraCompat", fedoraCompat );
		info.put( "baseURL",      baseURL );
		return info;
	}


	//========================================================================
	// REST API methods
	//========================================================================

	/**
	 * HTTP GET methods to retrieve objects and datastream metadata and files.
	 * Calls to GET should not change state in any way.
	**/
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		long start = System.currentTimeMillis();
		Map info = null;
		boolean outputRequired = true; // set to false to suppress status output
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;

		try
		{
			// parse request URI
			String[] path = path( req );

			// GET /collections
			if ( path.length == 2 && path[1].equals("collections") )
			{
				ts = triplestore(req);
				info = collectionListAll( ts );
			}
			// GET /collections/bb1234567x
			else if ( path.length == 3 && path[1].equals("collections") )
			{
				ts = triplestore(req);
				info = collectionListObjects( path[2], ts );
			}
			// GET /collections/bb1234567x/count
			else if ( path.length == 4 && path[1].equals("collections")
				&& path[3].equals("count") )
			{
				ts = triplestore(req);
				info = collectionCount( path[2], ts );
			}
			// GET /collections/bb1234567x/embargo
			else if ( path.length == 4 && path[1].equals("collections")
				&& path[3].equals("embargo") )
			{
				ts = triplestore(req);
				info = collectionEmbargo( path[2], ts );
			}
			// GET /collections/bb1234567x/files
			else if ( path.length == 4 && path[1].equals("collections")
				&& path[3].equals("files") )
			{
				ts = triplestore(req);
				info = collectionListFiles( path[2], ts );
			}
			// GET /units
			else if ( path.length == 2 && path[1].equals("units") )
			{
				ts = triplestore(req);
				info = unitListAll( ts );
			}
			// GET /units/bb1234567x
			else if ( path.length == 3 && path[1].equals("units") )
			{
				ts = triplestore(req);
				info = unitListObjects( path[2], ts );
			}
			// GET /units/bb1234567x/count
			else if ( path.length == 4 && path[1].equals("units")
				&& path[3].equals("count") )
			{
				ts = triplestore(req);
				info = unitCount( path[2], ts );
			}
			// GET /units/bb1234567x/embargo
			else if ( path.length == 4 && path[1].equals("units")
				&& path[3].equals("embargo") )
			{
				ts = triplestore(req);
				info = unitEmbargo( path[2], ts );
			}
			// GET /units/bb1234567x/files
			else if ( path.length == 4 && path[1].equals("units")
				&& path[3].equals("files") )
			{
				ts = triplestore(req);
				info = unitListFiles( path[2], ts );
			}
			// GET /events/bb1234567x
			else if ( path.length == 3  && path[1].equals("events") )
			{
				es = events(req);
				info = objectShow( path[2], es, null );
				if ( info.get("obj") != null )
				{
					DAMSObject obj = (DAMSObject)info.get("obj");
					output(
						obj, true, req.getParameterMap(), req.getPathInfo(), res
					);
					outputRequired = false;
				}
			}
			// GET /events
			else if ( path.length == 2 && path[1].equals("events") )
			{
				es = events(req);
				info = eventsListAll( es );
			}
			// GET /records
			else if ( path.length == 2 && path[1].equals("records") )
			{
				ts = triplestore(req);
				recordsList(
					ts, req.getParameterMap(), null, req.getPathInfo(), res
				);
				outputRequired = false; // streaming output
			}
			// GET /records/[type]
			else if ( path.length == 3 && path[1].equals("records")
				&& !path[2].equals("") )
			{
				ts = triplestore(req);
				recordsList(
					ts, req.getParameterMap(), path[2], req.getPathInfo(), res
				);
				outputRequired = false; // streaming output
			}
			// GET /sparql
			else if ( path.length == 2 && path[1].equals("sparql") )
			{
				Map<String,String[]> params = req.getParameterMap();
				ts = triplestore(params);
				String query = getParamString(params,"query",null);
				sparqlQuery( query, ts, params, req.getPathInfo(), res );
				outputRequired = false;
			}
			// GET /objects
			else if ( path.length == 2 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				info = objectsListAll( ts );
			}
			// GET /objects/export?id=bb1111111x&id=bb2222222y&id=bb3333333z
			else if ( path.length == 3 && path[1].equals("objects") 
				&& path[2].equals("export") )
			{
				ts = triplestore(req);
				es = events(req);
				String[] ids = req.getParameterValues("id");
				List<String> objids = new ArrayList<String>();
				for ( int i = 0; i < ids.length; i++ )
				{
					if ( ids[i] != null && !ids[i].trim().equals("") )
					{
						objids.add( ids[i].trim() );
					}
				}
				try
				{
					String xml = objectBatch( objids, ts, es );
					output( res.SC_OK, xml, "application/xml", res );
					outputRequired = false;
				}
				catch ( Exception ex )
				{
					info = error( "Error processing batch retrieval", ex );
				}
			}
			// GET /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				es = events(req);
				info = objectShow( path[2], ts, es );
				if ( info.get("obj") != null )
				{
					DAMSObject obj = (DAMSObject)info.get("obj");
					output(
						obj, false, req.getParameterMap(),
						req.getPathInfo(), res
					);
					outputRequired = false;
				}
			}
			// GET /objects/bb1234567x/export
			else if ( path.length == 4  && path[1].equals("objects")
				&& path[3].equals("export") )
			{
				ts = triplestore(req);
				es = events(req);
				info = objectShow( path[2], ts, es );
				if ( info.get("obj") != null )
				{
					DAMSObject obj = (DAMSObject)info.get("obj");
					output(
						obj, true, req.getParameterMap(), req.getPathInfo(), res
					);
					outputRequired = false;
				}
			}
			// GET /objects/bb1234567x/exists
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("exists") )
			{
				ts = triplestore(req);
				info = objectExists( path[2], ts );
			}
			// GET /objects/bb1234567x/files
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("files") )
			{
				ts = triplestore(req);
				info = objectListFiles( path[2], ts );
			}
			// GET /objects/bb1234567x/transform
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("transform") )
			{
				ts = triplestore(req);
				String xsl = getParamString(req,"xsl",null);
				boolean export = getParamBool(req,"recursive",false);
				objectTransform(
					path[2], null, null, export, ts, null, xsl, null, null,
					req.getParameterMap(), req.getPathInfo(), res
				);
				outputRequired = false;
			}
			// GET /objects/bb1234567x/validate
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("validate") )
			{
				fs = null; // filestore(req);
				ts = triplestore(req);
				es = events(req);
				info = objectValidate( path[2], fs, ts, es );
			}
			// GET /files/image-service
			else if ( path.length == 3 && path[1].equals("files") )
			{
				ts = triplestore(req);
				info = useListFiles( "\"" + path[2] + "\"", ts );
			}
			// GET /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				fileShow( path[2], null, path[3], req, res );
				outputRequired = false;
			}
			// GET /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				fileShow( path[2], path[3], path[4], req, res );
				outputRequired = false;
			}
			// GET /files/bb1234567x/1.tif/characterize
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("characterize") )
			{
				fs = filestore(req);
				info = fileCharacterize(
					path[2], null, path[3], false, fs, null, null, new HashMap<String, String[]>()
				);
			}
			// GET /files/bb1234567x/1/1.tif/characterize
			else if ( path.length == 6 && path[1].equals("files")
				&& isNumber(path[3]) && path[5].equals("characterize") )
			{
				fs = filestore(req);
				info = fileCharacterize(
					path[2], path[3], path[4], false, fs, null, null, new HashMap<String, String[]>()
				);
			}
			// GET /files/bb1234567x/1.tif/exists
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("exists") )
			{
				fs = filestore(req);
				info = fileExists( path[2], null, path[3], fs );
			}
			// GET /files/bb1234567x/1/1.tif/exists
			else if ( path.length == 6 && path[1].equals("files")
				&& path[5].equals("exists") && isNumber(path[3]) )
			{
				fs = filestore(req);
				info = fileExists( path[2], path[3], path[4], fs );
			}
			// GET /files/bb1234567x/1.tif/fixity
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("fixity") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				info = fileFixity( path[2], null, path[3], fs, ts, es );
			}
			// GET /files/bb1234567x/1/1.tif/fixity
			else if ( path.length == 6 && path[1].equals("files")
				&& path[5].equals("fixity") && isNumber(path[3]) )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				info = fileFixity( path[2], path[3], path[4], fs, ts, es );
			}
			// GET /files/bb1234567x/1.tif/text
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("text") )
			{
				fs = filestore(req);
				info = extractText( path[2], null, path[3], fs );
			}
			// GET /files/bb1234567x/1/1.tif/text
			else if ( path.length == 6 && path[1].equals("files")
				&& path[5].equals("text") && isNumber(path[3]) )
			{
				fs = filestore(req);
				info = extractText( path[2], path[3], path[4], fs );
			}
			// GET /client/info
			else if ( path.length == 3 && path[1].equals("client")
				&& path[2].equals("info") )
			{
				String ip   = getParamString(req, "ip",   req.getRemoteAddr());
				String user = getParamString(req, "user", req.getRemoteUser());
				info = clientInfo( ip, user );
			}
			// GET /system/config
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("config") )
			{
				String err = config( getServletContext() );
				if ( err == null ) { info = status("Configuration reloaded"); }
				else { info = error( err, null ); }
			}
			// GET /system/info
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("info") )
			{
				info = systemInfo(req);
			}
			// GET /system/predicates
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("predicates") )
			{
				ts = triplestore(req);
				info = predicateList( ts );
			}
			// GET /system/filestores
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("filestores") )
			{
				List<String> filestores = list(props,"fs.",".className");
				info = new LinkedHashMap();
				info.put( "filestores", filestores );
				info.put( "defaultFilestore", fsDefault );
			}
			// GET /system/times
			else if ( path.length == 3 && path[1].equals("system")
				&& path[2].equals("times") )
			{
				info = times();
			}
			// GET /system/triplestores
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("triplestores") )
			{
				List<String> triplestores = list(props,"ts.",".className");
				info = new LinkedHashMap();
				info.put( "triplestores", triplestores );
				info.put( "defaultTriplestore", tsDefault );
				info.put( "eventsTriplestore", tsEvents );
			}
			// GET /system/version
			else if ( path.length == 3 && path[1].equals("system")
				&& path[2].equals("version") )
			{
				info = new LinkedHashMap();
				info.put( "appVersion",     appVersion );
				info.put( "srcVersion",     srcVersion );
				info.put( "buildTimestamp", buildTimestamp );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request", null );
			}

			// output
			if ( outputRequired )
			{
				// make sure a status code is set
				if ( info.get("statusCode") == null )
				{
					info.put("statusCode",res.SC_OK);
				}
				output( info, req.getParameterMap(), req.getPathInfo(), res );
			}
		}
		catch ( Exception ex2 )
		{
			log.warn( "Error processing GET request", ex2 );
		}
		finally
		{
			// cleanup
			cleanup( fs, ts, es );
		}
		getTimes.add( System.currentTimeMillis() - start );
	}

	/**
	 * HTTP POST methods to create identifiers, objects, datastreams and
	 * relationships.  Calls to POST should be used to create resources.
	**/
	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		long start = System.currentTimeMillis();
		/*
			// detect overloaded POST
			String method = params.get("method");
			if ( method != null && method.equalsIgnoreCase("PUT") )
			{
				create = false;
			}
		*/

		Map info = null;
		boolean outputRequired = true; // set to false to suppress status output
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;
		Map<String,String[]> params = null;

		try
		{
			// parse request URI
			String[] path = path( req );

			// POST /index
			if ( path.length == 2 && path[1].equals("index") )
			{
				InputBundle bundle = input(req);
				params = bundle.getParams();
				String[] ids = getParamArray(params,"id",null);
				info = indexQueue( ids, "modifyObject" );
			}
			// POST /queue
			else if ( path.length == 2 && path[1].equals("queue") )
			{
				InputBundle bundle = input(req);
				params = bundle.getParams();
				String[] ids = getParamArray( params,"id",null);
				info = indexQueue( ids, "modifyObject" );
			}
			// POST /next_id
			else if ( path.length == 2 && path[1].equals("next_id") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				String idMinter = getParamString(params,"name",minterDefault);
				int count = getParamInt( params, "count", 1 );
				info = identifierCreate( idMinter, count );
			}
			// POST /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				try
				{
					InputBundle bundle = input( req );
					InputStream in = bundle.getInputStream();
					params = bundle.getParams();
					String adds = getParamString( params, "adds", null );
					ts = triplestore(params);
					es = events(params);
					fs = filestore(params);
					cacheRemove(path[2]);
					info = objectEdit( path[2], true, in, null, adds, null, null, ts, es, fs );
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file", ex);
				}
			}
			// POST /objects/bb1234567x/mint_doi
			else if ( path.length == 4 && path[1].equals("objects") 
					&& path[3].equals("mint_doi"))
			{
				try
				{
					ts = triplestore(params);
					es = events(params);
					fs = filestore(params);
					cacheRemove(path[2]);
					info = mintDOI( path[2], ts, es, fs, res );
				}
				catch ( Exception ex )
				{
					log.warn("Error minting DOI", ex );
					info = error("Error minting DOI: " + ex.getMessage(), ex);
				}
			}
			// POST /objects/bb1234567x/merge
			else if ( path.length == 4 && path[1].equals("objects") 
					&& path[3].equals("merge"))
			{
		   		info = error( res.SC_BAD_REQUEST, "Please use PUT to merge records.", null);
			}
			// POST /objects/bb1234567x/transform
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("transform") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				ts = triplestore(params);
				es = events(params);
				String xsl = getParamString(params,"xsl",null);
				String dest = getParamString(params,"dest",null);
				boolean export = getParamBool(params,"recursive",false);
				if ( dest != null )
				{
					fs = filestore(params);
					cacheRemove(path[2]);
				}
				objectTransform(
					path[2], null, null, export, ts, es, xsl, fs, dest,
					req.getParameterMap(), req.getPathInfo(), res
				);
				outputRequired = false;
			}
			// POST /objects/bb1234567x/index
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("index") )
			{
				String[] ids = new String[]{ path[2] };
				info = indexQueue( ids, "modifyObject" );
			}
			// POST /objects/bb1234567x/serialize
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("serialize") )
			{
				String objid = path[2];
				// serialize the record to disk
				try
				{
					fs = filestore(params);
					ts = triplestore(params);
					es = events(params);
					fs.write(
							objid, null, "rdf.xml", cacheUpdate(objid,ts,es).getBytes()
					);
				}
				catch ( Exception ex )
				{
					log.error("Error serializing RDF/XML on update", ex);
					info = error("Failed to serialize record " + objid + " to filestore.", ex);
				}
			}
			// POST /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				try
				{
					// make sure request is multipart with a file upload,
					// or that the srcfs or local params are specified
					if ( !ServletFileUpload.isMultipartContent(req) 
						&& req.getParameter("srcfs") == null
						&& req.getParameter("local") == null )
					{
						info = error(
							HttpServletResponse.SC_BAD_REQUEST,
							"Multipart or srcfs/local params required", null
						);
					}
					else
					{
						InputBundle bundle = input(req,path[2],null,path[3]);
						InputStream in = bundle.getInputStream();
						params = bundle.getParams();
						fs = filestore(params);
						ts = triplestore(params);
						es = events(params);
						cacheRemove(path[2]);
						info = fileUpload(
							path[2], null, path[3], false, in, fs, ts, es, params
						);
					}
				}
				catch ( SizeLimitExceededException ex )
				{
					log.warn("File too large for upload: " + ex.getMessage() );
					info = error("File too large for upload (max upload size: " + maxUploadSize + "), stage locally instead", ex);
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file", ex);
				}
			}
			// POST /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				try
				{
					// make sure request is multipart with a file upload or
					// the local or srcfs params are specified
					if ( !ServletFileUpload.isMultipartContent(req)
						&& req.getParameter("srcfs") == null
						&& req.getParameter("local") == null )
					{
						info = error(
							HttpServletResponse.SC_BAD_REQUEST,
							"Multipart or srcfs/local params required", null
						);
					}
					else
					{
						InputBundle bundle = input(req,path[2],path[3],path[4]);
						InputStream in = bundle.getInputStream();
						params = bundle.getParams();
						fs = filestore(params);
						ts = triplestore(params);
						es = events(params);
						cacheRemove(path[2]);
						info = fileUpload(
							path[2], path[3], path[4], false, in, fs, ts, es, params
						);
					}
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file", ex);
				}
			}
			// POST /files/bb1234567x/1.tif/characterize
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("characterize") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);
				cacheRemove(path[2]);
				info = fileCharacterize( path[2], null, path[3], false, fs, ts, es, params );
			}
			// POST /files/bb1234567x/1/1.tif/characterize
			else if ( path.length == 6 && path[1].equals("files")
				&& path[4].equals("characterize") && isNumber(path[3]) )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);
				cacheRemove(path[2]);
				info = fileCharacterize( path[2], path[3], path[4], false, fs, ts, es, params );
			}
			// POST /files/bb1234567x/1.tif/derivatives
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("derivatives") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);

                Map<String,String[]> params2 = new HashMap<String, String[]>();
                params2.put( "fs", new String[]{getParamString(params,"fs",fsDefault)} );
                params2.put( "size", getParamArray(params,"size",null) );
                params2.put( "frame", getParamArray(params,"frame",null) );
				cacheRemove(path[2]);
				info = fileDerivatives( path[2], null, path[3], false, fs, ts, es, params );
			}
			// POST /files/bb1234567x/1/1.tif/derivatives
			else if ( path.length == 6 && path[1].equals("files")
				&& isNumber(path[3]) && path[5].equals("derivatives") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);

				Map<String,String[]> params2 = new HashMap<String, String[]>();
				params2.put( "fs", new String[]{getParamString(params,"fs",fsDefault)} );
				params2.put( "size", getParamArray(params,"size",null) );
				params2.put( "frame", getParamArray(params,"frame",null) );
				cacheRemove(path[2]);
				info = fileDerivatives( path[2], path[3], path[4], false, fs, ts, es, params );
			}
			// POST /sparql
			else if ( path.length == 2 && path[1].equals("sparql") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				ts = triplestore(params);
				String query = getParamString(params,"query",null);
				sparqlQuery( query, ts, params, req.getPathInfo(), res );
				outputRequired = false;
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request", null );
			}

			// output
			if ( outputRequired )
			{
				// make sure a status code is set
				if ( info.get("statusCode") == null )
				{
					info.put("statusCode",res.SC_OK);
				}
				// make sure we have parameters
				if ( params == null )
				{
					params = req.getParameterMap();
				}
				output( info, params, req.getPathInfo(), res );
			}
		}
		catch ( Exception ex2 )
		{
			log.warn( "Error processing POST request", ex2 );
		}
		finally
		{
			// cleanup
			cleanup( fs, ts, es );
		}
		pstTimes.add( System.currentTimeMillis() - start );
	}
	/**
	 * HTTP PUT methods to modify objects and datastreams.  Calls to PUT should
	 * be used to modify existing resources.
	**/
	public void doPut( HttpServletRequest req, HttpServletResponse res )
	{
		long start = System.currentTimeMillis();
		Map info = null;
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;
		Map<String,String[]> params = null;

		try
		{
			// parse request URI
			String[] path = path( req );

			// PUT /objects/bb1234567x
			if ( path.length == 3 && path[1].equals("objects") )
			{
				try
				{
					InputBundle bundle = input( req );
					InputStream in = bundle.getInputStream();
					params = bundle.getParams();
					String adds    = getParamString(params,"adds",null);
					String updates = getParamString(params,"updates",null);
					String deletes = getParamString(params,"deletes",null);
					String mode    = getParamString(params,"mode",null);
					ts = triplestore(req);
					es = events(req);
					fs = filestore(req);
					cacheRemove(path[2]);
					info = objectEdit(
						path[2], false, in, mode, adds, updates, deletes,
						ts, es, fs
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating object", ex );
					info = error( "Error updating object", ex );
				}
			} 
			// PUT /objects/bb1234567x/merge
			else if ( path.length == 4 && path[1].equals("objects") 
					&& path[3].equals("merge") )
			{
				try
				{
					InputBundle bundle = input( req );
					params = bundle.getParams();
					ts = triplestore(req);
					es = events(req);
					fs = filestore(req);
					info = mergeRecords(
						path[2], params, ts, es, fs
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error merging records to " + path[2], ex );
					info = error( "Error merging records(s) to " + path[2], ex );
				}
			}
			// PUT /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				try
				{
					InputBundle bundle = input( req, path[2], null, path[3] );
					InputStream in = bundle.getInputStream();
					params = bundle.getParams();
					fs = filestore(params);
					ts = triplestore(params);
					es = events(params);
					cacheRemove(path[2]);
					info = fileUpload(
						path[2], null, path[3], true, in, fs, ts, es, params
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating file", ex );
					info = error( "Error updating file", ex );
				}
			}
			// PUT /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				try
				{
					InputBundle bundle = input( req, path[2], path[3], path[4] );
					InputStream in = bundle.getInputStream();
					params = bundle.getParams();
					fs = filestore(params);
					ts = triplestore(params);
					es = events(params);
					cacheRemove(path[2]);
					info = fileUpload(
						path[2], path[3], path[4], true, in, fs, ts, es, params
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating file", ex );
					info = error( "Error updating file", ex );
				}
			}
			// PUT /files/bb1234567x/1.tif/characterize
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("characterize") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);
				cacheRemove(path[2]);
				info = fileCharacterize( path[2], null, path[3], true, fs, ts, es, params );
			}
			// PUT /files/bb1234567x/1/1.tif/characterize
			else if ( path.length == 6 && path[1].equals("files")
				&& path[5].equals("characterize") && isNumber(path[3]) )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);
				cacheRemove(path[2]);
				info = fileCharacterize( path[2], path[3], path[4], true, fs, ts, es, params );
			}
			// PUT /files/bb1234567x/1.tif/derivatives
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("derivatives") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);

				Map<String,String[]> params2 = new HashMap<String, String[]>();
				params2.put( "fs", new String[]{getParamString(params,"fs",fsDefault)} );
				params2.put( "size", getParamArray(params,"size",null) );
				params2.put( "frame", getParamArray(params,"frame",null) );
				cacheRemove(path[2]);
				info = fileDerivatives( path[2], null, path[3], true, fs, ts, es, params2 );
			}
			// PUT /files/bb1234567x/1/1.tif/derivatives
			else if ( path.length == 6 && path[1].equals("files")
				&& isNumber(path[3]) && path[5].equals("derivatives") )
			{
				InputBundle bundle = input( req );
				params = bundle.getParams();
				fs = filestore(params);
				ts = triplestore(params);
				es = events(params);

				Map<String,String[]> params2 = new HashMap<String, String[]>();
				params2.put( "fs", new String[]{getParamString(params,"fs",fsDefault)} );
				params2.put( "size", getParamArray(params,"size",null) );
				params2.put( "frame", getParamArray(params,"frame",null) );
				cacheRemove(path[2]);
				info = fileDerivatives( path[2], path[3], path[4], true, fs, ts, es, params );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request", null );
			}

			// output
			if ( params == null )
			{
				params = req.getParameterMap();
			}
			output( info, params, req.getPathInfo(), res );
		}
		catch ( Exception ex2 )
		{
			log.warn( "Error processing PUT request", ex2 );
		}
		finally
		{
			// cleanup
			cleanup( fs, ts, es );
		}
		putTimes.add( System.currentTimeMillis() - start );
	}

	/**
	 * HTTP DELETE methods to delete objects, datastreams and relationships.
	 * Calls to DELETE should be used to delete resources.
	**/
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
		long start = System.currentTimeMillis();
		Map info = null;
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;

		try
		{
			// parse request URI
			String[] path = path( req );

			// DELETE /index
			if ( path.length == 2 && path[1].equals("index") )
			{
				InputBundle input = input(req);
				String[] ids = input.getParams().get("id");
				info = indexQueue( ids, "purgeObject" );
			}
			// DELETE /queue
			else if ( path.length == 2 && path[1].equals("queue") )
			{
				InputBundle input = input(req);
				String[] ids = input.getParams().get("id");
				info = indexQueue( ids, "purgeObject" );
			}
			// DELETE /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				cacheRemove(path[2]);
				info = objectDelete( path[2], ts, es, fs );
			}
			// DELETE /objects/bb1234567x/index
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("index") )
			{
				String[] ids = new String[]{ path[2] };
				info = indexQueue( ids, "purgeObject" );
			}
			// DELETE /objects/bb1234567x/selective
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("selective") )
			{
				String[] predicates = req.getParameterValues("predicate");
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				cacheRemove(path[2]);
				info = selectiveDelete( path[2], null, predicates, ts, es, fs );
			}
			// DELETE /objects/bb1234567x/1/selective
			else if ( path.length == 5 && path[1].equals("objects")
				&& isNumber(path[3]) && path[4].equals("selective") )
			{
				String[] predicates = req.getParameterValues("predicate");
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				cacheRemove(path[2]);
				info = selectiveDelete(
					path[2], path[3], predicates, ts, es, fs
				);
			}
			// DELETE /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				cacheRemove(path[2]);
				info = fileDelete( path[2], null, path[3], fs, ts, es );
			}
			// DELETE /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				cacheRemove(path[2]);
				info = fileDelete( path[2], path[3], path[4], fs, ts, es );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request", null );
			}

			// output
			output( info, req.getParameterMap(), req.getPathInfo(), res );
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing DELETE request", ex );
		}
		finally
		{
			// cleanup
			cleanup( fs, ts, es );
		}
		delTimes.add( System.currentTimeMillis() - start );
	}

	public Map times()
	{
		Map info = new HashMap();
		info.put( "get", times(getTimes) );
		info.put( "pst", times(pstTimes) );
		info.put( "put", times(putTimes) );
		info.put( "del", times(delTimes) );
		return info;
	}
	private Map times( List<Long> times )
	{
		// calculate mean
		int count = times.size();
		long sum = 0L;
		for ( long time : times )
		{
			sum += time;
		}

		// clear data
		times.clear();

		// send reponse
		Map info = new HashMap();
		info.put( "count", String.valueOf(count) );
		info.put( "sum", String.valueOf(sum) );
		info.put( "mean", String.valueOf( (float)sum/count ) );
		return info;
	}

	protected FileStore filestore( HttpServletRequest req ) throws Exception
	{
		return filestore( req.getParameterMap() );
	}
	protected FileStore filestore( Map<String,String[]> params )
		throws Exception
	{
		String fsName = getParamString(params,"fs",fsDefault);
		return FileStoreUtil.getFileStore(props,fsName);
	}
	protected TripleStore triplestore( HttpServletRequest req ) throws Exception
	{
		return triplestore( req.getParameterMap() );
	}
	protected TripleStore triplestore( Map<String,String[]> params )
		throws Exception
	{
		String tsName = getParamString(params,"ts",tsDefault);
		return TripleStoreUtil.getTripleStore(props,tsName);
	}
	protected TripleStore events( HttpServletRequest req ) throws Exception
	{
		return events( req.getParameterMap() );
	}
	protected TripleStore events( Map<String,String[]> params ) throws Exception
	{
		String tsName = getParamString(params,"es",tsEvents);
		return TripleStoreUtil.getTripleStore(props,tsName);
	}
	protected static void cleanup(FileStore fs, TripleStore ts, TripleStore es)
	{
		if ( fs != null )
		{
			try { fs.close(); }
			catch ( Exception ex ) { log.warn("Error closing FileStore",ex); }
		}
		if ( ts != null )
		{
			try { ts.close(); }
			catch ( Exception ex ) { log.warn("Error closing TripleStore",ex); }
		}
		if ( es != null )
		{
			try { es.close(); }
			catch ( Exception ex ) { log.warn("Error closing Event TripleStore",ex); }
		}
	}


	//========================================================================
	// Core Java API
	//========================================================================
	public Map clientInfo( String ip, String user )
	{
		Map info = new LinkedHashMap();
		info.put( "statusCode", HttpServletResponse.SC_OK );
		info.put( "ip", ip );

		if ( user == null ) { user = ""; }
		else
		{
			try
			{
				Map ldapInfo = ldaputil.lookup( user, null );
				if ( ldapInfo != null )
				{
					info.put( "user", user );
					info.putAll( ldapInfo );
				}
			}
			catch ( NameNotFoundException ex )
			{
				log.warn( "Error looking up groups, name not found: " + user );
			}
			catch ( Exception ex )
			{
				log.warn( "Error looking up groups: " + user, ex );
			}
		}

		String role = getRole( ip );
		info.put( "role", role );

		return info;
	}
	public Map collectionCount( String colid, TripleStore ts )
	{
		String[] preds = new String[]{"dams:collection","dams:assembledCollection","dams:provenanceCollection","dams:provenanceCollectionPart"};
		return count( preds, colid, ts );
	}
	public Map unitCount( String repid, TripleStore ts )
	{
		return count( new String[]{"dams:unit"}, repid, ts );
	}
	protected Map count( String[] pred, String obj, TripleStore ts )
	{
		try
		{
			Identifier objid = createID( obj, null, null );
			if ( !ts.exists(objid) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, pred + " does not exist", null
				);
			}
			if ( pred == null || pred.length < 1 )
			{
				return null;
			}

			String sparql = "select ?id where ";
			if ( pred.length == 1 )
			{
				Identifier pre = createPred( pred[0] );
 				sparql += "{ ?id <" + pre.getId() + "> "
					+ "<" + objid.getId() + "> }";
			}
			else
			{
				sparql += "{";
				for ( int i = 0; i < pred.length; i++ )
				{
					if ( i > 0 ) { sparql += " UNION "; }
					Identifier pre = createPred( pred[i] );
					sparql += "{?id <" + pre.getId() + "> "
						+ "<" + objid.getId() + "> }";
				}
				sparql += "}";
			}

			long count = ts.sparqlCount( sparql );
			Map info = new LinkedHashMap();
			info.put("count",count);
			return info;
		}
		catch ( Exception ex )
		{
			String msg = "Error counting " + pred + " members: " + obj;
			log.info(msg, ex );
			return error(msg, ex);
		}
	}
	public Map collectionListFiles( String colid, TripleStore ts )
	{
		String[] pred = new String[]{ "dams:collection", "dams:assembledCollection", "dams:provenanceCollection", "dams:provenanceCollectionPart" };
		Map m = listFiles( pred, colid, ts);
		return m;
	}
	public Map unitListFiles( String repid, TripleStore ts )
	{
		return listFiles( new String[]{"dams:unit"}, repid, ts );
	}
	public Map useListFiles( String use, TripleStore ts )
		throws TripleStoreException
	{
		// perform sparql query
		Identifier pre = createPred( "dams:use" );
		String sparql = "select ?obj where { "
			+ "?obj <" + pre.getId() + "> '" + use + "' }";
		BindingIterator bind = ts.sparqlSelect(sparql);
		List<Map<String,String>> results = bindings(bind);

		// unwrap file names
		List files = new ArrayList();
		for ( int i = 0; i < results.size(); i++ )
		{
			files.add( results.get(i).get("obj") );
		}

		// build output map
		Map info = new HashMap();
		info.put("files", files);
		return info;
	}
	protected Map listFiles( String[] pred, String obj, TripleStore ts )
	{
		try
		{
			// should be able to do this with sparql, but for now, we can
			// just use our API to list objects and then list files for each
			// object
			List files = new ArrayList();
			Map objlist = listObjects( pred, obj, ts  );

			// check status of object list
			int status = getParamInt(objlist,"statusCode",200);
			List objects = (List)objlist.get("objects");
			if ( status > 299 || objects == null )
			{
				return objlist;
			}

			// list files of each object
			for ( int i = 0; i < objects.size(); i++ )
			{
				Map o = (Map)objects.get(i);
				String objid = (String)o.get("obj");
				Map objfiles = objectListFiles( objid, ts );
				List filelist = (List)objfiles.get("files");
				files.addAll( filelist );
			}

			Map info = new LinkedHashMap();
			info.put("files",files);
			return info;
		}
		catch ( Exception ex )
		{
			String msg = "Error listing files in a " + pred + ": " + obj;
			log.info(msg, ex );
			return error(msg, ex);
		}
	}
	public Map objectListFiles( String objid, TripleStore ts )
	{
		try
		{
			Identifier obj = createID( objid, null, null );
			if ( !ts.exists(obj) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist", null
				);
			}

			Map<String,Map<String,String>> fm
				= new HashMap<String,Map<String,String>>();
			StatementIterator stmtit = ts.sparqlDescribe( obj );
			while ( stmtit.hasNext() )
			{
				Statement s = stmtit.nextStatement();
				String id = s.getSubject().getId();
				if ( !id.equals(obj.getId()) && id.startsWith(obj.getId()) )
				{
					String p = s.getPredicate().getId();
					p = p.replaceAll(prNS,"");
					String o = s.getLiteral();
					if ( !p.equals("hasFile") && o != null )
					{
						if ( o.startsWith("\"") && o.endsWith("\"") )
						{
							o = o.substring(1,o.length()-1);
						}
						Map<String,String> m = fm.get(id);
						if ( m == null )
						{
							m = new HashMap<String,String>();
							m.put("object",objid);
							m.put("id",id);
						}
						m.put( p, o );
						fm.put( id, m );
					}
				}
			}

			// convert map to list
			ArrayList fl = new ArrayList();
			for ( Iterator fit = fm.values().iterator(); fit.hasNext(); )
			{
				Map finfo = (Map)fit.next();
				if ( finfo.size() > 0 ) { fl.add(finfo); }
			}

			Map info = new LinkedHashMap();
			info.put("files",fl);
			return info;
		}
		catch ( Exception ex )
		{
			String msg = "Error listing files for object: " + objid;
			log.info(msg, ex );
			return error(msg, ex);
		}
	}
	public Map unitEmbargo( String colid, TripleStore ts )
	{
		Map info = new LinkedHashMap();
		try {
			info.put("embargoed", getEmbargoedList("dams:unit", colid, ts));
		} catch (TripleStoreException ex) {
			return error( "Error listing embargoed objects for unit " + colid, ex );
		}
		return info;
		// output = metadata: list of embargoed objects and embargoed endDate
	}
	public Map collectionEmbargo( String colid, TripleStore ts )
	{
		Map info = new LinkedHashMap();
		List<Map<String, String>> embargoes = new ArrayList<Map<String, String>>();
		String[] colTypes = new String[]{ "dams:collection", "dams:assembledCollection", "dams:provenanceCollection", "dams:provenanceCollectionPart" };
		try{
			for(int i=0; i<colTypes.length; i++)
				embargoes.addAll(getEmbargoedList(colTypes[i], colid, ts));
			
			info.put("embargoed", embargoes);
		} catch (TripleStoreException ex) {
			return error( "Error listing embargoed objects for collection " + colid, ex);
		}
		return info;
		// output = metadata: list of embargoed objects and embargoed endDate
	}
	public Map unitListAll( TripleStore ts )
	{
		try
		{
			String sparql = "select ?unit ?name where { ?unit <" + prNS + "unitName> ?name }";
			BindingIterator units = ts.sparqlSelect(sparql);
			List<Map<String,String>> unitList = bindings(units);
			Map info = new HashMap();
			info.put( "units", unitList );
			return info;
		}
		catch ( Exception ex )
		{
			return error( "Error listing units",  ex );
		}
	}
	public Map eventsListAll( TripleStore ts )
	{
		try
		{
			String sparql = "select ?obj where { ?obj <" + rdfNS + "type> <" + prNS + "DAMSEvent> }";
			BindingIterator evs = ts.sparqlSelect(sparql);
			List<Map<String,String>> events = bindings(evs);
			Map info = new HashMap();
			info.put( "events", events );
			return info;
		}
		catch ( Exception ex )
		{
			return error( "Error listing events",  ex );
		}
	}
	private void recordsList( TripleStore ts, Map<String,String[]> params,
		String type, String pathInfo, HttpServletResponse res )
	{
		try
		{
			// build sparql query
			String sparql = "select ?obj ?type where { ?obj <" + rdfNS + "type> ?t . ?t <" + rdfNS + "label> ?type";
			if ( type != null )
			{
				sparql += " . FILTER(?type = '\"" + type + "\"')";
			}
			sparql += "}";

			// select format
			OutputStreamer stream = null;
			String format = getParamString(params,"format",formatDefault);
			if ( format.equals("xml") )
			{
				stream = new XMLOutputStreamer( res );
			}
			else if ( format.equals("json") )
			{
				stream = new JSONOutputStreamer( res );
			}
			else
			{
				Map err = error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Unsupported format: " + format, null
				);
				output( err, params, pathInfo, res );
				return;
			}

			// iterate over records
			stream.start("records");
			BindingIterator objs = ts.sparqlSelect(sparql);
			int records = 0;
			while ( objs.hasNext() )
			{
				// build map of key/value pairs
				Map<String,String> binding = objs.nextBinding();
				Iterator<String> it = binding.keySet().iterator();
				while ( it.hasNext() )
				{
					String k = it.next();
					String v = binding.get(k);

					// remove redundant quotes in map
					if ( v.startsWith("\"") && v.endsWith("\"") )
					{
						v = v.substring(1,v.length()-1);
						binding.put(k,v);
					}
				}

			 	if ( binding.get("obj").startsWith("_") )
				{
					// suppress blank nodes
				}
				else if ( type == null &&
					(  binding.get("type").equals("dams:File")
					|| binding.get("type").equals("dams:Component")
					|| binding.get("type").equals("dams:DAMSEvent")) )
				{
					// suppress child records unless specifically asked for
				}
				else
				{
					// otherwise, write the record out
					stream.output( binding );
					records++;
				}
			}

			// add meta info
			Map info = new HashMap();
			info.put( "status", "OK" );
			info.put( "statusCode", "200" );
			info.put( "request", pathInfo );
			info.put( "count", String.valueOf(records) );
			stream.finish( info );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
	}
	public Map objectsListAll( TripleStore ts )
	{
		try
		{
			String sparql = "select ?obj where { ?obj <" + rdfNS + "type> <" + prNS + "Object> }";
			BindingIterator objs = ts.sparqlSelect(sparql);
			List<Map<String,String>> objects = bindings(objs);
			Map info = new HashMap();
			info.put( "objects", objects );
			return info;
		}
		catch ( Exception ex )
		{
			return error( "Error listing objects", ex );
		}
	}
	public Map collectionListAll( TripleStore ts )
	{
		try
		{
			List<Map<String,String>> cols = new ArrayList<Map<String,String>>();
			cols.addAll( collectionListAll(ts, "Collection") );
			cols.addAll( collectionListAll(ts, "AssembledCollection") );
			cols.addAll( collectionListAll(ts, "ProvenanceCollection") );
			cols.addAll( collectionListAll(ts, "ProvenanceCollectionPart") );

			Map info = new HashMap();
			info.put( "collections", cols );
			return info;
		}
		catch ( Exception ex )
		{
			return error( "Error listing collections", ex );
		}
	}
	public List<Map<String,String>> collectionListAll( TripleStore ts, String type ) throws Exception
	{
		String sparql = "select ?collection ?title where { ?collection <" + prNS + "title> ?bn . ?bn <" + madsNS + "authoritativeLabel> ?title . ?collection <" + rdfNS + "type> <" + prNS + type + "> }";
		BindingIterator bit = ts.sparqlSelect(sparql);
		List<Map<String,String>> cols = bindings(bit);

		// add type value
		for ( int i = 0; i < cols.size(); i++ )
		{
			Map<String,String> m = cols.get(i);
			m.put("type",type);
		}

		return cols;
	}

	private List<Map<String,String>> bindings( BindingIterator bit )
	{
		List<Map<String,String>> bindings = new ArrayList<Map<String,String>>();
		while ( bit.hasNext() )
		{
			// remove redundant quotes in map
			Map<String,String> binding = bit.nextBinding();
			Iterator<String> it = binding.keySet().iterator();
			while ( it.hasNext() )
			{
				String k = it.next();
				String v = binding.get(k);
				if ( v.startsWith("\"") && v.endsWith("\"") )
				{
					v = v.substring(1,v.length()-1);
					binding.put(k,v);
				}
			}
			bindings.add( binding );
		}
		return bindings;
	}

	public Map collectionListObjects( String colid, TripleStore ts  )
	{
		String[] pred = new String[]{ "dams:collection", "dams:assembledCollection", "dams:provenanceCollection", "dams:provenanceCollectionPart" };
		Map m = listObjects( pred, colid, ts );
		return m;
	}
	public Map unitListObjects( String repid, TripleStore ts  )
	{
		return listObjects( new String[]{"dams:unit"}, repid, ts );
	}
	public Map listObjects( String[] pred, String col, TripleStore ts  )
	{
		try
		{
			// make sure collection exists
			Identifier id = createID( col, null, null );
			if ( !ts.exists(id) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND,
					pred + " does not exist", null
				);
			}

			// list objects related to the col by each predicate
			List<Map<String,String>> objs = new ArrayList<Map<String,String>>();
			for ( int i = 0; i < pred.length; i++ )
			{
				objs.addAll( listObjects(pred[i],id,ts) );
			}

			Map info = new HashMap();
			info.put( "objects", objs );
			return info;
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			return error( "Error listing " + pred, ex );
		}
	}
	public List listObjects( String pred, Identifier id, TripleStore ts  )
		throws Exception
	{
		Identifier pre = createPred( pred );
		String sparql = "select ?obj where { "
			+ "?obj <" + pre.getId() + "> "
			+ "<" + id.getId() + "> }";
		BindingIterator bind = ts.sparqlSelect(sparql);
		List<Map<String,String>> objects = bindings(bind);

		return objects;
	}
	public Map fileCharacterize( String objid, String cmpid, String fileid,
		boolean overwrite, FileStore fs, TripleStore ts, TripleStore es,
		Map<String, String[]> params ) throws TripleStoreException
	{

		String sourceFile = null;
		boolean srcDelete = false;
		StatementIterator sit = null;
		try{

			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object identifier required", null
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required", null
				);
			}

			Identifier oid = createID( objid, null, null );
			Identifier fid = createID( objid, cmpid, fileid );
			Identifier parent = oid;
			Identifier cid = null;
			if ( cmpid != null && !cmpid.equals("0") )
			{
				cid = createID( objid, cmpid, null );
				parent = cid;
			}

			Identifier hasFile = Identifier.publicURI( prNS + "hasFile" );
			Identifier hasComp = Identifier.publicURI( prNS + "hasComponent" );
			Identifier cmpType = Identifier.publicURI( prNS + "Component" );
			Identifier rdfType = Identifier.publicURI( rdfNS + "type" );
			Identifier damsFile = Identifier.publicURI( prNS + "File" );

			if ( ts != null && !overwrite )
			{
				sit = ts.listStatements(parent, hasFile, fid);
				if(sit.hasNext()){
					return error(
						HttpServletResponse.SC_FORBIDDEN,
						"Characterization for file " + fid.getId()
							+ " already exists. Please use PUT instead", null
					);
				}
			}

			Map<String, String> m = null;
			if (   getParamString(params,"formatName",null) != null
				&& getParamString(params,"mimeType",null) != null
				&& (getParamString(params,"crc32checksum",null) != null
					|| getParamString(params,"md5checksum",null) != null
					|| getParamString(params,"sha1checksum",null) != null ) )
			{
				// if formatName, mimeType and at least one checksum are
				// specified, skip jhove processing
				m = new HashMap<String,String>();
				m.put("size",getParamString(params,"size",null) );
				m.put("formatName",getParamString(params,"formatName",null) );
				m.put("formatVersion",getParamString(params,"formatVersion",null) );
				m.put("mimeType",getParamString(params,"mimeType",null) );
				m.put("crc32checksum",getParamString(params,"crc32checksum",null) );
				m.put("md5checksum",getParamString(params,"md5checksum",null) );
				m.put("sha1checksum",getParamString(params,"sha1checksum",null) );
				String duration = getParamString(params,"duration",null);
				if(duration != null && duration.length() > 0)
					m.put("duration", duration );
				String quality = getParamString(params,"quality",null);
				if(quality != null && quality.length() > 0)
					m.put("quality", quality );
			}
			else
			{
				// extract technical metadata using jhove
				File localFile = getParamFile(params,"local",null);

				if ( fs instanceof LocalStore )
				{
					// use localstore version if possible
					sourceFile = fs.getPath( objid, cmpid, fileid );
					log.info(
						"Jhove extraction, source=localStore: " + sourceFile
					);
				}
				else if ( localFile != null )
				{
					// otherwise, use staged source file
					sourceFile = localFile.getAbsolutePath();
					log.info(
						"Jhove extraction, source=local file: " + sourceFile
					);
				}
				else
				{
					// if file not available locally, copy to local disk
					String fileExt = fileid.indexOf(".")>0?fileid.substring(fileid.indexOf(".")):"";
					File srcFile = File.createTempFile( "jhovetmp",  fileExt);
					long fileSize = fs.length(objid, cmpid, fileid);

					// make sure file is not too large to copy locally...
					if ( jhoveMaxSize != -1L && fileSize > jhoveMaxSize )
					{
						return error(
							HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
							"File is too large to retrieve for local processing"
							+ " (maxSize=" + jhoveMaxSize + "): " + fid.getId(), null
						);
					}
					else if ( fileSize > srcFile.getFreeSpace() )
					{
						return error(
							HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
							"There is not enough disk space to create a temp"
							+ " file for " + fid.getId(), null
						);
					}
					srcDelete = true;
					FileOutputStream fos = new FileOutputStream(srcFile);
					fs.read( objid, cmpid, fileid, fos );
					fos.close();
					sourceFile = srcFile.getAbsolutePath();
					log.info(
						"Jhove extraction, source=cached file: " + sourceFile
					);
				}

				m = jhoveExtraction( sourceFile );
			}

			// User submitted properties: use, dateCreated, sourceFilename, and
			// sourcePath
			String[] input = params.get("use");
			String use = input!=null?input[0]:null;
			input = params.get("dateCreated");
			String dateCreated = input!=null?input[0]:null;
			input = params.get("sourceFileName");
			String sourceFilename = input!=null?input[0]:null;
			input = params.get("sourcePath");
			String sourcePath = input!=null?input[0]:null;
			input = params.get("fs");
			String fsName = input!=null?input[0]:null;

			// Output is saved to the triplestore.
			if ( ts != null && es != null )
			{
				if ( overwrite )
				{
					// delete existing metadata
					fileDeleteMetadata( objid, cmpid, fileid, ts, true );
				}
				else
				{
					// check for file metadata and complain if it exists
					if ( cmpid != null && !cmpid.equals("0") )
					{
						sit = ts.listStatements(cid, hasFile, fid);
					}
					else
					{
						sit = ts.listStatements(oid, hasFile, fid);
					}
					if(sit.hasNext())
					{
						return error(
							HttpServletResponse.SC_FORBIDDEN,
							"Characterization for file " + fid.getId()
							+ " already exists. Please use PUT instead", null
						);
					}
				}

				// Add/Replace properties submitted from user

				// Required use property
				if ( use == null || use.length() == 0)
				{
					 use =  getFileUse( fileid );
				}
				m.put("use", use);

				if ( dateCreated != null && dateCreated.length() > 0 )
				{
					m.put( "dateCreated", dateCreated );
				}
				if ( sourceFilename != null && sourceFilename.length() > 0 )
				{
					m.put( "sourceFileName", sourceFilename );
				}
				if ( sourcePath != null && sourcePath.length() > 0 )
				{
					m.put( "sourcePath", sourcePath );
				}
				if ( fsName != null && fsName.length() > 0 )
				{
					m.put( "filestore", fsName );
				}

				// Add constant properties

				String compositionLevel = m.get("compositionLevel");
				if( compositionLevel == null )
				{
					compositionLevel = getDefaultCompositionLevel(
						sourceFilename
					);
				}
				m.put( "preservationLevel", "full" );
				m.put( "objectCategory", "file" );
				// # of decoding/extraction steps to get usable file
				m.put( "compositionLevel", compositionLevel );

				String key = null;
				String value = null;
				for ( Iterator it=m.keySet().iterator(); it.hasNext(); )
				{
					key = (String)it.next();
					if ( ! key.equals( "status" ) )
					{
						value = m.get(key);
						if(value != null && value.length() > 0)
						{
							ts.addLiteralStatement(
								fid, Identifier.publicURI( prNS + key ),
								"\"" + value + "\"", oid
							);
						}
					}
				}

				// link from object/component to file record
				if ( cmpid != null && !cmpid.equals("0") )
				{
					// make sure something links to this component
					sit = ts.listStatements(null, hasComp, cid);
					if(!sit.hasNext())
					{
						ts.addStatement( oid, hasComp, cid, oid );
						ts.addStatement( cid, rdfType, cmpType, oid );
					}
					ts.addStatement( cid, hasFile, fid, oid );
				}
				else
				{
					ts.addStatement( oid, hasFile, fid, oid );
				}
				ts.addStatement( fid, rdfType, damsFile, oid );

				// Create event when required with es
				if(es != null)
				{
					//indexQueue(objid,"modifyObject");
					createEvent(
						ts, es, fs, objid, cmpid, fileid, Event.CHECKSUM_CALCULATED,
						true, null, m.get("status")
					);
				}
				return status( "Jhove extracted and saved successfully" );
			}
			else
			{
				// Output is displayed but not saved to the triplestore.
				Map info = new HashMap();
				info.put("characterization",m);
				return info;
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error Jhove extraction", ex );
			return error( "Error Jhove extraction", ex );
		}
		finally
		{
			if(srcDelete)
			{
				new File(sourceFile).delete();
			}
			if(sit != null)
			{
				sit.close();
				sit = null;
			}
		}
	}
	public Map<String,String> recordedChecksums( String objid, String cmpid,
		String fileid, TripleStore ts )
	{
		Map<String,String> sums = new HashMap<String,String>();
		try
		{
			Identifier oid = createID( objid, null, null );
			Identifier fid = createID( objid, cmpid, fileid );
			if ( !ts.exists(oid) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist", null
				);
			}

			StatementIterator stmtit = ts.sparqlDescribe( oid );
			while ( stmtit.hasNext() )
			{
				Statement s = stmtit.nextStatement();
				String sub = s.getSubject().getId();
				if ( sub.equals(fid.getId()) )
				{
					String pre = s.getPredicate().getId();
					if ( pre.endsWith("checksum") )
					{
						pre = pre.substring( prNS.length(), pre.length()-8 );
						String val = s.getLiteral();
						if ( val.startsWith("\"") && val.indexOf("\"",1) > 1)
						{
							val = val.substring( 1, val.indexOf("\"",1) );
						}
						sums.put( pre, val );
					}
				}
			}
		}
		catch ( Exception ex )
		{
			String msg = "Error retrieving checksums: " + ex.toString();
			log.info(msg, ex );
			return error(msg, ex);
		}
		return sums;
	}
	public Map fileUpload( String objid, String cmpid, String fileid,
		boolean overwrite, InputStream in, FileStore fs, TripleStore ts,
		TripleStore es, Map<String, String[]> params )
	{
		try
		{
			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object identifier required", null
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required", null
				);
			}

			if ( in == null )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File upload or locally-staged file required", null
				);
			}

			// check upload count and abort if at limit
			if ( maxUploadCount != -1 && uploadCount >= maxUploadCount )
			{
				log.info("Upload: refused");
				return error(
					HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"Too many concurrent uploads", null
				);
			}
			else
			{
				uploadCount++;
				log.info("Upload: start: " + uploadCount);
			}

			// make sure appropriate method is being used to create/update
			if ( !overwrite && fs.exists( objid, cmpid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File already exists, use PUT to overwrite", null
				);
			}
			else if ( overwrite && !fs.exists( objid, cmpid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist, use POST to create", null
				);
			}

			// upload file
			fs.write( objid, cmpid, fileid, in );
			boolean successful = fs.exists(objid,cmpid,fileid)
				&& fs.length(objid,cmpid,fileid) > 0;
			in.close();

			String type = null;
			if ( overwrite) { type = Event.FILE_MODIFIED; }
			else { type = Event.FILE_ADDED; }
			String message = null;
			if ( successful )
			{
				int status = -1;
				if ( overwrite )
				{
					status = HttpServletResponse.SC_OK;
					message = "File saved successfully";
				}
				else
				{
					status = HttpServletResponse.SC_CREATED;
					message = "File created successfully";
				}
				//indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, fs, objid, cmpid, fileid, type, true, null, null
				);

				Map info = status( status, message );

				// delete any existing metadata when replacing files
				if ( overwrite )
				{
					Map delInfo = fileDeleteMetadata(objid, cmpid, fileid, ts, false);
					int delStat = getParamInt(delInfo,"statusCode",200);
					if ( delStat > 299 )
					{
						info.put(
							"existing_metadata_deletion",
							delStat + ": " + delInfo.get("message")
						);
					}
				}

				// perform characterization and pass along any error messages
				Map charInfo = fileCharacterize(
					objid, cmpid, fileid, true, fs, ts, es, params
				);
				int charStat = getParamInt(charInfo,"statusCode",200);
				if ( charStat > 299 )
				{
					info.put(
						"characterization",
						charStat + ": " + charInfo.get("message")
					);
				}

				return info;
			}
			else
			{
				if ( overwrite ) { message = "File update failed"; }
				else { message = "File creation failed"; }
				createEvent(
					ts, es, fs, objid, cmpid, fileid, type, false, null,
					"Failed to upload file"
				);

				return error( message, null );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error uploading file", ex );
			return error( "Error uploading file", ex );
		}
		finally
		{
			uploadCount--;
		}
	}
	public Map fileDelete( String objid, String cmpid, String fileid,
		FileStore fs, TripleStore ts, TripleStore es )
	{
		try
		{
			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object identifier required", null
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required", null
				);
			}

			// make sure the file exists
			if ( !fs.exists( objid, cmpid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist", null
				);
			}

			// delete the file
			fs.trash( objid, cmpid, fileid );
			boolean successful = !fs.exists(objid,cmpid,fileid);

			if ( successful )
			{
				//indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, fs, objid, cmpid, fileid, Event.FILE_DELETED, true,
					null, null
				);

				// FILE_META: update file metadata
				fileDeleteMetadata( objid, cmpid, fileid, ts, false );

				return status( "File deleted successfully" );
			}
			else
			{
				createEvent(
					ts, es, fs, objid, cmpid, fileid, Event.FILE_DELETED, false,
					null, null
				);
				return error(
					"Failed to delete file: " + objid + fileString(cmpid,fileid), null
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting file", ex );
			return error( "Error deleting file", ex );
		}
	}
	private static String listToString(String[] arr)
	{
		String val = "";
		for ( int i = 0; arr != null && i < arr.length; i++ )
		{
			if ( !val.equals("") ) { val += ", "; }
			val += arr[i];
		}
		return val;
	}
	public Map fileDerivatives( String objid, String cmpid, String fileid,
		boolean overwrite, FileStore fs, TripleStore ts, TripleStore es,
		Map<String, String[]> params )
	{
		String derName = null;
		StatementIterator sit = null;
		String errorMessage = "";
		try
		{
			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object identifier required", null
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required", null
				);
			}
			if ( derivativesRes == null || derivativesRes.size() == 0 )
			{
				return error(
					"Derivative dimensions not configured.", null
				);
			}

			Identifier oid = createID( objid, null, null );
			Identifier fid = createID( objid, cmpid, fileid );
			Identifier hasFile = Identifier.publicURI( prNS + "hasFile" );

			String[] sizes = params.get("size");

			// check for comma-separate size list
			if ( sizes != null && sizes.length == 1
				&& sizes[0].indexOf(",") != -1 )
			{
				sizes = sizes[0].split(",");
			}
			if ( sizes != null)
			{
				int len = sizes.length;
				for ( int i=0; i<len; i++ )
				{
					derName = sizes[i] = sizes[i].trim();
					if ( !derivativesRes.containsKey( derName ) )
					{
						return error(
								HttpServletResponse.SC_BAD_REQUEST,
								"Unknown derivative name: " + derName, null
							);
					}
					Identifier dfid = createID( objid, cmpid, derName + derivativesExt );
					if ( !overwrite )
					{
						sit = ts.listStatements(oid, hasFile, dfid);
						if(sit.hasNext())
						{
							return error(
								HttpServletResponse.SC_FORBIDDEN,
								"Derivative " + dfid.getId()
									+ " already exists. Please use PUT instead", null
							);
						}
					}
				}
			}
			else
			{
				int i = 0;
				sizes = new String[derivativesRes.size()];
				for ( Iterator it=derivativesRes.keySet().iterator(); it.hasNext(); )
				{
					sizes[i++] = (String)it.next();
				}
			}

			int frame = 0;
			String[] frameNo = params.get("frame");
			if ( frameNo != null && frameNo.length > 0 )
			{
				frame = Integer.parseInt( frameNo[0] );
			}
			ImageMagick magick = new ImageMagick( magickCommand );
			Ffmpeg ffmpeg = new Ffmpeg(ffmpegCommand);
			String[] sizewh = null;
			String derid = null;
			for ( int i=0; i<sizes.length; i++ )
			{
				boolean successful = false;
				derName = sizes[i];
				sizewh = derivativesRes.get(derName).split("x");
				derid = derName + derivativesExt;
				if(fileid.endsWith(".mp3") || fileid.endsWith(".wav")) {
					successful = ffmpeg.makeDerivative(
								fs, objid, cmpid, fileid, derName+".mp3"
					);
				} else {
					successful = magick.makeDerivative(
					    fs, objid, cmpid, fileid, derid,
						Integer.parseInt(sizewh[0]),
						Integer.parseInt(sizewh[1]), frame
					);
				}
				if(! successful )
				{
					errorMessage += "Error derivatives creation: "
						+ objid + "/" + fid + "\n";
				}

				String[] uses = {derivativesUse.get(derName)};
				Map<String,String[]> params2 = new HashMap<String,String[]>();
				params2.putAll( params );
				params2.put("use", uses);
				fileCharacterize(
					objid, cmpid, derid, overwrite, fs, ts, es, params2
				);
				//indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, fs, objid, cmpid, derid, Event.DERIVATIVE_CREATED,
					true, null, null
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error derivatives creation", ex );
			return error( "Error derivatives creation", ex );
		}
		finally
		{
			if(sit != null) {
				sit.close();
				sit = null;
			}
		}
		if ( errorMessage.length() > 0)
		{
			return error( errorMessage, null );
		}
		else
		{
			return status (HttpServletResponse.SC_CREATED, "Derivatives created successfully");  // DAMS_MGR

		}
	}
	public Map fileExists( String objid, String cmpid, String fileid,
		FileStore fs )
	{
		try
		{
			if ( fs.exists( objid, cmpid, fileid ) )
			{
				return status( "File exists" );
			}
			else
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, "File does not exist", null
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error checking file existence", ex );
			return error( "Error processing request", ex );
		}
	}
    public Map extractText( String objid, String cmpid, String fileid,
		FileStore fs )
	{
		Map info = new LinkedHashMap();
		InputStream in = null;
		String fn = objid;
		if ( cmpid != null ) { fn += "/" + cmpid; }
		fn += "/" + fileid;
		try
		{
			// make sure objid and fileid are specified
			if ( objid == null || fileid == null )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object and file must be specified", null
				);
			}

			// make sure file exists
			if ( !fs.exists( objid, cmpid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, "File does not exist", null
				);
			}

			// extract text
			in = fs.getInputStream( objid, cmpid, fileid );
			//String text = PDFParser.getContent( in, objid );
			ContentHandler contentHandler = new BodyContentHandler((int)maxUploadSize*4);
			Metadata metadata = new Metadata();
			metadata.set(Metadata.RESOURCE_NAME_KEY, fileid);
			Parser parser = new AutoDetectParser();
			ParseContext parserContext = new ParseContext();
			parser.parse(in, contentHandler, metadata, parserContext);
			info.put( "text",contentHandler.toString() );
		}
		catch ( Exception ex )
		{
			log.warn( "Error extracting text from " + fn, ex );
			return error( "Error extracting text from " + fn, ex);
		}
		return info;
	}
	public Map fileFixity( String objid, String cmpid, String fileid,
		FileStore fs, TripleStore ts, TripleStore es )
	{
		Map info = new LinkedHashMap();
		InputStream in = null;
		try
		{
			// retrieve recorded checksums from triplestore
			Map<String,String> recorded = recordedChecksums(
				objid, cmpid, fileid, ts
			);
			boolean crcB = recorded.containsKey("crc32");
			boolean md5B = recorded.containsKey("md5");
			boolean sha1B = recorded.containsKey("sha1");
			boolean sha256B = recorded.containsKey("sha256");
			boolean sha512B = recorded.containsKey("sha512");

			// calculate current checksums from file
			in = fs.getInputStream( objid, cmpid, fileid );
			// uses same core checksuming code as JHove, do we need this
			// duplication?? only benefit is not having to pull data to local
			// filesystem
			Map<String,String> actual = Checksum.checksums(
				in, null, crcB, md5B, sha1B, sha256B, sha512B
			);

			// compare and build response
			boolean success = true;
			String detail = "";
			Iterator<String> it = recorded.keySet().iterator();
			while ( it.hasNext() )
			{
				String alg = it.next();
				String rec = recorded.get(alg);
				String act = actual.get(alg);
				if ( rec != null && act != null && rec.equals(act) )
				{
					info.put( alg, act );
					detail += alg + "=" + act + " ";
				}
				else
				{
					String msg = "recorded: " + rec + ", calculated: " + act;
					info.put( alg, msg );
					info.put(
						"statusCode",
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR
					);
					success = false;

					detail += alg + ": " + msg + ". ";
				}
			}

			// log fixity check event
			if ( es != null )
			{
				createEvent(
					ts, es, fs, objid, cmpid, fileid, Event.CHECKSUM_VERIFIED,
					success, detail, null
				);
			}
		}
		catch ( Exception ex )
		{
			log.error( "Error comparing checksums", ex );
			return error( "Error comparing checksums", ex );
		}
		finally
		{
			if ( in != null ) { try {in.close();} catch (Exception ex2){} }
		}
		return info;
	}
	public Map identifierCreate( String name, int count )
	{
		// lookup minter URL and add count parameter
		String minterURL = idMinters.get(name);
		if ( minterURL == null )
		{
			return error("Unknown id minter: " + name, null);
		}
		minterURL += count;

		try
		{
			// generate id and check output
			String result = HttpUtil.get(minterURL);
			if ( result == null || !result.startsWith("id: ") )
			{
				return error("Failed to generate id", null);
			}
			else
			{
				String[] ids = result.split("\\n");
				for ( int i = 0; i < ids.length; i++ )
				{
					ids[i] = ids[i].replaceAll(".*/","");
				}
				List idList = Arrays.asList(ids);
				Map info = new LinkedHashMap();
				info.put("ids",idList);
				return info;
			}
		}
		catch ( Exception ex )
		{
			return error( "Error generating id", null );
		}
	}
	protected Map objectEdit( String objid, boolean create, InputStream in,
		String mode, String adds, String updates, String deletes,
		TripleStore ts, TripleStore es, FileStore fs )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST, "No subject provided", null
				);
			}

	   		// make sure appropriate method is being used to create/update
			Identifier id = createID( objid, null, null );
			if ( create && ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_FORBIDDEN,
			   		"Object already exists, use PUT to update", null
		   		);
			}
			else if ( !create && !ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_FORBIDDEN,
			   		"Object does not exist, use POST to create", null
		  		);
			}

			// process uploaded file if present
			if ( in != null )
			{
				if ( mode == null || mode.equals("") || mode.equals("all")
					|| mode.equals("add") )
				{
                    boolean deleteFirst = false;
                    if ( !create && mode != null && mode.equals("all") )
                    {
                        // mode=all: delete object and replace
                        deleteFirst = true;
					}

					try
					{
						// ingest RDF/XML from inputstream
						Set<String> errors = TripleStoreUtil.loadRDFXML(in, deleteFirst, ts, nsmap, validClasses, validProperties);
						if ( errors != null && errors.size() > 0 )
						{
							Map resp = error(
								HttpServletResponse.SC_BAD_REQUEST, "Invalid RDF input", null
							);
							resp.put("errors", errors);
							return resp;
						}

						// success
						int status = -1;
						String message = null;
						String type = null;
						if ( create ) { type = Event.RECORD_CREATED; }
						else { type = Event.RECORD_EDITED; }
						if ( create )
						{
							status = HttpServletResponse.SC_CREATED;
							message = "Object created successfully";
						}
						else
						{
							status = HttpServletResponse.SC_OK;
							message = "Object saved successfully";
						}
						//indexQueue(objid,"modifyObject");
						createEvent(
							ts, es, fs, objid, null, null, type, true, null, null
						);
						return status( status, message );
					}
					catch ( Exception ex )
					{
						log.warn("Error loading metadata", ex );
						return error( "Error loading new metadata", ex );
					}
				}
				return error( "Unsupported mode: " + mode, null );
			}
			// otherwise, look for JSON adds
			else
			{
				if ( adds != null && !adds.equals("") )
				{
					// save data to the triplestore
					Edit edit = new Edit(
						backupDir, adds, updates, deletes, objid, ts, nsmap
					);
					edit.saveBackup();
					String type = null;
					if ( create ) { type = Event.RECORD_CREATED; }
					else { type = Event.RECORD_EDITED; }
					if ( edit.update() )
					{
						// success
						int status = -1;
						String message = null;
						if ( create )
						{
							status = HttpServletResponse.SC_CREATED;
							message = "Object created successfully";
						}
						else
						{
							status = HttpServletResponse.SC_OK;
							message = "Object saved successfully";
						}
						//indexQueue(objid,"modifyObject");
						createEvent(
							ts, es, fs, objid, null, null, type, true, null, null
						);
						edit.removeBackup();
						return status( status, message );
					}
					else
					{
						// failure
						String msg = edit.getException().toString();
						createEvent(
							ts, es, fs, objid, null, null, type, false,
							null, msg
						);
						return error( msg, edit.getException() );
					}
				}
				else
				{
					return error(
						HttpServletResponse.SC_BAD_REQUEST,
						"Object metadata must be supplied as a file upload or in the adds parameter",
						null
					);
				}
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error editing object", ex );
			return error( "Error editing object", ex );
		}
	}
	public Map objectDelete( String objid, TripleStore ts, TripleStore es,
		FileStore fs )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "No subject provided", null );
			}

	   		// make sure appropriate method is being used to create/update
			Identifier id = createID( objid, null, null );
			if ( !ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"Object does not exist", null
		   		);
			}
			ts.removeObject(id);

			if ( ! ts.exists(id) )
			{
				//indexQueue(objid,"purgeObject");
				createEvent(
					ts, es, fs, objid, null, null, Event.RECORD_DELETED, true,
					null, null
				);
				return status( "Object deleted successfully" );
			}
			else
			{
				createEvent(
					ts, es, fs, objid, null, null, Event.RECORD_DELETED, false,
					null, null
				);
				return error( "Object deletion failed", null );
			}
			//ts.addLiteralStatement(id,updatedFlag,"delete",id);
			//String userID = request.getParameter("userID");
			//if ( userID != null && !userID.trim().equals("") )
			//{
			//	ts.addLiteralStatement(id,updatedFlag,userID,id);
			//}
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting object", ex );
			return error( "Error deleting object", ex );
		}
	}
	public Map selectiveDelete( String objid, String cmpid, String[] predicates,
		TripleStore ts, TripleStore es, FileStore fs )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST, "No subject provided", null
				);
			}

	   		// make sure object exists
			Identifier sub = createID( objid, cmpid, null );
			Identifier id = createID( objid, null, null );
			if ( !ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"Object does not exist", null
		   		);
			}

			if ( predicates == null || predicates.length == 0 )
			{
		   		return error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"No predicates specified for deletion", null
		   		);
			}

			// remove each predicate...
			ArkTranslator trans = new ArkTranslator( ts, nsmap );
			for ( int i = 0; i < predicates.length; i++ )
			{
				Identifier pre = createPred( predicates[i] );
				TripleStoreUtil.recursiveDelete( id, sub, pre, null, null, ts );
			}

			//indexQueue(objid,"modifyObject");
			createEvent(
				ts, es, fs, objid, null, null, Event.RECORD_EDITED, true,
				null, null
			);
			return status( "Predicate deleted successfully" );
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting predicates", ex );
			try {createEvent(
				ts, es, fs, objid, null, null, Event.RECORD_EDITED, false,
				null, null
			);} catch ( Exception ex2 ) {}
			return error( "Error deleting predicates", ex );
		}
	}
	public String objectBatch( List<String> objids, TripleStore ts,
		TripleStore es ) throws Exception
	{
		ArrayList<String> docs = new ArrayList<String>();
		ArrayList<String> errors = new ArrayList<String>();

		// retrieve rdf/xml for each record
		for ( int i = 0; i < objids.size(); i++ )
		{
			try
			{
				Identifier id = createID( objids.get(i), null, null );
				if ( ts.exists(id) )
				{
					DAMSObject obj = new DAMSObject(ts, es, objids.get(i), nsmap);
					docs.add( obj.getRDFXML(true) );
				}
				else if ( es != null && es.exists(id) )
				{
					DAMSObject obj = new DAMSObject(es, null, objids.get(i), nsmap);
					docs.add( obj.getRDFXML(true) );
				}
				else
				{
					errors.add( objids.get(i) + ": not found" );
				}
			}
			catch ( Exception ex )
			{
				errors.add( objids.get(i) + ": " + ex.toString() );
			}
		}

		// combine xml documents
		String xml = null;
		if ( docs.size() == 1 )
		{
			xml = docs.get(0);
		}
		else if ( docs.size() > 1 )
		{
			try
			{
				Document doc = DocumentHelper.parseText(docs.get(0));
				Element rdf = doc.getRootElement();
				for ( int i = 1; i < docs.size(); i++ )
				{
					Document doc2 = DocumentHelper.parseText(docs.get(i));
					Iterator it = doc2.getRootElement().elementIterator();
					if ( it.hasNext() )
					{
						Element e = (Element)it.next();
						rdf.add( e.detach() );
					}
				}
				xml = doc.asXML();
			}
			catch ( Exception ex )
			{
				errors.add(
					"Error combining records into a batch: " + ex.toString()
				);
			}
		}

		// check errors
		if ( errors.size() > 0 )
		{
			String msg = errors.get(0);
			for ( int i = 1; i < errors.size(); i++ )
			{
				msg += "; " + errors.get(i);
			}
			throw new Exception( msg );
		}

		return xml;
	}
	public Map objectShow( String objid, TripleStore ts, TripleStore es )
	{
		// output = metadata: object
		DAMSObject obj = null;
		try
		{
			if ( objid == null || objid.equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object id must be specified", null
				);
			}
			Identifier id = createID( objid, null, null );
			if ( ts == null ) { log.error("NULL TRIPLESTORE"); }
			if ( ts.exists(id) )
			{
				obj = new DAMSObject( ts, es, objid, nsmap );
			}
			else if ( es != null && es.exists(id) )
			{
				obj = new DAMSObject( es, null, objid, nsmap );
			}
			else
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND,
					"Object does not exist", null
				);
			}

			Map info = new HashMap();
			info.put("obj",obj);
			return info;
		}
		catch ( Exception ex )
		{
			log.warn( "Error showing object", ex );
			return error( "Error processing request", ex );
		}
	}
	private Map mintDOI( String objid, TripleStore ts, TripleStore es, FileStore fs,
		HttpServletResponse res ) throws Exception
	{
		// load object XML
		Map m = objectShow( objid, ts, es );
		String xml = null;
		if ( m.get("obj") != null )
		{
			DAMSObject obj = (DAMSObject)m.get("obj");
			xml = obj.getRDFXML(true);
		}

		// transform to datacite format with XSL
		String datacite = xslt( xml, "datacite.xsl", null, null );

		// mint doi
		String doi = ezid.mintDOI( nsmap.get("damsid") + objid, datacite );
		String doiURL = doi.replaceAll("doi:","http://doi.org/");
		log.info("Minted DOI: " + doiURL + " for " + objid);

		// add doi to object
		Document doc = DocumentHelper.parseText(xml);
		Element obj = (Element)doc.getRootElement().elements().get(0);
		Element doiNote = obj.addElement("dams:note").addElement("dams:Note");
		doiNote.addElement("dams:type").setText("identifier");
		doiNote.addElement("dams:displayLabel").setText("DOI");
		doiNote.addElement("rdf:value").setText(doiURL);

		// update preferred citation note
		List cites = doc.selectNodes(
				"/rdf:RDF/*/dams:note/dams:Note[dams:type='preferred citation']/rdf:value");
		if ( cites.size() > 0 )
		{
			Element cite = (Element)cites.get(0);
			cite.setText( cite.getText() + " " + doiURL );
		}

		// save RDF
		Map info = objectEdit( objid, false, new ByteArrayInputStream(doc.asXML().getBytes()),
				"all", null, null, null, ts, es, fs );

		// queue for reindexing
		indexQueue( objid, "modifyObject" );

		info.put("message", "Minted DOI: " + doiURL);
		return info;
	}

	/**
	 * Retrieve the RDF/XML for an object, and transform it using XSLT (passing
	 * the fileid as a parameter to the stylesheet).
	 * @param objid Object identifier.
	 * @param fileid File identifier.
	 * @param ts TripleStore to retrieve the object from.
	 * @param xslName Filename of XSL stylesheet.
	**/
	public void objectTransform( String objid, String cmpid, String fileid,
		boolean export, TripleStore ts, TripleStore es, String xslName,
		Map<String,String[]> params, String pathInfo, HttpServletResponse res )
	{
		objectTransform(
			objid, cmpid, fileid, export, ts, es, xslName,
			null, null, params, pathInfo, res
		);
	}
	/**
	 * Retrieve the RDF/XML for an object, and transform it using XSLT (passing
	 * the fileid as a parameter to the stylesheet) optionally saving the
	 * output as a new file.
	 * @param objid Object identifier.
	 * @param fileid Component identifier.
	 * @param fileid File identifier.
	 * @param ts TripleStore to retrieve the object from.
	 * @param xslName Filename of XSL stylesheet.
	 * @param fs If not null, save the result to this FileStore.
	 * @param destid If not null, save the result as this file.
	**/
	public void objectTransform( String objid, String cmpid, String fileid,
		boolean export, TripleStore ts, TripleStore es, String xslName,
		FileStore fs, String destid, Map<String,String[]> params,
		String pathInfo, HttpServletResponse res )
	{
		try
		{
			// get object from triplestore as Document
			Map m = objectShow( objid, ts, es );
			String xml = null;
			if ( m.get("obj") != null )
			{
				DAMSObject obj = (DAMSObject)m.get("obj");
				xml = obj.getRDFXML(export);
			}
			else
			{
				// if there is no object, output the error message we received
				output( m, params, pathInfo, res );
			}

			// transform metadata and output
			if ( fileid != null ) { params.put("fileid",new String[]{fileid}); }
			if ( cmpid != null ) { params.put("cmpid",new String[]{cmpid}); }
			String content = xslt( xml, xslName, params, queryString(params) );
			output( res.SC_OK, content, "application/xml", res );

			// if destid specified, then also save output
			if ( destid != null )
			{
				fs.write( objid, cmpid, destid, content.getBytes() );
				//indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, fs, objid, cmpid, fileid, Event.RECORD_TRANSFORMED,
					true, null, null
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error transforming metadata", ex );
			try
			{
				createEvent(
					ts, es, fs, objid, cmpid, fileid, Event.RECORD_TRANSFORMED,
					false, null, ex.toString()
				);
			}
			catch ( Exception ex2 ) { log.error("Error creating event",ex2); }
			output(
				error("Error transforming metadata", ex), params, pathInfo, res
			);
		}
	}
	public Map objectExists( String objid, TripleStore ts )
	{
		try
		{
			if ( !objid.startsWith("http") ) { objid = idNS + objid; }
			Identifier id = Identifier.publicURI(objid);
			if ( ts.exists( id ) )
			{
				return status( "Object exists" );
			}
			else
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist", null
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error checking object existence", ex );
			return error( "Error checking object existence", ex );
		}
	}
	public Map objectValidate( String objid, FileStore fs, TripleStore ts,
		TripleStore es ) throws TripleStoreException
	{
		// load object
		Map info = objectShow( objid, ts, es );
		if ( info.get("obj") == null )
		{
			return error( HttpServletResponse.SC_NOT_FOUND, "Object does not exist", null );
		}

		// validate model
		DAMSObject obj = (DAMSObject)info.get("obj");
		Set<String> errors = Validator.validateModel(
			obj.asModel(false), validClasses, validProperties
		);
		if ( errors != null && errors.size() > 0 )
		{
			Map resp = error( "Validation failure", null );
			resp.put( "errors", errors );
			return resp;
		}
		else
		{
			return status( "Validation successful" );
		}
	}

	public Map predicateList( TripleStore ts )
	{
		try
		{
			// setup damsobject
			ArkTranslator trans = new ArkTranslator( ts, nsmap );
			Map<String,String> predicates = trans.predicateMap();

			// build map and display
			Map info = new LinkedHashMap();
			info.put("predicates",predicates);
			return info;
		}
		catch ( Exception ex )
		{
			log.error( "Error looking up predicate map", ex );
			return error( "Error looking up predicate map", ex );
		}
	}

	private Map fileDeleteMetadata( String objid, String cmpid, String fileid,
		TripleStore ts, boolean keepSourceCapture ) throws TripleStoreException
	{
		try
		{
			// identifier object for the file
			Identifier parent = createID( objid, null, null );
			Identifier sub = createID( objid, cmpid, null );
			Identifier fileID = createID( objid, cmpid, fileid );
			Identifier hasFile = Identifier.publicURI( prNS + "hasFile" );
			Identifier sourceCapture = null;
			if ( keepSourceCapture )
			{
				sourceCapture = Identifier.publicURI( prNS + "sourceCapture" );
			}

			// delete file metadata (n.b. first arg is object identifer, not
			// the subject of the triple, so this works for files attached
			// to components, etc.)
			TripleStoreUtil.recursiveDelete(
				parent, sub, hasFile, fileID, sourceCapture, ts
			);

			// delete links from object/components
			if ( !keepSourceCapture )
			{
				ts.removeStatements( null, null, fileID );
			}

			return status("File metadata deleted successfully");
		}
		catch ( Exception ex )
		{
			log.error( "Error deleting file metadata", ex );
			return error("Error deleting file metadata", ex );
		}
	}

	/**
	 * Bulk indexing queue.
	**/
	private Map indexQueue( String[] ids, String type )
	{
		Map info = null;

		// return error if no ids provided
		if ( ids == null || ids.length == 0 )
		{
			info = error(
				HttpServletResponse.SC_BAD_REQUEST,
				"No identifier specified", null
			);
		}

		// send each id to the queue
		List<String> errors = new ArrayList<String>();
		int queueTotal = ids.length;
		int queueSuccess = 0;
		for ( int i = 0; i < ids.length; i++ )
		{
			String error = indexQueue(ids[i],type);
			if ( error != null ) { errors.add( error ); }
			else { queueSuccess++; }
		}

		// return error/success info
		info = new LinkedHashMap();
		info.put( "queueTotal", queueTotal );
		info.put( "queueSuccess", queueTotal );
		info.put( "errors", errors );
		return info;
	}

	/**
	 * Send object to solrizer indexing queue.
	 * @param objid Object id
	 * @param type 'purgeObject' for deletes, 'modifyObject' for other
	 *   operations.
	**/
	private String indexQueue( String objid, String type )
	{
		String error = null;
		if ( queueEnabled && queueSession != null )
		{
			try
			{
				TextMessage msg = queueSession.createTextMessage(
					"DAMS Queue Message: " + objid + " (" + type + ")"
				);
				msg.setStringProperty("pid",objid);
				msg.setStringProperty("methodName",type);
				queueProducer.send(msg);
			}
			catch ( Exception ex )
			{
				log.warn("Error sending event to queue", ex );
				error = "Error sending object to queue: " + ex.toString();
			}
		}
		return error;
	}
	protected void createEvent( TripleStore ts, TripleStore es, FileStore fs,
		String objid, String cmpid, String fileid, String type, boolean success,
		String detail, String outcomeNote )
			throws TripleStoreException
	{
		try
		{
			// mint ARK for event
			String minterURL = idMinters.get(minterDefault) + "1";
			String eventARK = HttpUtil.get(minterURL);
			eventARK = eventARK.replaceAll(".*/","").trim();
			Identifier eventID = Identifier.publicURI( idNS + eventARK );

			// lookup user identifier
			// ZZZ: AUTH: who is making the request? hydra or end-user?
			Identifier userID = null;

			// create event object and save to the triplestore
			String obj = objid.startsWith("http") ? objid : idNS + objid;
			Identifier objID = Identifier.publicURI(obj);
			if ( cmpid != null ) { obj += "/" + cmpid; }
			if ( fileid != null ) { obj += "/" + fileid; }
			Identifier subID = Identifier.publicURI( obj );
			Event e = new Event(
				eventID, objID, subID, userID, success, type,
				detail, outcomeNote
			);
			e.save(ts,es);

			// serialize update rdfxml to disk
			try
			{
				fs.write(
					objid, null, "rdf.xml", cacheUpdate(objid,ts,es).getBytes()
				);
			}
			catch ( Exception inner )
			{
				log.warn("Error serializing RDF/XML on update", inner);
			}
		}
		catch ( IOException ex )
		{
			log.warn( "Error minting event ARK", ex );
		}
	}

	//=========================================================================
	// Methods that handle their own response
	//=========================================================================
	public void fileShow( String objid, String cmpid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// ZZZ: access control
		req.setAttribute(
			"edu.ucsd.library.dams.api.DAMSAPIServlet.authorized","true"
		);
		String url = "/file/" + objid + fileString(cmpid, fileid);
		if ( req.getQueryString() != null && !req.getQueryString().equals("") )
		{
			url += "?" + req.getQueryString();
		}
		String fs = req.getParameter("fs");
		if ( fs == null || fs.trim().equals("") )
		{
			fs = lookupFileStore(objid, cmpid, fileid);
		}
		if ( fs != null )
		{
			url += ( url.indexOf("?") > -1 ) ? "&" : "?";
			url += "fs=" + fs;
			log.info("added filestore=" + fs);
		}
		try
		{
			req.getRequestDispatcher(url).forward( req, res );
		}
		catch ( Exception ex )
		{
			log.error("Error sending redirect: " + ex.toString());
			log.warn( "Error sending redirect", ex );
		}
	}
	private void sparqlQuery( String sparql, TripleStore ts,
		Map<String,String[]> params, String pathInfo,
		HttpServletResponse res ) throws Exception
	{
		if ( sparql == null )
		{
                Map err = error(
                    HttpServletResponse.SC_BAD_REQUEST, "No query specified.", null
                );
                output( err, params, pathInfo, res );
                return;
		}
		else
		{
			log.info("sparql: " + sparql);
		}

		// sparql query
		BindingIterator objs = ts.sparqlSelect(sparql);

		// start output
		String sparqlNS = "http://www.w3.org/2005/sparql-results#";
		res.setContentType("application/sparql-results+xml");
		OutputStream out = res.getOutputStream();
		XMLOutputFactory factory = XMLOutputFactory.newInstance();
		XMLStreamWriter stream = factory.createXMLStreamWriter(out);
		stream.setDefaultNamespace( sparqlNS );
		stream.writeStartDocument();
		stream.writeStartElement( "sparql" );

		// output bindings
		boolean headerWritten = false;
		while ( objs.hasNext() )
		{
			Map<String,String> binding = objs.nextBinding();

			// write header on first binding
			if ( !headerWritten )
			{
				Iterator<String> it = binding.keySet().iterator();
				stream.writeStartElement("head");
				while ( it.hasNext() )
				{
					String k = it.next();
					stream.writeStartElement( "variable");
					stream.writeAttribute("name",k);
					stream.writeEndElement();
				}
				stream.writeEndElement();
				stream.writeStartElement( "results"); // ordered='false' distinct='false'
				headerWritten = true;
			}

			stream.writeStartElement( "result");
			Iterator<String> it = binding.keySet().iterator();
			while ( it.hasNext() )
			{
				String k = it.next();
				String v = binding.get(k);
				stream.writeStartElement( "binding");
				stream.writeAttribute("name",k);
				String type = null;
				if ( v.startsWith("\"") && v.endsWith("\"") )
				{
					type = "literal";
					v = v.substring(1,v.length()-1);
				}
				else if ( v.startsWith("_:") )
				{
					type = "bnode";
					v = v.substring(2);
				}
				else
				{
					type = "uri";
				}
				stream.writeStartElement(type);
				stream.writeCharacters(v);
				stream.writeEndElement();
				stream.writeEndElement();
			}
			stream.writeEndElement();
		}

		// finish output
		stream.writeEndElement();
		stream.writeEndDocument();
		stream.flush();
		stream.close();
	}
	
	/**
	 * Merge records in the id parameter to record objid
	 * @param oid
	 * @param params
	 * @throws TripleStoreException 
	 */
	private Map mergeRecords(String objid, Map<String,String[]> params, TripleStore ts, TripleStore es, FileStore fs) throws TripleStoreException
	{
		
		// make sure an identifier is specified
		if ( objid == null || objid.trim().equals("") )
		{
			return error(
				HttpServletResponse.SC_BAD_REQUEST, "No subject provided", null
			);
		}

   		// make sure the target record exists for merging
		Identifier id = createID( objid, null, null );
		if ( !ts.exists(id) )
		{
	   		return error(
		   		HttpServletResponse.SC_FORBIDDEN,
		   		"The selected record does not exist: " + objid, null
	  		);
		}
		
		
		Map info = null;	
		String message = null;
		String tmpid = null;
		String[] ids = params.get("id");
		List<String> recordsAffected = new ArrayList<String>();
		List<String> records2merge = new ArrayList<String>();
		for ( int i=0; i<ids.length; i++ )
		{
			tmpid = ids[i];
			String[] mids = tmpid.split(",");
			for ( int j=0; j<mids.length; j++ )
			{
				tmpid = mids[j].trim();
				if ( tmpid.length() > 0 )
				{
					if ( !ts.exists( createID(tmpid, null, null)) )
					{
				   		return error(
					   		HttpServletResponse.SC_FORBIDDEN,
					   		"Record for merging does not exist: " + tmpid, null
				  		);
					}
					records2merge.add( tmpid );
				}
			}
		}
		
		// make sure an identifier(s) for merging is specified
		if ( records2merge.size() == 0 )
		{
			return error(
				HttpServletResponse.SC_BAD_REQUEST, "No subject for merging provided in id parameter", null
			);
		}
		
		boolean successful = true;
		String merid = null;
		String records2MergeStr = "";
		Statement stmt  = null;
		StatementIterator sit = null;
		int idNSLength = idNS.length();
		
		for ( Iterator<String> it=records2merge.iterator(); it.hasNext(); )
		{	
			merid = it.next();
			records2MergeStr += ( records2MergeStr.length()>0?", ":"" ) + merid;
			boolean merged = true;
			
			Identifier mergedID = createID( merid, null, null );
			try {
				
				sit = ts.listStatements( null, null, mergedID );
				while ( sit.hasNext() )
				{
					stmt = sit.nextStatement();
					Identifier parent = stmt.getParent();
					
					Identifier tmpID = stmt.getSubject();
					List<String> subs = new ArrayList<String>();
					subs.add( parent.getId() );
					
					if( subs.size() == 0 )
					{
						merged = false;
						//Unbound record or other unknown nodes?
						message = "Unable to find subject parent " + stmt.getObject() + ": " + stmt.toString();
						log.warn( message );
						updateErrorInfo( info, message );
					}
					else if( subs.size() == 1 )
					{
						//Parent subject
						String subAffected = subs.get(0);
						
						//Change the linked records to link to objid choosen
						Identifier parentID = createID( subAffected, null, null );
						cacheRemove(subAffected);
						//Update the linking
						updateResource(stmt, ts, id, parentID);
						createEvent(
								ts, es, fs, subAffected, null, null, Event.RECORD_EDITED, true, null, null
						);
						
						log.info("Updated record " + parentID.getId() + " to link to " + id.getId() + ".");
						
						//Looking up records that are affected
						processAffectedRecords( subs, recordsAffected );
						while(subs.size() > 0)
						{
							recordsAffected.addAll( subs );
							List<String> subAs = new ArrayList<String>();
							for(Iterator<String> itSubs=subs.iterator(); itSubs.hasNext();)
							{
								String subTmp = itSubs.next();
								retrieveSubject( createID( subTmp, null, null ), ts, subAs);
							}
							processAffectedRecords( subAs, recordsAffected );
							subs = subAs;
						}
					}
					else
					{
						merged = false;
						message = "";
						for(Iterator<String> itTmp=subs.iterator(); itTmp.hasNext();)
							message = (message.length()>0?", ":"") + message;
						message = "Failed to merge " +  mergedID.getId() + " to " + objid + ". multiple parent object found for " + mergedID.getId() + ": " + message;
						log.error( message );
						updateErrorInfo( info, message );
					}
				}
				
				sit.close();
				sit = null;
				
				//Delete the merged record from the triplestore if there are no records linked to it
				sit = ts.listStatements( null, null, mergedID );
				if( !sit.hasNext() )
				{
					cacheRemove(merid);
					ts.removeObject(mergedID);
					
					log.info("Merging " + records2MergeStr + " to " + objid + " deleted " + mergedID.getId()  + " from the triplestore.");
					if ( ! ts.exists(id) )
					{
						createEvent(
								ts, es, fs, merid, null, null, Event.RECORD_DELETED, true, null, null
						);
					}
					else
					{
						createEvent(
								ts, es, fs, merid, null, null, Event.RECORD_DELETED, false, null, null
						);
						updateErrorInfo( info, "Object deletion failed: " +  mergedID.getId());
					}
				}
				else
				{
					successful = false;
					message = "Can't delete the merged records " + mergedID.getId() + " from the triplestore. The following triples link to it: " + sit.nextStatement().toString();
					updateErrorInfo( info, message );
					log.error( message );
				}
			}
			finally
			{
				if ( sit != null )
				{
					sit.close();
					sit = null;
				}
			}

			//Remove the merged record from SOLR
			if ( merged )
			{
				message = indexQueue( merid.replace(idNS, ""), "purgeObject" );
				if ( message != null && message.length() > 0 )
				{
					successful = false;
					updateErrorInfo( info, message );
				}
				log.info("Records merging deleted record " + merid.replace(idNS, "") + " from SOLR.");
			}
			else
			{
				successful = false;
			}
			
			//Update SOLR for records affected
			for ( Iterator<String> ita=recordsAffected.iterator(); ita.hasNext(); )
			{
				message = indexQueue( ita.next(), "modifyObject" );
				if ( message != null && message.length() > 0 )
					updateErrorInfo( info, message );
			}
			log.info("Updated affected records in solr for merging " + records2MergeStr + " to " + objid + ": Total records updated " +  recordsAffected.size());
		}
		if ( successful &&  info == null )
			info = status( 201, "Successfully merged records (" + records2MergeStr + ") to " + objid + ". Total records updated in solr " + recordsAffected.size());
		else if ( info == null )
			updateErrorInfo( info, "Failed to merge records2MergeStr to " + objid + ".");
		
		return info;
	}
	
	private void processAffectedRecords( List<String> subs, List<String> recordsAffected ) throws TripleStoreException
	{
		//Skip those records that are processed previously 
		Object[] subsArr = subs.toArray();
		for(int i=0; i<subsArr.length; i++)
		{
			if ( recordsAffected.contains(subsArr[i]) )
			{
				subs.remove(subsArr[i]);
			}
		}
	}
	
	private void retrieveSubject(Identifier objID, TripleStore ts, List<String> subjects) throws TripleStoreException
	{
		
		StatementIterator sit = null;
		Statement stmt = null;
		List<Identifier> ids = new ArrayList<Identifier>();
		try{
			sit = ts.listStatements( null, null, objID );
			while(sit.hasNext())
			{
				stmt = sit.nextStatement();
				Identifier tmpID = stmt.getSubject();
				String predicate = stmt.getPredicate().getId();
				//Skip looking up the linking for collections and objects
				if ( !(predicate.toLowerCase().endsWith("collection") || predicate.toLowerCase().endsWith("collectionpart") || predicate.toLowerCase().endsWith("haspart") || predicate.toLowerCase().endsWith("object")) )
					ids.add(tmpID);
			}
		}
		finally
		{
			if(sit != null)
			{
				sit.close();
				sit = null;
			}
		}
		
		for ( Iterator<Identifier> it= ids.iterator(); it.hasNext(); )
		{
			int idNSLength = idNS.length();
			
			Identifier id = it.next();
			String tmpid =id.getId();
			if ( tmpid.startsWith(idNS) )
			{
				tmpid = tmpid.substring(idNSLength);
				if ( tmpid.indexOf("/") > 0 ) // Component, File etc.
					tmpid = tmpid.substring(0, tmpid.indexOf("/"));
				
				if ( !subjects.contains(tmpid) )
					subjects.add(tmpid);
			}
			else
			{
				//Internal class instance, BlankNodes or other unknown nodes
				retrieveSubject( id, ts, subjects );
			}
		}
	}
	
	private void updateResource(Statement stmt, TripleStore ts, Identifier newID, Identifier parent) throws TripleStoreException
	{   
		Identifier objID = stmt.getObject();
		//Add statement for the new linking.
		stmt.setObject(newID);
		ts.addStatement(stmt, parent);
		//Remove the original linked record statement
		ts.removeStatements(stmt.getSubject(), stmt.getPredicate(), objID);
	}
	
	private void updateErrorInfo(Map info, String message)
	{
		if(info == null)
		{
			info = error(message, null);
		}
		else
		{
			info = error(info.get("message") + " ; " + message, null);
		}
	}

	/**
	 * Build querystring for Solr.
	**/
	private String queryString( Map<String,String[]> params )
		throws UnsupportedEncodingException
	{
		StringBuffer buf = new StringBuffer();
		for ( Iterator<String> it = params.keySet().iterator(); it.hasNext(); )
		{
			String key = it.next();
			String[] vals = params.get(key);
			for ( int i = 0; vals != null && i < vals.length; i++ )
			{
				if ( buf.length() != 0 )
				{
					buf.append("&");
				}
				buf.append( URLEncoder.encode(key,encodingDefault) );
				buf.append("=");
				if ( vals[i] != null )
				{
					buf.append( URLEncoder.encode(vals[i],encodingDefault) );
				}
			}
		}
		return buf.toString();
	}

	/**
	 * Load object record from solr and find which filestore contains a file
	**/
	private String lookupFileStore(String objid, String cmpid, String fileid)
	{
		String url = solrBase + "/select?q=id:" + objid + "&wt=xml";
		HttpUtil http = new HttpUtil(url);
		String solrxml = null;
		try
		{
			String fs = null;
			http.exec();
			if ( http.status() == 200 )
			{
				solrxml = http.contentBodyAsString();
				//file_3_1.xml_filestore_tesim
				String fieldname = "file_";
				if ( cmpid != null ) { fieldname += cmpid + "_";}
				fieldname += fileid + "_filestore_tesim";
				Document doc = DocumentHelper.parseText(solrxml);
				fs = doc.valueOf("//arr[@name='" + fieldname + "']/str");
			}
			return fs;
		}
		catch ( Exception ex )
		{
			return null;
		}
	}

	//========================================================================
	// Output formatting
	//========================================================================

	protected Map error( String msg, Exception ex )
	{
		return error( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex );
	}
	protected Map error( int errorCode, String msg, Exception ex )
	{
		Map info = status( errorCode, msg );
		if ( ex != null ) { info.put( "exception", ex ); }
		return info;
	}
	protected Map status( String msg )
	{
		return status( HttpServletResponse.SC_OK, msg );
	}
	protected Map status( int statusCode, String msg )
	{
		Map info = new LinkedHashMap();
		info.put( "statusCode", statusCode );
		info.put( "message", msg );
        info.put( "timestamp", dateFormat.format(new Date()) );
		return info;
	}

	public void output( DAMSObject obj, boolean export,
		Map<String,String[]> params, String pathInfo, HttpServletResponse res )
	{
		try
		{
			String format = getParamString(params,"format",formatDefault);
			String content = null;
			String contentType = null;
			if ( format.equals("nt") )
			{
				content = obj.getNTriples(export);
				contentType = "text/plain";
			}
			else if ( format.equals("xml") )
			{
				content = obj.getRDFXML(export);
				contentType = "application/xml";
			}
			else
			{
				Map err = error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Unsupported format: " + format,
					null
				);
				output( err, params, pathInfo, res );
				return;
			}
			output( HttpServletResponse.SC_OK, content, contentType, res );
		}
		catch ( Exception ex )
		{
			log.warn("Error outputting object metadata",ex);
			Map err = error("Error outputting object metadata", ex);
			output( err ,params, pathInfo, res );
		}
	}

	protected void output( Map info, Map<String,String[]> params,
		String pathInfo, HttpServletResponse res )
	{
		int statusCode = 200;
		try
		{
			Integer statusInteger = (Integer)info.get("statusCode");
			if ( statusInteger != null )
			{
				statusCode = statusInteger.intValue();
			}
		}
		catch ( Exception ex ) { log.debug("Error processing status code",ex); }

		// auto-populate basic request info
		info.put( "request", pathInfo );
		if ( statusCode < 400 ) { info.put("status","OK"); }
		else { info.put("status","ERROR"); }

		// convert errors to 200/OK + error message for Flash, etc.
		String flash = getParamString(params,"flash","");
		if ( flash != null && flash.equals("true") )
		{
			info.put("flash","true");
			info.put("statusCode",String.valueOf(statusCode));
			statusCode = res.SC_OK;
		}

		String content = null;
		String contentType = null;
		String format = getParamString(params,"format",formatDefault);

		// if format is not specified/configured, or is invalid, send a warning
		// but don't clobber existing request status/message
		if ( format == null )
		{
			// handle missing formatDefault
			info.put(
				"warning","No format specified and no default format configured"
			);
			format = "xml";
		}
		else if ( !format.equals("json") && !format.equals("html")
			&& !format.equals("properties") && !format.equals("xml") )
		{
			// handle invalid format
			info.put( "warning", "Invalid format: '" + format + "'" );
			format = "xml";
		}

		if ( format.equals("json") )
		{
			// convert exceptions to strings b/c toJSONString can't handle them
			Exception e = (Exception)info.get("exception");
			if ( e != null ) { info.put("exception", e.getMessage()); }
			content = JSONValue.toJSONString(info);
			contentType = "application/json";
		}
		else if ( format.equals("html") )
		{
			content = toHTML(info);
			contentType = "text/html";
		}
		else if ( format.equals("properties") )
		{
			content = toProperties(info);
			contentType = "text/plain";
		}
		else if ( format.equals("xml") )
		{
			content = toXMLString(info);
			contentType = "application/xml; charset=utf-8";
		}
		output( statusCode, content, contentType, res );
	}

	protected void output( int status, String content, String contentType,
		HttpServletResponse res )
	{
		// output content
		try
		{
			if ( status != 200 )
			{
				res.setStatus( status );
			}
			if ( contentType != null ) { res.setContentType( contentType ); }
			PrintWriter out = new PrintWriter(
				new OutputStreamWriter(res.getOutputStream(), "UTF-8")
			);
			out.print( content );
			out.close();
		}
		catch ( Exception ex )
		{
			log.warn( "Error sending output", ex );
		}
	}
	public static Document toXML( Map m )
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("response");
		doc.setRootElement(root);
		Iterator keys = m.keySet().iterator();
		while ( keys.hasNext() )
		{
			String key = (String)keys.next();
			Object val = m.get(key);
			Element e = root.addElement(key);
			if ( val instanceof String )
			{
				e.setText( val.toString() );
			}
			else if ( val instanceof Collection )
			{
				Collection col = (Collection)val;
				for ( Iterator it = col.iterator(); it.hasNext(); )
				{
					Object o = it.next();
					Element sub = e.addElement("value");
					if ( o instanceof Map )
					{
						Map valmap = (Map)o;
						Iterator fields = valmap.keySet().iterator();
						while( fields.hasNext() )
						{
							String field = (String)fields.next();
							Element sub2 = sub.addElement( field );
							sub2.setText( valmap.get(field).toString() );
						}
					}
					else
					{
						sub.setText( o.toString() );
					}
				}
			}
			else if ( val instanceof Map )
			{
				Map m2 = (Map)val;
				for ( Iterator it = m2.keySet().iterator(); it.hasNext(); )
				{
					String k2 = (String)it.next();
					String v2 = (String)m2.get(k2);
					Element sub = e.addElement("value");
					sub.addAttribute("key",k2);
					sub.setText( v2 );
				}
			}
			else if ( val instanceof Exception )
			{
				Exception ex = (Exception)val;
				e.addElement("p").setText( ex.toString() );
				StackTraceElement[] elem = ex.getStackTrace();
				for ( int i = 0; i < elem.length; i++ ) {
					e.addElement("p").setText(elem[i].toString());
				}
			}
			else
			{
				e.setText( String.valueOf(val) );
			}
		}
		return doc;
	}
	public static String toProperties( Map m )
	{
		Properties props = new Properties();
		Iterator keys = m.keySet().iterator();
		while ( keys.hasNext() )
		{
			String key = (String)keys.next();
			Object val = m.get(key);
			if ( val instanceof Collection )
			{
				Collection col = (Collection)val;
				int i = 0;
				for ( Iterator it = col.iterator(); it.hasNext(); i++ )
				{
					Object o = it.next();
					if ( o instanceof Map )
					{
						Map valmap = (Map)o;
						Iterator fields = valmap.keySet().iterator();
						while( fields.hasNext() )
						{
							String field = (String)fields.next();
							props.put(
								key + "." + i + "." + field,
								valmap.get(field).toString()
							);
						}
					}
					else
					{
						props.put( key + "." + i, o.toString() );
					}
				}
			}
			else if ( val instanceof Map )
			{
				Map m2 = (Map)val;
				for ( Iterator it = m2.keySet().iterator(); it.hasNext(); )
				{
					String k2 = (String)it.next();
					props.put( key + "." + k2, (String)m2.get(k2) );
				}
			}
			else if ( val instanceof Exception )
			{
				Exception ex = (Exception)val;
				props.put( key + ".summary", ex.toString() );
				StackTraceElement[] elem = ex.getStackTrace();
				for ( int i = 0; i < elem.length; i++ ) {
					props.put(key + "." + i, elem[i].toString());
				}
			}
			else
			{
				props.put( key, val.toString() );
			}
		}

		// serialize to a string
		String content = null;
		try
		{
			StringWriter sw = new StringWriter();
			props.store( sw, null );
			content = sw.toString();
		}
		catch ( Exception ex )
		{
			content = "Error serializing properties: " + ex.toString();
		}
			
		return content;
	}
	public static String toXMLString( Map m )
	{
		return toXML(m).asXML();
	}
	public static String toHTML( Map m )
	{
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("html");
		doc.setRootElement(root);
		Element body = root.addElement("body");
		Element table = body.addElement("table");
		Iterator keys = m.keySet().iterator();
		while ( keys.hasNext() )
		{
			String key = (String)keys.next();
			Object val = m.get(key);
			Element row = table.addElement("tr");
			Element keyCell = row.addElement("td");
			keyCell.setText(key);
			Element valCell = row.addElement("td");
			if ( val instanceof String )
			{
				valCell.setText(val.toString());
			}
			else if ( val instanceof Collection )
			{
				Collection col = (Collection)val;
				for ( Iterator it = col.iterator(); it.hasNext(); )
				{
					Element p = valCell.addElement("p");
					Object o = it.next();
					if ( o instanceof Map )
					{
						Map valmap = (Map)o;
						Iterator fields = valmap.keySet().iterator();
						while ( fields.hasNext() )
						{
							String field = (String)fields.next();
							String value = (String)valmap.get(field);
							p.addText(field + ": " + value);
							if ( fields.hasNext() ) { p.addElement("br"); }
						}
					}
					else
					{
						p.setText( o.toString() );
					}
				}
			}
			else if ( val instanceof Map )
			{
				Map m2 = (Map)val;
				for ( Iterator it = m2.keySet().iterator(); it.hasNext(); )
				{
					String k2 = (String)it.next();
					String v2 = (String)m2.get(k2);
					Element div = valCell.addElement("div");
					div.setText(k2 + ": " + v2);
				}
			}
			else if ( val instanceof Exception )
			{
				Exception ex = (Exception)val;
				valCell.addElement("p").setText(ex.toString());
				StackTraceElement[] elem = ex.getStackTrace();
				for ( int i = 0; i < elem.length; i++ ) {
					valCell.addText(elem[i].toString());
					valCell.addElement("br");
				}
			}

		}
		return doc.asXML();
	}

	public String xslt( String xml, String xslName, Map<String,String[]> params,
		String queryString ) throws TransformerException
	{
		// setup the transformer
		String xsl = xslName.startsWith("http") ? xslName : xslBase + xslName;
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer t = tf.newTransformer( new StreamSource(xsl) );
		return xslt( xml, t, params, queryString);
	}
	public String xslt( String xml, Transformer t, Map<String,String[]> params,
		String queryString ) throws TransformerException
	{
		if ( xml == null )
		{
			throw new TransformerException("No input document provided");
		}
		if ( t == null )
		{
			throw new TransformerException("Null transform");
		}

		// params
	    String casGroupTest = getParamString(params,"casGroupTest",null);

		// clear stale parameters
		t.clearParameters();

		// add request params to xsl
		if ( params != null )
		{
			Iterator<String> it = params.keySet().iterator();
			while (it.hasNext() )
			{
				String key = it.next();
				String val = null;
				String[] vals = (String[])params.get(key);
				for ( int i = 0; i < vals.length; i++ )
				{
					if ( vals[i] != null && !vals[i].equals("") )
					{
						if ( val == null )
						{
							val = vals[i];
						}
						else
						{
							val += "; " + vals[i];
						}
					}
				}
				if ( key != null && val != null )
				{
					String escaped = StringEscapeUtils.escapeJava(val);
					t.setParameter( key, escaped );
				}
			}
		}
		if ( queryString != null )
		{
			t.setParameter(
				"queryString",StringEscapeUtils.escapeJava(queryString)
			);
		}
		if(casGroupTest != null)
		{
			t.setParameter("casTest",casGroupTest);
		}
		StringWriter sw = new StringWriter();
		t.transform(
			new StreamSource( new StringReader(xml) ),
			new StreamResult( sw )
		);
		return sw.toString();
	}


	//========================================================================
	// Request parsing, input, etc.
	//========================================================================
	public static boolean isNumber( String s )
	{
		if ( numberPattern == null )
		{
			numberPattern = Pattern.compile("\\d+");
		}
		return numberPattern.matcher( s ).matches();
	}
	public static String fileString( String cmpid, String fileid )
	{
		return (cmpid != null) ? "/" + cmpid + "/" + fileid : "/" + fileid;
	}
	private Identifier createPred( String preid )
	{
		if ( preid == null ) { return null; }
		else if ( preid.startsWith("http") )
		{
			return Identifier.publicURI( preid );
		}
		else
		{
			String[] parts = preid.split(":");
			String ns = nsmap.get(parts[0]);
			return Identifier.publicURI( ns + parts[1] );
		}
	}
	protected Identifier createID( String objid, String cmpid, String fileid )
	{
		String id = objid;
		if ( cmpid != null ) { id += "/" + cmpid; }
		if ( fileid != null ) { id += "/" + fileid; }
		if ( !id.startsWith("http") ) { id = idNS + id; }
		return Identifier.publicURI( id );
	}
	public List<String> list( Properties props, String prefix, String suffix )
	{
		List<String> values = new ArrayList<String>();
		for ( Iterator it = props.keySet().iterator(); it.hasNext(); )
		{
			String key = (String)it.next();
			if ( key != null && key.startsWith(prefix) && key.endsWith(suffix) )
			{
				String s = key.substring(
					prefix.length(), key.length()-suffix.length()
				);
				if ( !values.contains(s) ) { values.add(s); }
			}
		}
		return values;
	}
	public String getRole( String ip )
	{
		// return default role if the ip address is not provided
		if ( ip == null || ip.trim().equals("") )
		{
			return roleDefault;
		}

		// get each role and associated ip ranges, checking to see if any match
		Iterator<String> roles = roleMap.keySet().iterator();
		while ( roles.hasNext() )
		{
			String role = roles.next();
			String[] ipranges = roleMap.get( role );
			for ( int i = 0; i < ipranges.length; i++ )
			{
				// trailing . is treated as a wildcard
				if ( ipranges[i].endsWith(".") )
				{
					if ( ip.startsWith(ipranges[i]) )
					{
						return role.replaceAll(".*\\.","");
					}
				}
				// otherwise, require exact match
				else if ( ip.equals(ipranges[i]) )
				{
					return role.replaceAll(".*\\.","");
				}
			}
		}

		// return the default role if nothing matches
		return roleDefault;
	}
	protected File getParamFile( Map<String,String[]> params,
		String key, File defaultFile )
	{
		if ( params == null ) { return defaultFile; }

		File f = null;
		String[] arr = params.get(key);
		if ( arr != null && arr.length > 0 && arr[0] != null
			&& !arr[0].trim().equals("") )
		{
			f = new File( fsStaging, arr[0] );
		}

		// make sure file exists
		if ( f != null && f.isFile() && f.exists() )
		{
			return f;
		}
		else
		{
			return defaultFile;
		}
	}
	/**
	 * Extract technical metadata with Jhove
	 * @param srcFilename
	 * @return
	 * @throws Exception
	 */
	public static Map<String, String> jhoveExtraction(String srcFilename) throws Exception
	{
		/*
		data dictionary:
			property           source
			size				jhove, filestore
			mimeType			jhove
			formatName			jhove
			formatVersion		jhove
			dateCreated			jhove
			quality				jhove
			crc32checksum		jhove
			md5checksum			jhove
			sha1checksum		jhove
			preservationLevel	fixed=full
			objectCategory		fixed=file
			compositionLevel	fixed=0
			sourceFilename		jhove tmp file/initial upload/automatic
			sourcePath			jhove tmp file/initial upload/user-specified
			use                	user-specified/type from mime, role=service?

	*/
		MyJhoveBase jhove = MyJhoveBase.getMyJhoveBase();
		JhoveInfo data = jhove.getJhoveMetaData(srcFilename);
		Map<String, String> props = new HashMap<String, String>();
		props.put("size", String.valueOf(data.getSize()));
		props.put("mimeType", data.getMIMEtype());
		props.put("formatName", data.getFormat());
		props.put("formatVersion", data.getVersion());
		props.put("dateCreated", dateFormat.format(data.getDateModified()));
		props.put("quality", data.getQuality());
		props.put("duration", data.getDuration());
		props.put("sourceFileName", data.getLocalFileName());
		props.put("sourcePath", data.getFilePath());
		props.put("crc32checksum", data.getCheckSum_CRC32());
		props.put("md5checksum", data.getChecksum_MD5());
		props.put("sha1checksum", data.getChecksum_SHA());
		props.put("status", data.getStatus());
		return props;
	}

	/**
	 * Default file use
	 * @param filename
	 */
	public String getFileUse( String filename )
	{
		String use = null;

		// check for generated derivatives
		if ( filename.endsWith(derivativesExt) )
		{
			String fid = filename.substring(0,filename.indexOf(derivativesExt) );
			use = props.getProperty("derivatives." + fid + ".use");
		}

		if ( use == null )
		{
			// check in fsUseMap
			String ext = filename.substring(filename.lastIndexOf(".")+1);
			use = fsUseMap.get(ext);
		}

		if ( use == null )
		{
			// fallback on mime type
			MimetypesFileTypeMap mimeTypes = new MimetypesFileTypeMap();
			String mimeType = mimeTypes.getContentType(filename);
			String format = mimeType.substring(0, mimeType.indexOf('/'));
			if ( format.equals("application") ) { format = "data"; }
			if(!filename.startsWith("1.") && filename.endsWith(derivativesExt))
			{
				// Derivative type
				use = format + "-thumbnail";
			}
			else
			{
				use = format + "-service";
			}
		}
		return use;
	}

	public static String getDefaultCompositionLevel (String srcFileName)
	{
		String compositionLevel = "0";
		if ( srcFileName != null )
		{
			if (srcFileName.endsWith(".tar.gz") || srcFileName.endsWith(".tgz"))
			{
				compositionLevel = "2";
			}
			else if (srcFileName.endsWith(".gz") || srcFileName.endsWith(".tar")
				|| srcFileName.endsWith(".zip"))
			{
				compositionLevel = "1";
			}
		}
		return compositionLevel;
	}

	protected static int getPropInt( Properties props, String key,
		int defaultValue )
	{
		if ( props == null ) { return defaultValue; }

		int value = defaultValue;
		String val = props.getProperty(key);
		try
		{
			int i = Integer.parseInt(val);
			value = i;
		}
		catch ( Exception ex )
		{
			log.debug("Error parsing integer property: " + ex.toString());
			value = defaultValue;
		}
		return value;
	}
	protected static long getPropLong( Properties props, String key,
		long defaultValue )
	{
		if ( props == null ) { return defaultValue; }

		long value = defaultValue;
		String val = props.getProperty(key);
		try
		{
			long l = Long.parseLong(val);
			value = l;
		}
		catch ( Exception ex )
		{
			log.debug("Error parsing long property: " + ex.toString());
			value = defaultValue;
		}
		return value;
	}
	protected static String[] getParamArray( Map<String,String[]> params,
		String key, String[] defaultValues )
	{
		if ( params == null ) { return defaultValues; }

		String[] values = null;

		if ( params != null )
		{
			String[] arr = params.get(key);
			if ( arr != null && arr.length > 0 )
			{
				return arr;
			}
		}

		return defaultValues;
	}
	protected static String getParamString( Map params, String key,
		String defaultValue )
	{
		String value = null;
		if ( params == null )
		{
			return defaultValue;
		}
		else
		{
			Object o = params.get(key);
			if ( o != null && o instanceof String[] )
			{
				String[] arr = (String[])o;
				if ( arr != null && arr.length > 0 && arr[0] != null
					&& !arr[0].trim().equals("") )
				{
					return arr[0];
				}
			}
			else if ( o != null )
			{
				value = o.toString();
			}
		}

		if ( value != null )
		{
			return value;
		}
		else
		{
			return defaultValue;
		}
	}
	protected static String getParamString( HttpServletRequest req, String key,
		String defaultValue )
	{
		return getParamString(req.getParameterMap(),key,defaultValue);
	}
	protected static boolean getParamBool( Map params, String key,
		boolean defaultValue )
	{
		if ( params == null ) { return defaultValue; }

		String value = getParamString(params,key,null);
		if ( value == null || value.trim().equals("") )
		{
			return defaultValue;
		}
		else
		{
			return value.trim().equalsIgnoreCase("true");
		}
	}
	protected static boolean getParamBool( HttpServletRequest req, String key,
		boolean defaultValue )
	{
		return getParamBool(req.getParameterMap(),key,defaultValue);
	}
	protected static int getParamInt( HttpServletRequest req, String key,
		int defaultValue )
	{
		return getParamInt( req.getParameterMap(), key, defaultValue );
	}
	protected static int getParamInt( Map params, String key, int defaultValue )
	{
		if ( params == null ) { return defaultValue; }

		String s = getParamString(params,key,null);
		int value = defaultValue;

		if ( s != null )
		{
			try
			{
				int i = Integer.parseInt(s);
				value = i;
			}
			catch ( Exception ex )
			{
				log.debug("Error parsing integer parameter: " + ex.toString());
				value = defaultValue;
			}
		}
		return value;
	}
	protected static String[] path( HttpServletRequest req )
	{
		String pathstr = req.getPathInfo();
		log.info(
			req.getMethod() + " " + req.getContextPath()
			+ req.getServletPath() + pathstr
		);
		if ( pathstr == null ) { return new String[]{}; }
		else { return pathstr.split("/"); }
	}
	protected InputBundle input( HttpServletRequest req )
		throws IOException, FileUploadException
	{
		return input( req, null, null, null );
	}
	protected InputBundle input( HttpServletRequest req, String objid,
		String cmpid, String fileid )
		throws IOException, FileUploadException
	{
		log.info( req.getMethod() + " " + req.getRequestURL() );
		InputBundle input = null;
		if ( ServletFileUpload.isMultipartContent(req) || (req.getContentType() != null && req.getContentType().startsWith("multipart/form-data")) )
		{
			// process multipart uploads
			input = multipartInput(req, objid, cmpid, fileid);
		}
		else if ( req.getContentLength() > 0 )
		{
			// if there is a POST/PUT body, then use it
			InputStream in = req.getInputStream();
			input = new InputBundle(req.getParameterMap(), in, fedoraDebug);
		}
		else
		{
			// check for locally-staged file or source filestore reference
			Map<String,String[]> params = req.getParameterMap();
			InputStream in = alternateStream( params, objid, cmpid, fileid );
			input = new InputBundle( params, in );
		}
		return input;
	}

	/**
	 * Look for file sources other than HttpRequest, such as locally-staged
	 * files and FileStore source references.
	**/
	private InputStream alternateStream( Map<String,String[]> params,
		String objid, String cmpid, String fileid ) throws IOException
	{
		File f = getParamFile(params,"local",null);
		InputStream in = null;
		if ( f != null )
		{
			in = new FileInputStream(f);
		}
		else
		{
			String srcName = getParamString(params,"srcfs",null);
			if ( srcName != null )
			{
				try
				{
					FileStore srcfs = FileStoreUtil.getFileStore(props,srcName);
					log.info("Loading from " + srcfs + ": " + objid + "/" + cmpid + "/" + fileid);
					if ( srcfs != null && srcfs.exists(objid, cmpid, fileid) )
					{
						in = srcfs.getInputStream( objid, cmpid, fileid );
					}
				}
				catch ( Exception ex )
				{
					throw new IOException(
						"Error trying to load file from alternate FileStore", ex
					);
				}
			}
		}
		return in;
	}
	private InputBundle multipartInput( HttpServletRequest req, String objid,
		String cmpid, String fileid )
		throws IOException, SizeLimitExceededException
	{
		// process parts
		Map<String,String[]> params = new HashMap<String,String[]>();
		params.putAll( req.getParameterMap() );
		InputStream in = null;
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload( factory );
		if ( maxUploadSize != -1L ) { upload.setSizeMax( maxUploadSize ); }
		List items = null;
		try
		{
			items = upload.parseRequest( req );
		}
		catch ( SizeLimitExceededException ex )
		{
			throw ex;
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		for ( int i = 0; items != null && i < items.size(); i++ )
		{
			FileItem item = (FileItem)items.get(i);
			// form fields go in parameter map
			if ( item.isFormField() )
			{
				params.put(
					item.getFieldName(), new String[]{item.getString()}
				);
				log.debug(
					"Parameter: " + item.getFieldName()
						+ " = " + item.getString()
				);
			}
			// file gets opened as an input stream
			else
			{
				in = item.getInputStream();
				log.debug(
					"File: " + item.getFieldName() + ", " + item.getName()
				);
				params.put(
					"sourceFileName", new String[]{item.getName()}
				);
			}
		}

		// if no file upload found, check for locally-staged file
		if ( in == null )
		{
			in = alternateStream( params, objid, cmpid, fileid );
		}
		return new InputBundle( params, in );
	}
	/**
	 * List embargoed objects a collection/unit
	 */
	private List<Map<String,String>> getEmbargoedList(String pred, String gid, TripleStore ts) throws TripleStoreException{
		Identifier pre = createPred( pred );
		String sparql = "select ?oid ?endDate where {?oid <" + pre.getId() + "> <" + idNS + gid + ">" +
				" . ?oid <" + prNS + "license> ?Lisense . ?Lisense <" + prNS + "licenseNote> '\"embargo\"'" +
				" . ?Lisense <" + prNS + "restriction> ?Restriction . ?Restriction <" + prNS + "endDate> ?endDate}";
		BindingIterator embargoeds = null;
		try
		{
			embargoeds = ts.sparqlSelect(sparql);
			List<Map<String,String>> embargoedList = bindings(embargoeds);
			return embargoedList;
		}
		finally
		{
			if(embargoeds != null )
				embargoeds.close();
		}
	}
	private Set<String> loadSet( ServletContext context, String resourcePath )
	{
		Set<String> set = new HashSet<>();
		try
		{
			InputStream in = context.getResourceAsStream(resourcePath);
			BufferedReader buf = new BufferedReader( new InputStreamReader(in) );
			for ( String line = null; (line = buf.readLine()) != null; )
			{
				set.add( line );
			}
			return set;
		}
		catch ( Exception ex )
		{
			log.warn("Error loading set from " + resourcePath, ex);
			return null;
		}
	}
}
class InputBundle
{
	Map<String,String[]> params;
	InputStream in;
	InputBundle( Map<String,String[]> params, InputStream in )
	{
		this( params, in, false );
	}
	InputBundle( Map<String,String[]> params, InputStream in, boolean debug )
	{
		this.params = params;
		if ( debug )
		{
			try
			{
				StringBuffer buf = new StringBuffer();
				for ( int i = -1; (i=in.read()) != -1; )
				{
					buf.append( (char)i );
				}
				in.close();
				System.out.println("raw input: " + buf.toString() );
				this.in = new ByteArrayInputStream( buf.toString().getBytes() );
			}
			catch ( Exception ex ) { ex.printStackTrace(); }
		}
		else
		{
			this.in = in;
		}
	}
	Map<String,String[]> getParams() { return params; }
	InputStream getInputStream() { return in; }
}
