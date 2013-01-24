package edu.ucsd.library.dams.api;

// java core api
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
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

// post/put file attachments
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
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

// json
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

// dams
import edu.ucsd.library.dams.file.Checksum;
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;
import edu.ucsd.library.dams.file.ImageMagick;
import edu.ucsd.library.dams.file.impl.LocalStore;
import edu.ucsd.library.dams.jhove.JhoveInfo;
import edu.ucsd.library.dams.jhove.MyJhoveBase;
import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.model.Event;
import edu.ucsd.library.dams.solr.SolrFormat;
import edu.ucsd.library.dams.solr.SolrHelper;
import edu.ucsd.library.dams.solr.SolrIndexer;
import edu.ucsd.library.dams.triple.ArkTranslator;
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
import edu.ucsd.library.dams.triple.edit.Edit;
import edu.ucsd.library.dams.util.HttpUtil;
import edu.ucsd.library.dams.util.LDAPUtil;

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
	protected String tsDefault;	// TripleStore to be used when not specified
	protected String tsEvents;	// TripleStore to be used for events

	// identifiers and namespaces
	protected String minterDefault;	      // ID series when not specified
	private Map<String,String> idMinters; // ID series name=>url map
	private Map<String,String> nsmap;     // URI/name to URI map
	protected String idNS;                // Prefix for unqualified identifiers
	protected String prNS;                // Prefix for unqualified predicates
	protected String rdfNS;               // Prefix for RDF predicates

	// uploads
	private int uploadCount = 0; // current number of uploads being processed
	private int maxUploadCount;  // number of concurrent uploads allowed
	private long maxUploadSize;  // largest allowed upload size
	private String backupDir;    // directory to store temporary edit backups

	// solr
	private String solrBase;		// base URL for solr webapp
	private String xslBase;		    // base dir for server-side XSL stylesheets
	private String encodingDefault; // default character encoding
	private String mimeDefault;     // default output mime type
	private File solrXslFile;       // default solr xsl stylesheet

	// ip address mapping
	protected String roleDefault;         // default role if not matching
	protected String roleAdmin;           // special role for administrators
	private Map<String,String[]> roleMap; // map of roles to IP addresses

	// fedora compat
	protected String fedoraObjectDS;  // datastream id that maps to object RDF
	protected String fedoraRightsDS;  // datastream id that maps to rights
	protected String fedoraLinksDS;   // datastream id that maps to links
	protected String fedoraSystemDS;  // datastream id that maps to system info
	protected String sampleObject;    // sample object for fedora demo
	protected String adminEmail;      // email address of system admin
	protected String fedoraCompat;    // fedora version emulated

	// derivatives creation
	private Map<String, String> derivativesMap; // derivatives map
	private String magickCommand; 				// ImageMagick command
	private long jhoveMaxSize;                  // local file cache size limit
	
	// number detection
	private static Pattern numberPattern = null;
	private static SimpleDateFormat dateFormat = new SimpleDateFormat(
		"yyyy-MM-dd'T'hh:mm:ssZ"
	);

	// ldap for group lookup
	private LDAPUtil ldaputil;

	// activemq for solrizer
	private String queueUrl;
	private String queueName;
	private ActiveMQConnectionFactory queueConnectionFactory;
	private Connection queueConnection;
	private Session queueSession;
	MessageProducer queueProducer;

	// initialize servlet parameters
	public void init( ServletConfig config ) throws ServletException
	{
		config();
		ServletContext ctx = config.getServletContext();
		appVersion     = ctx.getInitParameter("app-version");
		srcVersion     = ctx.getInitParameter("src-version");
		buildTimestamp = ctx.getInitParameter("build-timestamp");
		super.init(config);
	}
	private String config()
	{
		String error = null;
		try
		{
			InitialContext ctx = new InitialContext();
			damsHome = (String)ctx.lookup("java:comp/env/dams/home");
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
					minterNames[0], props.getProperty("minters."+minterNames[0])
				);
			}
			nsmap = TripleStoreUtil.namespaceMap(props);
			idNS = nsmap.get("damsid");
			prNS = nsmap.get("dams");
			rdfNS = nsmap.get("rdf");

			// solr
			solrBase = props.getProperty("solr.base");
			mimeDefault = props.getProperty("solr.mimeDefault");
			encodingDefault = props.getProperty("solr.encoding");
			xslBase = props.getProperty("solr.xslDir");
			solrXslFile = new File( xslBase, "solrindexer.xsl" );

			// access control/filters
			roleDefault = props.getProperty("role.default");
			roleAdmin = props.getProperty("role.admin");
			String roleList = props.getProperty("role.list");
			String[] roles = roleList.split(",");
			roleMap = new HashMap<String,String[]>();
			for ( int i = 0; i < roles.length; i++ )
			{
				String ipList = props.getProperty(
					"role." + roles[i] + ".iplist"
				);
				String[] ipArray = ipList.split(",");
				roleMap.put( roles[i], ipArray );
			}

			// triplestores
			tsDefault = props.getProperty("ts.default");
			tsEvents  = props.getProperty("ts.events");

			// files
			fsDefault = props.getProperty("fs.default");
			fsStaging = props.getProperty( "fs.staging" );
			maxUploadCount = getPropInt(props, "fs.maxUploadCount", -1 );
			maxUploadSize  = getPropLong(props, "fs.maxUploadSize", -1L );

			// fedora compat
			fedoraObjectDS = props.getProperty("fedora.objectDS");
			fedoraRightsDS = props.getProperty("fedora.rightsDS");
			fedoraLinksDS  = props.getProperty("fedora.linksDS");
			fedoraSystemDS  = props.getProperty("fedora.systemDS");
			sampleObject = props.getProperty("fedora.samplePID");
			adminEmail = props.getProperty("fedora.adminEmail");
			fedoraCompat = props.getProperty("fedora.compatVersion");
			
			// derivative list
			String derList = props.getProperty("derivatives.list");
			derivativesMap = new HashMap<String, String>();
			if(derList != null)
			{
				String[] derivatives = derList.split(",");
				for ( int i=0; i<derivatives.length; i++ )
				{
					String[] pair = derivatives[i].split(":");
					derivativesMap.put(pair[0].trim(), pair[1].trim());
				}
			}
			
			// ImageMagick convert command
			magickCommand = props.getProperty("magick.convert");
			if ( magickCommand == null )
				magickCommand = "convert";
					
			// Jhove configuration
			String jhoveConf = props.getProperty("jhove.conf");
			if ( jhoveConf != null )
				MyJhoveBase.setJhoveConfig("jhove.conf");
			jhoveMaxSize = getPropLong( props, "jhove.maxSize", -1L );

			// ldap for group lookup
			ldaputil = new LDAPUtil( props );

			// queue
			queueUrl = props.getProperty("queue.url");
			queueName = props.getProperty("queue.name");
			if ( queueUrl != null )
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
			}
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
		Map info = null;
		boolean outputRequired = true; // set to false to suppress status output
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;

		try
		{
			// parse request URI
			String[] path = path( req );
	
			// GET /index
			if ( path.length == 2 && path[1].equals("index") )
			{
				// make sure char encoding is specified
				if ( req.getCharacterEncoding() == null )
				{
					try
					{
						req.setCharacterEncoding( encodingDefault );
						log.debug(
							"Setting character encoding: " + encodingDefault
						);
					}
					catch ( UnsupportedEncodingException ex )
					{
						log.warn("Unable to set chararacter encoding", ex);
					}
				}
				else
				{
					log.debug(
						"Browser specified character encoding: "
							+ req.getCharacterEncoding()
					);
				}

				indexSearch( req.getParameterMap(), req.getPathInfo(), res );
				outputRequired = false;
			}
			// GET /collections
			else if ( path.length == 2 && path[1].equals("collections") )
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
			// GET /repositories
			else if ( path.length == 2 && path[1].equals("repositories") )
			{
				ts = triplestore(req);
				info = repositoryListAll( ts );
			}
			// GET /repositories/bb1234567x
			else if ( path.length == 3 && path[1].equals("repositories") )
			{
				ts = triplestore(req);
				info = repositoryListObjects( path[2], ts );
			}
			// GET /repositories/bb1234567x/count
			else if ( path.length == 4 && path[1].equals("repositories")
				&& path[3].equals("count") )
			{
				ts = triplestore(req);
				info = repositoryCount( path[2], ts );
			}
			// GET /repositories/bb1234567x/embargo
			else if ( path.length == 4 && path[1].equals("repositories")
				&& path[3].equals("embargo") )
			{
				ts = triplestore(req);
				info = repositoryEmbargo( path[2], ts );
			}
			// GET /repositories/bb1234567x/files
			else if ( path.length == 4 && path[1].equals("repositories")
				&& path[3].equals("files") )
			{
				ts = triplestore(req);
				info = repositoryListFiles( path[2], ts );
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
			// GET /objects
			else if ( path.length == 2 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				info = objectsListAll( ts );
			}
			// GET /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				info = objectShow( path[2], ts, null );
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
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				info = objectValidate( path[2], fs, ts, es );
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
				String err = config();
				if ( err == null ) { info = status("Configuration reloaded"); }
				else { info = error( err ); }
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
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("version") )
			{
				info = new LinkedHashMap();
				info.put( "appVersion",     appVersion );
				info.put( "srcVersion",     srcVersion );
				info.put( "buildTimestamp", buildTimestamp );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request" );
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
	}

	/**
	 * HTTP POST methods to create identifiers, objects, datastreams and
	 * relationships.  Calls to POST should be used to create resources.
	**/
	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
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
				InputBundle input = input(req);
				String[] ids = input.getParams().get("id");
				ts = triplestore(req);
				es = events(req);
				info = indexUpdate( ids, ts, es );
			}
			// POST /next_id
			else if ( path.length == 2 && path[1].equals("next_id") )
			{
				String idMinter = getParamString( req, "name", minterDefault );
				int count = getParamInt( req, "count", 1 );
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
					ts = triplestore(req);
					es = events(req);
					info = objectCreate( path[2], in, adds, ts, es );
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file");
				}
			}
			// POST /objects/bb1234567x/transform
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("transform") )
			{
				ts = triplestore(req);
				es = events(req);
				String xsl = getParamString(req,"xsl",null);
				String dest = getParamString(req,"dest",null);
				boolean export = getParamBool(req,"recursive",false);
				if ( dest != null )
				{
					fs = filestore(req);
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
				ts = triplestore(req);
				es = events(req);
				info = indexUpdate( ids, ts, es );
			}
			// POST /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				try
				{
					// make sure request is multipart with a file upload
					if ( !ServletFileUpload.isMultipartContent(req) )
					{
						info = error(
							HttpServletResponse.SC_BAD_REQUEST,
							"Multipart required"
						);
					}
					else
					{
						InputBundle bundle = input( req );
						InputStream in = bundle.getInputStream();
						params = bundle.getParams();
						fs = filestore(req);
						ts = triplestore(req);
						es = events(req);
						info = fileUpload(
							path[2], null, path[3], false, in, fs, ts, es, params
						);
					}
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file");
				}
			}
			// POST /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				try
				{
					// make sure request is multipart with a file upload
					if ( !ServletFileUpload.isMultipartContent(req) )
					{
						info = error(
							HttpServletResponse.SC_BAD_REQUEST,
							"Multipart required"
						);
					}
					else
					{
						InputBundle bundle = input( req );
						InputStream in = bundle.getInputStream();
						params = bundle.getParams();
						fs = filestore(req);
						ts = triplestore(req);
						es = events(req);
						info = fileUpload(
							path[2], path[3], path[4], false, in, fs, ts, es, params
						);
					}
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file");
				}
			}
			// POST /files/bb1234567x/1.tif/characterize
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("characterize") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				InputBundle bundle = input( req );
				params = bundle.getParams();
				info = fileCharacterize( path[2], null, path[3], false, fs, ts, es, params );
			}
			// POST /files/bb1234567x/1/1.tif/characterize
			else if ( path.length == 6 && path[1].equals("files")
				&& path[4].equals("characterize") && isNumber(path[3]) )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				InputBundle bundle = input( req );
				params = bundle.getParams();
				info = fileCharacterize( path[2], path[3], path[4], false, fs, ts, es, params );
			}
			// POST /files/bb1234567x/1.tif/derivatives
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("derivatives") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				
				params = new HashMap<String, String[]>();
				params.put("size", req.getParameterValues("size"));
				params.put("frame", req.getParameterValues("frame"));
				info = fileDerivatives( path[2], null, path[3], false, fs, ts, es, params );
			}
			// POST /files/bb1234567x/1/1.tif/derivatives
			else if ( path.length == 6 && path[1].equals("files")
				&& isNumber(path[3]) && path[5].equals("derivatives") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				
				params = new HashMap<String, String[]>();
				params.put("size", req.getParameterValues("size"));
				params.put("frame", req.getParameterValues("frame"));
				info = fileDerivatives( path[2], path[3], path[4], false, fs, ts, es, params );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request" );
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
	}
	/**
	 * HTTP PUT methods to modify objects and datastreams.  Calls to PUT should
	 * be used to modify existing resources.
	**/
	public void doPut( HttpServletRequest req, HttpServletResponse res )
	{
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
					info = objectUpdate(
						path[2], in, mode, adds, updates, deletes, ts, es
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating object", ex );
					info = error( "Error updating object" );
				}
			}
			// PUT /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				try
				{
					InputBundle bundle = input( req );
					InputStream in = bundle.getInputStream();
					params = bundle.getParams();
					fs = filestore(req);
					ts = triplestore(req);
					es = events(req);
					info = fileUpload(
						path[2], null, path[3], true, in, fs, ts, es, params
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating file", ex );
					info = error( "Error updating file" );
				}
			}
			// PUT /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				try
				{
					InputBundle bundle = input( req );
					InputStream in = bundle.getInputStream();
					params = bundle.getParams();
					fs = filestore(req);
					ts = triplestore(req);
					es = events(req);
					info = fileUpload(
						path[2], path[3], path[4], true, in, fs, ts, es,
						params
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating file", ex );
					info = error( "Error updating file" );
				}
			}
			// PUT /files/bb1234567x/1.tif/characterize
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("characterize") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				InputBundle bundle = input( req );
				params = bundle.getParams();
				info = fileCharacterize( path[2], null, path[3], true, fs, ts, es, params );
			}
			// PUT /files/bb1234567x/1/1.tif/characterize
			else if ( path.length == 6 && path[1].equals("files")
				&& path[5].equals("characterize") && isNumber(path[3]) )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				InputBundle bundle = input( req );
				params = bundle.getParams();
				info = fileCharacterize( path[2], path[3], path[4], true, fs, ts, es, params );
			}
			// PUT /files/bb1234567x/1.tif/derivatives
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("derivatives") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				
				params = new HashMap<String, String[]>();
				params.put("size", req.getParameterValues("size"));
				params.put("frame", req.getParameterValues("frame"));
				info = fileDerivatives( path[2], null, path[3], true, fs, ts, es, params );
			}
			// PUT /files/bb1234567x/1/1.tif/derivatives
			else if ( path.length == 6 && path[1].equals("files")
				&& isNumber(path[3]) && path[5].equals("derivatives") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				
				params = new HashMap<String, String[]>();
				params.put("size", req.getParameterValues("size"));
				params.put("frame", req.getParameterValues("frame"));
				info = fileDerivatives( path[2], path[3], path[4], true, fs, ts, es, params );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request" );
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
	}

	/**
	 * HTTP DELETE methods to delete objects, datastreams and relationships.
	 * Calls to DELETE should be used to delete resources.
	**/
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
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
				String tsName = getParamString(req,"ts",tsDefault);
				info = indexDelete( ids, tsName );
			}
			// DELETE /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				es = events(req);
				info = objectDelete( path[2], ts, es );
			}
			// DELETE /objects/bb1234567x/index
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("index") )
			{
				String[] ids = new String[]{ path[2] };
				String tsName = getParamString(req,"ts",tsDefault);
				info = indexDelete( ids, tsName );
			}
			// DELETE /objects/bb1234567x/selective
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("selective") )
			{
				String[] predicates = req.getParameterValues("predicate");
				ts = triplestore(req);
				es = events(req);
				info = selectiveDelete( path[2], null, predicates, ts, es );
			}
			// DELETE /objects/bb1234567x/1/selective
			else if ( path.length == 5 && path[1].equals("objects")
				&& isNumber(path[3]) && path[4].equals("selective") )
			{
				String[] predicates = req.getParameterValues("predicate");
				ts = triplestore(req);
				es = events(req);
				info = selectiveDelete( path[2], path[3], predicates, ts, es );
			}
			// DELETE /files/bb1234567x/1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				info = fileDelete( path[2], null, path[3], fs, ts, es );
			}
			// DELETE /files/bb1234567x/1/1.tif
			else if ( path.length == 5 && path[1].equals("files")
				&& isNumber(path[3]) )
			{
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				info = fileDelete( path[2], path[3], path[4], fs, ts, es );
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request" );
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
	}

	protected FileStore filestore( HttpServletRequest req ) throws Exception
	{
		String fsName = getParamString(req,"fs",fsDefault);
		return FileStoreUtil.getFileStore(props,fsName);
	}
	protected TripleStore triplestore( HttpServletRequest req ) throws Exception
	{
		String tsName = getParamString(req,"ts",tsDefault);
		return TripleStoreUtil.getTripleStore(props,tsName);
	}
	protected TripleStore events( HttpServletRequest req ) throws Exception
	{
		String tsName = getParamString(req,"es",tsEvents);
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
			catch ( Exception ex )
			{
				log.warn( "Error looking up groups", ex );
			}
		}

		String role = getRole( ip ); 
		info.put( "role", role );

		return info;
	}
	public Map collectionCount( String colid, TripleStore ts )
	{
		return count( "dams:collection", colid, ts );
	}
	public Map repositoryCount( String repid, TripleStore ts )
	{
		return count( "dams:repository", repid, ts );
	}
	protected Map count( String pred, String obj, TripleStore ts )
	{
		try
		{
			Identifier objid = createID( obj, null, null );
			if ( !ts.exists(objid) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND,
					pred + " does not exist"
				);
			}

			Identifier pre = createPred( pred );
			String sparql = "select ?id where { ?id <" + pre.getId() + "> "
				+ "<" + objid.getId() + "> }";

			long count = ts.sparqlCount( sparql );
			Map info = new LinkedHashMap();
			info.put("count",count);
			return info;
		}
		catch ( Exception ex )
		{
			String msg = "Error counting " + pred + " members: " + obj;
			log.info(msg, ex );
			return error(msg);
		}
	}
	public Map collectionListFiles( String colid, TripleStore ts )
	{
		return listFiles( "dams:collection", colid, ts );
	}
	public Map repositoryListFiles( String repid, TripleStore ts )
	{
		return listFiles( "dams:repository", repid, ts );
	}
	protected Map listFiles( String pred, String obj, TripleStore ts )
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
			return error(msg);
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
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist"
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
			return error(msg);
		}
	}
	public Map repositoryEmbargo( String colid, TripleStore ts )
	{
		return null; // DAMS_MGR
		// output = metadata: list of objects (??)
	}
	public Map collectionEmbargo( String colid, TripleStore ts )
	{
		return null; // DAMS_MGR
		// output = metadata: list of objects (??)
	}
	public Map repositoryListAll( TripleStore ts )
	{
		try
		{
			String sparql = "select ?repository ?name where { ?repository <" + prNS + "repositoryName> ?name }";
			BindingIterator repos = ts.sparqlSelect(sparql);
			List<Map<String,String>> repoList = bindings(repos);
			Map info = new HashMap();
			info.put( "repositories", repoList );
			return info;
		}
		catch ( Exception ex )
		{
			return error( "Error listing repositories: " + ex.toString() );
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
			return error( "Error listing events: " + ex.toString() );
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
			return error( "Error listing objects: " + ex.toString() );
		}
	}
	public Map collectionListAll( TripleStore ts )
	{
		try
		{
			String sparql = "select ?collection ?title where { ?collection <" + prNS + "title> ?bn . ?bn <" + rdfNS + "value> ?title . ?collection <" + rdfNS + "type> <" + prNS + "Collection> }";
			BindingIterator cols = ts.sparqlSelect(sparql);
			List<Map<String,String>> collections = bindings(cols);
			Map info = new HashMap();
			info.put( "collections", collections );
			return info;
		}
		catch ( Exception ex )
		{
			return error( "Error listing collections: " + ex.toString() );
		}
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
		return listObjects( "dams:collection", colid, ts );
	}
	public Map repositoryListObjects( String repid, TripleStore ts  )
	{
		return listObjects( "dams:repository", repid, ts );
	}
	public Map listObjects( String pred, String obj, TripleStore ts  )
	{
		try
		{
			Identifier id = createID( obj, null, null );
			Identifier pre = createPred( pred );
			if ( !ts.exists(id) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND,
					pred + " does not exist"
				);
			}

			String sparql = "select ?obj where { "
				+ "?obj <" + pre.getId() + "> "
				+ "<" + id.getId() + "> }";
			BindingIterator bind = ts.sparqlSelect(sparql);
			List<Map<String,String>> objects = bindings(bind);

			Map info = new HashMap();
			info.put( "objects", objects );
			return info;
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			return error( "Error listing " + pred + ": " + ex.toString() );
		}
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
					"Object identifier required"
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required"
				);
			}
	
			Identifier oid = createID( objid, null, null );
			Identifier fid = createID( objid, cmpid, fileid );
			Identifier cid = null;
			if ( cmpid != null )
			{
				cid = createID( objid, cmpid, null );
			}
	
			Identifier hasFile = Identifier.publicURI( prNS + "hasFile" );
			Identifier hasComp = Identifier.publicURI( prNS + "hasComponent" );
			Identifier cmpType = Identifier.publicURI( prNS + "Component" );
			Identifier rdfType = Identifier.publicURI( rdfNS + "type" );
	
			if ( ts != null && !overwrite )
			{	
				sit = ts.listStatements(oid, hasFile, fid);
				if(sit.hasNext()){
					// there is no PUT for characterization, what is the use
					// case for tech md redo?  when a new file is uploaded,
					// tech md should already be deleted in fileUpload...
					return error(
						HttpServletResponse.SC_FORBIDDEN,
						"Characterization for file " + fid.getId()
							+ " already exists. Please use PUT instead"
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
					File srcFile = File.createTempFile( "jhovetmp", null );
					long fileSize = fs.length(objid, cmpid, fileid);

					// make sure file is not too large to copy locally...
					if ( jhoveMaxSize != -1L && fileSize > jhoveMaxSize )
					{
						return error(
							HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
							"File is too large to retrieve for local processing"
							+ " (maxSize=" + jhoveMaxSize + "): " + fid.getId()
						);
					}
					else if ( fileSize > srcFile.getFreeSpace() )
					{
						return error(
							HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
							"There is not enough disk space to create a temp"
							+ " file for " + fid.getId()
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
			
			// Output is saved to the triplestore.
			if ( ts != null && es != null )
			{	
				if ( overwrite )
				{
					// delete existing metadata
					fileDeleteMetadata( objid, cmpid, fileid, ts );
				}
				else
				{
					// check for file metadata and complain if it exists
					if ( cmpid != null )
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
							+ " already exists. Please use PUT instead"
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
				if ( cmpid != null )
				{
					// if component record doesn't exist, create stub
					sit = ts.listStatements(oid, hasComp, cid);
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
				
				// Create event when required with es
				if(es != null)
				{
					indexQueue(objid,"modifyObject");
					createEvent(
						ts, es, objid, cmpid, fileid, "Jhove Extraction", true,
						null, m.get("status")
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
			return error( "Error Jhove extraction: " + ex.toString() );
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
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist"
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
			return error(msg);
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
					"Object identifier required"
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required"				);
			}
	
			if ( in == null )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File upload or locally-staged file required"
				);
			}

			// check upload count and abort if at limit
			if ( maxUploadCount != -1 && uploadCount >= maxUploadCount )
			{
				log.info("Upload: refused");
				return error(
					HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"Too many concurrent uploads"
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
					"File already exists, use PUT to overwrite"
				);
			}
			else if ( overwrite && !fs.exists( objid, cmpid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist, use POST to create"
				);
			}
	
			// upload file
			fs.write( objid, cmpid, fileid, in );
			boolean successful = fs.exists(objid,cmpid,fileid)
				&& fs.length(objid,cmpid,fileid) > 0;
			in.close();
	
			String type = (overwrite) ? "file modification" : "file creation";
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
				indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, objid, cmpid, fileid, type, true, null, null
				);

				Map info = status( status, message );

				// delete any existing metadata when replacing files
				if ( overwrite )
				{
					Map delInfo = fileDeleteMetadata(objid, cmpid, fileid, ts);
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
					objid, cmpid, fileid, overwrite, fs, ts, es, params
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
					ts, es, objid, cmpid, fileid, type, false, null,
					"Failed to upload file"
				);

				return error( message );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error uploading file", ex );
			return error( "Error uploading file: " + ex.toString() );
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
					"Object identifier required"
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required"
				);
			}
	
			// make sure the file exists
			if ( !fs.exists( objid, cmpid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist"
				);
			}
	
			// delete the file
			fs.trash( objid, cmpid, fileid );
			boolean successful = !fs.exists(objid,cmpid,fileid);
	
			if ( successful )
			{
				indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, objid, cmpid, fileid, "file deletion", true,
					null, null
				);

				// FILE_META: update file metadata
				fileDeleteMetadata( objid, cmpid, fileid, ts );

				return status( "File deleted successfully" );
			}
			else
			{
				createEvent(
					ts, es, objid, cmpid, fileid, "file deletion", false,
					null, null
				);
				return error(
					"Failed to delete file: " + objid + fileString(cmpid,fileid)
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting file", ex );
			return error( "Error deleting file: " + ex.toString() );
		}
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
					"Object identifier required"
				);
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"File identifier required"
				);
			}
			if ( derivativesMap == null || derivativesMap.size() == 0 )
			{
				return error(
					"Derivative dimensions not configured."
				);
			}
			
			Identifier oid = createID( objid, null, null );
			Identifier fid = createID( objid, cmpid, fileid );
			Identifier hasFile = Identifier.publicURI( prNS + "hasFile" );
			
			String[] sizes = params.get("size");
			if( sizes != null && sizes.length > 0)
				sizes = sizes[0].split(",");
			if ( sizes != null)
			{
				int len = sizes.length;
				for ( int i=0; i<len; i++ )
				{
					derName = sizes[i] = sizes[i].trim();
					if ( !derivativesMap.containsKey( derName ) )
					{
						return error(
								HttpServletResponse.SC_BAD_REQUEST,
								"Unknow derivative name: " + derName
							);
					}
					Identifier dfid = createID( objid, cmpid, derName + ".jpg" );
					if ( !overwrite )
					{
						sit = ts.listStatements(oid, hasFile, dfid);
						if(sit.hasNext())
						{
							return error(
								HttpServletResponse.SC_FORBIDDEN,
								"Derivative " + dfid.getId()
									+ " already exists. Please use PUT instead"
							);
						}
					}
				}
			}
			else
			{
				int i = 0;
				int len = derivativesMap.size();
				sizes = new String[len];
				for ( Iterator it=derivativesMap.keySet().iterator(); it.hasNext(); )
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
			String[] sizewh = null;
			String derid = null;
			for ( int i=0; i<sizes.length; i++ )
			{
				boolean successful = false;
				derName = sizes[i];
				sizewh = derivativesMap.get(derName).split("x");
				derid = derName + ".jpg";

				successful = magick.makeDerivative(fs, objid, cmpid, fileid, derid, Integer.parseInt(sizewh[0]), Integer.parseInt(sizewh[1]), frame );
				if(! successful )
					errorMessage += "Error derivatives creation: " + objid + "/" + fid + "\n";

				String[] uses = {"visual-thumbnail"};
				params.put("use", uses);
				fileCharacterize( objid, cmpid, derid, overwrite, fs, ts, es,  params);
				indexQueue(objid,"modifyObject");
				createEvent( ts, es, objid, cmpid, derid, "Derivatives Creation", true, null, null );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error derivatives creation", ex );
			return error( "Error derivatives creation: " + ex.toString() );
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
			return error ( errorMessage );
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
					HttpServletResponse.SC_NOT_FOUND, "File does not exist"
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error checking file existence", ex );
			return error( "Error processing request: " + ex.toString() );
		}
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
			// XXX: uses same core checksuming code as JHove, do we need this
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
					ts, es, objid, cmpid, fileid, "fixity check--content",
					success, detail, null
				);
			}
		}
		catch ( Exception ex )
		{
			log.error( "Error comparing checksums", ex );
			return error( "Error comparing checksums: " + ex.toString() );
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
			return error("Unknown id minter: " + name);
		}
		minterURL += count;

		try
		{
			// generate id and check output
			String result = HttpUtil.get(minterURL);
			if ( result == null || result.trim().equals("") )
			{
				return error("Failed to generate id");
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
			return error( "Error generating id" );
		}
	}
	public Map objectCreate( String objid, InputStream in, String adds,
		TripleStore ts, TripleStore es )
	{
		return objectEdit( objid, true, in, null, adds, null, null, ts, es );
	}
	public Map objectUpdate( String objid, InputStream in, String mode,
		String adds, String updates, String deletes, TripleStore ts,
		TripleStore es )
	{
		return objectEdit(
			objid, false, in, mode, adds, updates, deletes, ts, es
		);
	}
	private InputStream debugInputStream( InputStream in )
		throws IOException
	{
		if ( in == null ) { return in; }
		StringBuffer buf = new StringBuffer();
		for ( int i = 0; (i=in.read()) != -1; )
		{
			char c = (char)i;
			buf.append( c );
		}
		String xml = buf.toString();
		System.out.println("xml: " + xml);
		return new ByteArrayInputStream( xml.getBytes() );
	}
		
	protected Map objectEdit( String objid, boolean create, InputStream in,
		String mode, String adds, String updates, String deletes,
		TripleStore ts, TripleStore es )
	{
		try
		{
			in = debugInputStream(in);

			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST, "No subject provided"
				);
			}

	   		// make sure appropriate method is being used to create/update
			Identifier id = createID( objid, null, null );
			if ( create && ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_FORBIDDEN,
			   		"Object already exists, use PUT to update"
		   		);
			}
			else if ( !create && !ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_FORBIDDEN,
			   		"Object does not exist, use POST to create"
		  		);
			}

			// process uploaded file if present
			if ( in != null )
			{
				// XXX: impl other modes
				if ( mode == null || mode.equals("") || mode.equals("all")
					|| mode.equals("add") )
				{
					if ( !create && mode != null && mode.equals("all") )
					{
						try
						{
							// delete object
							ts.removeObject(id);
						}
						catch ( Exception ex )
						{
							return error( "Error deleting existing metadata" );
						}
					}

					try
					{
						// ingest RDF/XML from inputstream
						TripleStoreUtil.loadRDFXML( in, ts, idNS );
						
						// success
						int status = -1;
						String message = null;
						String type = create ?
							"object creation" : "object modification";
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
						indexQueue(objid,"modifyObject");
						createEvent(
							ts, es, objid, null, null, type, true, null, null
						);
						return status( status, message );
					}
					catch ( Exception ex )
					{
						log.warn("Error loading metadata", ex );
						return error( "Error loading new metadata" );
					}
				}
				return error( "Unsupported mode: " + mode );
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
					String type = create ?
						"object creation" : "object modification";
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
						indexQueue(objid,"modifyObject");
						createEvent(
							ts, es, objid, null, null, type, true, null, null
						);
						edit.removeBackup();
						return status( status, message );
					}
					else
					{
						// failure
						String msg = edit.getException().toString();
						createEvent(
							ts, es, objid, null, null, type, false,
							null, msg
						);
						return error( msg );
					}
				}
				else
				{
					return error(
						HttpServletResponse.SC_BAD_REQUEST,
						"Object metadata must be supplied as a file upload or in the adds parameter"
					);
				}
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error editing object", ex );
			return error( "Error editing object: " + ex.toString() );
		}
	}
	public Map objectDelete( String objid, TripleStore ts, TripleStore es )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "No subject provided" );
			}

	   		// make sure appropriate method is being used to create/update
			Identifier id = createID( objid, null, null );
			if ( !ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"Object does not exist"
		   		);
			}
			ts.removeObject(id);

			if ( ! ts.exists(id) )
			{
				indexQueue(objid,"purgeObject");
				createEvent( ts, es, objid, null, null, "object deletion", true, null, null );
				return status( "Object deleted successfully" );
			}
			else
			{
				createEvent(
					ts, es, objid, null, null, "object deletion", false,
					null, null
				);
				return error( "Object deletion failed" );
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
			return error( "Error deleting object: " + ex.toString() );
		}
	}
	public Map selectiveDelete( String objid, String cmpid, String[] predicates,
		TripleStore ts, TripleStore es )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST, "No subject provided"
				);
			}

	   		// make sure object exists
			Identifier sub = createID( objid, cmpid, null );
			Identifier id = createID( objid, null, null );
			if ( !ts.exists(id) )
			{
		   		return error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"Object does not exist"
		   		);
			}

			if ( predicates == null || predicates.length == 0 )
			{
		   		return error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"No predicates specified for deletion"
		   		);
			}

			// remove each predicate...
			ArkTranslator trans = new ArkTranslator( ts, nsmap );
			for ( int i = 0; i < predicates.length; i++ )
			{
				Identifier pre = createPred( predicates[i] );
				TripleStoreUtil.recursiveDelete( id, sub, pre, null, ts );
			}

			indexQueue(objid,"modifyObject");
			createEvent(
				ts, es, objid, null, null, "object modification", true,
				null, null
			);
			return status( "Predicate deleted successfully" );
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting predicates", ex );
			try {createEvent(
				ts, es, objid, null, null, "object modification", false,
				null, null
			);} catch ( Exception ex2 ) {}
			return error( "Error deleting predicates: " + ex.toString() );
		}
	}
	public Map indexDelete( String[] ids, String tsName )
	{
		// make sure we have some ids to index
		if ( ids == null || ids.length == 0 )
		{
			return error(
				HttpServletResponse.SC_BAD_REQUEST, "No identifier specified"
			);
		}

		// connect to solr
		SolrHelper solr = new SolrHelper( solrBase );

		int recordsDeleted = 0;
		try
		{
			// delete individual records
			for ( int i = 0; i < ids.length; i++ )
			{
				if ( solr.delete( tsName, ids[i] ) )
				{
					recordsDeleted++;
				}
			}

			// commit changes
			solr.commit( tsName );
			solr.optimize( tsName );

			// report status
			return status( "Solr: deleted " + recordsDeleted + " records" );
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting records", ex );
			return error( "Error deleting records: " + ex.toString() );
		}
	}
	public Map indexUpdate( String[] ids, TripleStore ts, TripleStore es )
	{
		// make sure we have some ids to index
		if ( ids == null || ids.length == 0 )
		{
			return error( HttpServletResponse.SC_BAD_REQUEST, "No identifier specified" );
		}

		try
		{
			// connect to solr
			SolrIndexer indexer = new SolrIndexer( ts, solrBase, nsmap );
			indexer.addXslFile( solrXslFile );

			// index each record
			for ( int i = 0; i < ids.length; i++ )
			{
				indexer.indexSubject( ids[i] );
			}

			// commit changes
			indexer.flush();
			indexer.commit();

			createEvent(
				ts, es, ids[0], null, null, "Bulk Solr Indexing", true,
				null, null
			);

			// output status message
			return status( indexer.summary() );
		}
		catch ( Exception ex )
		{
			log.warn( "Error updating Solr", ex );
			try
			{
				createEvent(
					ts, es, ids[0], null, null, "Bulk Solr Indexing", false,
					null, ex.toString()
				);
			}
			catch ( Exception ex2 ) { log.error("Error creating event",ex2); }
			return error( "Error updating Solr: " + ex.toString() );
		}
	}
	public Map objectShow( String objid, TripleStore ts, TripleStore es )
	{
		// output = metadata: object
		try
		{
			if ( objid == null || objid.equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Object id must be specified"
				);
			}
            if ( !objid.startsWith("http") ) { objid = idNS + objid; }
            Identifier id = Identifier.publicURI(objid);
			if ( !ts.exists(id) )
			{
				return error(
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist"
				);
			}

			DAMSObject obj = new DAMSObject( ts, es, objid, nsmap );
			Map info = new HashMap();
			info.put("obj",obj);
			return info;
		}
		catch ( Exception ex )
		{
			log.warn( "Error showing object", ex );
			return error( "Error processing request: " + ex.toString() );
		}
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
			output( res.SC_OK, content, "text/xml", res );

			// if destid specified, then also save output
			if ( destid != null )
			{
				fs.write( objid, cmpid, destid, content.getBytes() );
				indexQueue(objid,"modifyObject");
				createEvent(
					ts, es, objid, cmpid, fileid, "transformation-metadata",
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
					ts, es, objid, cmpid, fileid, "transformation-metadata",
					false, null, ex.toString()
				);
			}
			catch ( Exception ex2 ) { log.error("Error creating event",ex2); }
			output(
				error("Error transforming metadata"), params, pathInfo, res
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
					HttpServletResponse.SC_NOT_FOUND, "Object does not exist"
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error checking object existence", ex );
			return error( "Error checking object existence: " + ex.toString() );
		}
	}
	public Map objectValidate( String objid, FileStore fs, TripleStore ts,
		TripleStore es )
	{
		return null; // DAMS_MGR
	}
	// XXX: probably don't need this with automatic ARK/URI translation
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
			return error( "Error looking up predicate map" );
		}
	}

	private Map fileDeleteMetadata( String objid, String cmpid, String fileid,
		TripleStore ts ) throws TripleStoreException
	{
		try
		{
			// identifier object for the file
			Identifier parent = createID( objid, null, null );
			Identifier sub = createID( objid, cmpid, null );
			Identifier fileID = createID( objid, cmpid, fileid );
			Identifier hasFile = Identifier.publicURI( prNS + "hasFile" );

			// delete file metadata (n.b. first arg is object identifer, not
			// the subject of the triple, so this works for files attached
			// to components, etc.)
			TripleStoreUtil.recursiveDelete( parent, sub, hasFile, fileID, ts );

			// delete links from object/components
			ts.removeStatements( null, null, fileID );

			return status("File metadata deleted successfully");
		}
		catch ( Exception ex )
		{
			log.error( "Error deleting file metadata", ex );
			return error("Error deleting file metadata: " + ex.toString());
		}
	}

	/**
	 * Send object to solrizer indexing queue.
	 * @param objid Object id
	 * @param type 'purgeObject' for deletes, 'modifyObject' for other
	 *   operations.
	**/
	private void indexQueue( String objid, String type )
	{
		if ( queueSession != null )
		{
			try
			{
				TextMessage msg = queueSession.createTextMessage(
					"DAMS Queue Message: " + objid + " (" + type + ")"
				);
				msg.setStringProperty("pid","damsid:" + objid);
				msg.setStringProperty("methodName",type);
				queueProducer.send(msg);
			}
			catch ( Exception ex )
			{
				log.warn("Error sending event to queue", ex );
			}
		}
	}
	private void createEvent( TripleStore ts, TripleStore es, String objid,
		String cmpid, String fileid, String type, boolean success,
		String detail, String outcomeNote ) throws TripleStoreException
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
	public void indexSearch( Map<String,String[]> params, String pathInfo,
		HttpServletResponse res )
	{
		// load profile
		String profileFilter = null;
		String profile = getParamString(params,"profile",null);
		if ( profile != null )
		{
			try
			{
				profileFilter = props.getProperty( "solr.profile." + profile );
			}
			catch ( Exception ex )
			{
				log.warn(
					"Error looking up profile filter (" + profile + ")", ex
				);
			}
		}

		// check ip and username
		// XXX integrate with clientInfo
/*
		String username = req.getRemoteUser();
		if ( username == null ) { username = "anonymous"; }
		String roleFilter = null;
		if ( username == null || username.equals("") )
		{
			// not logged in, check ip addr role
			String role = getRole( req.getRemoteAddr() );
			try
			{
				roleFilter = props.getProperty( "role." + role + ".filter" );
			}
			catch ( Exception ex )
			{
				log.warn("Error looking up role filter (" + role + ")", ex );
			}
		}
*/

		// reformatting
		String xsl = getParamString(params,"xsl",null);  // XSL takes precedence
		String format = getParamString(params,"format",null); // JSON conversion

		// velocity
		String name = "select";
		String contentType = mimeDefault;
		String v_template = getParamString(params,"v.template", null);
		if ( (v_template != null && !v_template.equals("")) || (profileFilter != null && profileFilter.indexOf("v.template") != -1) )
		{
			name = "velo";
			contentType = "text/html";
		}

		// datasource param
		String ds = getParamString(params,"ts",tsDefault);
		ds = ds.replaceAll(".*\\/","");

		// build URL
		String queryString = null;
		try
		{
			queryString = queryString(params);
		}
		catch ( Exception ex ) { log.error("Unsupported encoding", ex); }
		
		String url = solrBase + "/" + ds + "/" + name + "?" + queryString;
		if ( xsl != null && !xsl.equals("") )
		{
			url += "&wt=xml";
		}
/* ZZZ: auth
		if ( roleFilter != null && !roleFilter.equals("") )
		{
			url += "&fq=" + roleFilter;
		}
*/
		if ( profileFilter != null && !profileFilter.equals("") )
		{
			url += "&fq=" + profileFilter;
		}
		log.info("url: " + url);

		// perform search
		try
		{
			String output = null;
			HttpUtil http = new HttpUtil(url);
			try
			{
				http.exec();
				if ( http.status() == 200 )
				{
					output = http.contentBodyAsString();
				}
			}
			catch ( IOException ex )
			{
				log.info(
					"Parsing error performing Solr search, url: " + url, ex
				);
				if ( ex.getMessage().endsWith("400 Bad Request") )
				{
					log.warn( "Parsing error", ex );
					error(
						"Parsing error: " + ex.toString()
					);
				}
			}
			finally
			{
				if ( http != null )
				{
					http.shutdown();
				}
			}

			// output reformatting
			Exception formatEx = null;
			if ( output != null && xsl != null && !xsl.equals("") )
			{
				try
				{
					output = xslt( output, xsl, null, queryString(params) );
					contentType = "text/xml";
				}
				catch ( Exception ex )
				{
					formatEx = ex;
				}
			}
			else if ( output != null && format != null
				&& format.equals("curator") )
			{
				// reformat json
				try
				{
					output = SolrFormat.jsonFormatText(
						output, ds, null, getParamString(params,"q",null)
					);
					contentType = "application/json";
				}
				catch ( Exception ex )
				{
					formatEx = ex;
				}
			}
			else if ( output != null && format != null
				&& format.equals("grid") )
			{
				// reformat json
				int PAGE_SIZE = 20;
				int rows = getParamInt( params, "rows", PAGE_SIZE );
				int page = getParamInt( params, "page", 1 );
				try
				{
					output = SolrFormat.jsonGridFormat( output, page, rows );
					contentType = "application/json";
				}
				catch ( Exception ex )
				{
					formatEx = ex;
				}
			}

			// check for null xml
			if ( output == null )
			{
				log.info(
					"Processing error performing Solr search, url: " + url,
					formatEx
				);
				String msg = (formatEx != null) ?
					formatEx.toString() : "unknown";
				output(error("Processing error: " + msg), params, pathInfo, res );
				return;
			}

			if ( contentType.indexOf("; charset=") == -1 )
			{
				contentType += "; charset=" + encodingDefault;
			}

			// override content type
			if ( params.get("contentType") != null )
			{
				contentType = getParamString(params,"contentType",null);
			}

			// send output to client
			res.setContentType( contentType );
			PrintWriter out = res.getWriter();
			out.println( output );
			out.flush();
			out.close();
		}
		catch ( Exception ex )
		{
			Map err = error( "Error performing Solr search: " + ex.toString());
			output(err, params, pathInfo, res);
			log.warn( "Error performing Solr search", ex );
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

	//========================================================================
	// Output formatting
	//========================================================================

	protected Map error( String msg )
	{
		return error( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg );
	}
	protected Map error( int errorCode, String msg )
	{
		Map info = new LinkedHashMap();
		info.put( "statusCode", errorCode );
		info.put( "message", msg );
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
				contentType = "text/xml";
			}
			else
			{
				Map err = error(
					HttpServletResponse.SC_BAD_REQUEST,
					"Unsupported format: " + format
				);
				output( err, params, pathInfo, res );
				return;
			}
			output( HttpServletResponse.SC_OK, content, contentType, res );
		}
		catch ( Exception ex )
		{
			log.warn("Error outputting object metadata",ex);
			Map err = error("Error outputting object metadata");
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
			&& !format.equals("xml") )
		{
			// handle invalid format
			info.put( "warning", "Invalid format: '" + format + "'" );
			format = "xml";
		}

		if ( format.equals("json") )
		{
			content = JSONValue.toJSONString(info);
			contentType = "application/json";
		}
		else if ( format.equals("html") )
		{
			content = toHTML(info);
			contentType = "text/html";
		}
		else if ( format.equals("xml") )
		{
			content = toXMLString(info);
			contentType = "text/xml";
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
			PrintWriter out = res.getWriter();
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
			else if ( val instanceof List )
			{
				List list = (List)val;
				for ( int i = 0; i < list.size(); i++ )
				{
					Object o = list.get(i);
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
			else
			{
				e.setText( String.valueOf(val) );
			}
		}
		return doc;
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
			else if ( val instanceof List )
			{
				List list = (List)val;
				for ( int i = 0; i < list.size(); i++ )
				{
					Element p = valCell.addElement("p");
					Object o = list.get(i);
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

		}
		return doc.asXML();
	}
    public String xslt( String xml, String xslName,
        Map<String,String[]> params, String queryString )
		throws TransformerException
    {
		if ( xml == null )
		{
			throw new TransformerException("No input document provided");
		}

        // params
        String casGroupTest = getParamString(params,"casGroupTest",null);

        // setup the transformer
		String xsl = xslName.startsWith("http") ? xslName : xslBase + xslName;
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer( new StreamSource(xsl) );

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
                    t.setParameter( key, StringEscapeUtils.escapeJava(val) );
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
						return role;
					}
				}
				// otherwise, require exact match
				else if ( ip.equals(ipranges[i]) )
				{
					return role;
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
	protected static String getParamString( Map<String,String[]> params,
		String key, String defaultValue )
	{
		if ( params == null ) { return defaultValue; }

		String value = null;
		String[] arr = params.get(key);
		if ( arr != null && arr.length > 0 && arr[0] != null
			&& !arr[0].trim().equals("") )
		{
			return arr[0];
		}
		else
		{
			return defaultValue;
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
			crc32checksum		jhove XXX: do we want other checksum algorithms?
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
	public static String getFileUse( String filename )
	{
		String use = "";
		MimetypesFileTypeMap mimeTypes = new MimetypesFileTypeMap();
		String mimeType = mimeTypes.getContentType(filename);
		if(!filename.startsWith("1.") && filename.endsWith(".jpg"))
		{
			// Derivative type
			use = mimeType.substring(0, mimeType.indexOf('/')) + "-thumbnail";
		}
		else
		{
			use = mimeType.substring(0, mimeType.indexOf('/')) + "-service";
		}
		return use;
	}
	
	public static String getDefaultCompositionLevel (String srcFileName) 
	{
		String compositionLevel = "0";
		if ( srcFileName != null )
		{
			if ( srcFileName.endsWith(".tar.gz") || srcFileName.endsWith(".tgz") )
				compositionLevel = "2";
			else if ( srcFileName.endsWith(".gz") || srcFileName.endsWith(".tar") || srcFileName.endsWith(".zip"))
				compositionLevel = "1";
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
	protected static String getParamString( HttpServletRequest req, String key,
		String defaultValue )
	{
		if ( req == null ) { return defaultValue; }

		String value = req.getParameter( key );
		if ( value == null || value.trim().equals("") )
		{
			return defaultValue;
		}
		else
		{
			return value;
		}
	}
	protected static boolean getParamBool( HttpServletRequest req, String key,
		boolean defaultValue )
	{
		if ( req == null ) { return defaultValue; }

		String value = req.getParameter( key );
		if ( value == null || value.trim().equals("") )
		{
			return defaultValue;
		}
		else
		{
			return value.trim().equalsIgnoreCase("true");
		}
	}
	protected static int getParamInt( HttpServletRequest req, String key,
		int defaultValue )
	{
		if ( req == null ) { return defaultValue; }

		String value = req.getParameter( key );
		if ( value != null && !value.trim().equals("") )
		{
			try
			{
				int i = Integer.parseInt(value);
				return i;
			}
			catch ( Exception ex )
			{
				log.debug("Error parsing integer parameter: " + ex.toString());
			}
		}
		return defaultValue;
	}
	protected static int getParamInt( Map params, String key, int defaultValue )
	{
		if ( params == null ) { return defaultValue; }

		int value = defaultValue;
		String s = null;

		Object o = params.get(key);
		if ( o instanceof String[] )
		{
			String[] arr = (String[])o;
			if ( arr != null && arr.length > 0 && arr[0] != null )
			{
				s = arr[0];
			}
		}
		else if ( o instanceof String )
		{
			s = (String)o;
		}

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
		return value;
	}
	protected static String[] path( HttpServletRequest req )
	{
		String pathstr = req.getPathInfo();
		if ( pathstr == null ) { return new String[]{}; }
		else { return pathstr.split("/"); }
	}
	protected InputBundle input( HttpServletRequest req )
		throws IOException, FileUploadException
	{
		log.info( req.getMethod() + " " + req.getRequestURL() );
		InputBundle input = null;
		if ( ServletFileUpload.isMultipartContent(req) || (req.getContentType() != null && req.getContentType().startsWith("multipart/form-data")) )
		{
			// process multipart uploads
			input = multipartInput(req);
		}
		else if ( req.getContentLength() > 0 )
		{
			// if there is a POST/PUT body, then use it
			InputStream in = req.getInputStream();
			input = new InputBundle( req.getParameterMap(), in );
		}
		else
		{
			// check for locally-staged file
			Map<String,String[]> params = req.getParameterMap();
			input = new InputBundle( params, localFile(params) );
		}
		return input;
	}
	private InputStream localFile( Map<String,String[]> params )
		throws IOException
	{
		File f = getParamFile(params,"local",null);
		InputStream in = null;
		if ( f != null )
		{
			in = new FileInputStream(f);
		}
		return in;
	}
	private InputBundle multipartInput( HttpServletRequest req )
		throws IOException
	{
		// process parts
		Map<String,String[]> params = new HashMap<String,String[]>();
		InputStream in = null;
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload( factory );
		if ( maxUploadSize != -1L ) { upload.setSizeMax( maxUploadSize ); }
		List items = null;
		try
		{
			items = upload.parseRequest( req );
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
				log.info(
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
			in = localFile( params );
		}
		return new InputBundle( params, in );
	}
}
class InputBundle
{
	Map<String,String[]> params;
	InputStream in;
	InputBundle( Map<String,String[]> params, InputStream in )
	{
		this.params = params;
		this.in = in;
	}
	Map<String,String[]> getParams() { return params; }
	InputStream getInputStream() { return in; }
}
