package edu.ucsd.library.dams.api;

// java core api
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import javax.naming.InitialContext;

// servlet api
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// logging
import org.apache.log4j.Logger;

// post/put file attachments
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

// dom4j
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

// json
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

// dams
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;
import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.model.Event;
import edu.ucsd.library.dams.solr.SolrFormat;
import edu.ucsd.library.dams.solr.SolrHelper;
import edu.ucsd.library.dams.solr.SolrIndexer;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
import edu.ucsd.library.dams.triple.edit.Edit;
import edu.ucsd.library.dams.util.HttpUtil;

/**
 * Servlet implementing the DAMS REST API.
 * @author escowles@ucsd.edu
**/
public class DAMSAPIServlet extends HttpServlet
{
	//========================================================================
	// Servlet init and shared state
	//========================================================================

	// logging
	private static Logger log = Logger.getLogger(DAMSAPIServlet.class);

	private Properties props; // config
	private String damsHome;  // config file location

	// default output format
	private String formatDefault; // output format to use when not specified

	// default data stores
	private String fsDefault;	// FileStore to be used when not specified
	private String tsDefault;	// TripleStore to be used when not specified

	// identifiers and namespaces
	private String minterDefault;	// ID series to be used when not specified
	private Map<String,String> idMinters; // ID series name=>url map
	private String idNS;       // Namespace prefix for unqualified identifiers
	private String prNS;       // Namespace prefix for unqualified predicates
	private String owlSameAs;  // Untranslated URI for owl:sameAs
	private String rdfLabel;   // Untranslated URI for rdf:label
	// NSTRANS: idNS=http://library.ucsd.edu/ark:/20775/
	// NSTRANS: prNS=http://library.ucsd.edu/ontology/dams#

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

	// ip address mapping
	private String roleDefault;		   // default role if not matching
	private Map<String,String[]> roleMap; // map of roles to IP addresses

	// initialize servlet parameters
	public void init( ServletConfig config ) throws ServletException
	{
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
			idNS = props.getProperty("ns.identifiers");
			prNS = props.getProperty("ns.predicates");
			owlSameAs = props.getProperty("ns.owlSameAs");
			rdfLabel  = props.getProperty("ns.rdfLabel");

			// solr
			solrBase = props.getProperty("solr.base");
			mimeDefault = props.getProperty("solr.mimeDefault");
			encodingDefault = props.getProperty("solr.encoding");
			xslBase = props.getProperty("solr.xslDir");

			// access control/filters
			roleDefault = props.getProperty("role.default");
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

			// files
			fsDefault = props.getProperty("fs.default");
			String maxCount = props.getProperty( "fs.maxUploadCount" );
			maxUploadCount = Integer.parseInt(maxCount);
			String maxSize = props.getProperty( "fs.maxUploadSize" );
			maxUploadSize = Long.parseLong(maxSize);
		}
		catch ( Exception ex )
		{
			log.error( "Error initializing", ex );
		}

		super.init(config);
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
		// parse request URI
		String[] path = path( req );

		// GET /index
		if ( path.length == 2 && path[1].equals("index") )
		{
			indexSearch( req, res );
		}
		// collection
		else if ( path.length > 1 && path[1].equals("collections") )
		{
			// GET /collections
			if ( path.length == 2 )
			{
				collectionListAll( req, res );
			}
			// GET /collections/bb1234567x
			else if ( path.length == 3 )
			{
				collectionListObjects( path[2], req, res );
			}
			// GET /collections/bb1234567x/count
			else if ( path[3].equals("count") )
			{
				collectionCount( path[2], req, res );
			}
			// GET /collections/bb1234567x/embargo
			else if ( path[3].equals("embargo") )
			{
				collectionEmbargo( path[2], req, res );
			}
		}
		// objects
		else if ( path.length > 1 && path[1].equals("objects") )
		{
			// GET /objects/bb1234567x
			if ( path.length == 3 )
			{
				objectShow( path[2], false, req, res );
			}
			// GET /objects/bb1234567x/exists
			else if ( path.length == 4 && path[3].equals("exists") )
			{
				objectExists( path[2], req, res );
			}
			// GET /objects/bb1234567x/validate
			else if ( path.length == 4 && path[3].equals("validate") )
			{
				objectValidate( path[2], req, res );
			}
		}
		// files
		else if ( path.length > 1 && path[1].equals("files") )
		{
			// GET /files/bb1234567x/1-1.tif
			if ( path.length == 4 )
			{
				fileShow( path[2], path[3], req, res );
			}
			// GET /files/bb1234567x/1-1.tif/exists
			if ( path.length == 5 && path[4].equals("exists") )
			{
				fileExists( path[2], path[3], req, res );
			}
			// GET /files/bb1234567x/1-1.tif/fixity
			else if ( path.length == 5 && path[4].equals("fixity") )
			{
				fileFixity( path[2], path[3], req, res );
			}
		}
		// client
		else if ( path.length == 3 && path[1].equals("client") )
		{
			// GET /client/authorize
			if ( path[2].equals("authorize") )
			{
				clientAuthorize( req, res );
			}
			// GET /client/info
			else if ( path[2].equals("info") )
			{
				clientInfo( req, res );
			}
		}
		// predicates
		else if ( path.length == 2 && path[1].equals("predicates") )
		{
			// GET /predicates
			predicateList( req, res );
		}
		else
		{
			error( res.SC_BAD_REQUEST, "Invalid request", req, res );
		}
	}

	/**
	 * HTTP POST methods to create identifiers, objects, datastreams and
	 * relationships.  Calls to POST should be used to create resources.
	**/
	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		// redirect overloaded PUT/DELETE requests
/*
		String method = req.getParameter("method");
		if ( method != null && method.equalsIgnoreCase("DELETE") )
		{
			doDelete( req, res );
			return;
		}
		else if ( method != null && method.equalsIgnoreCase("PUT") )
		{
			doPut( req, res );
			return;
		}
*/

		// parse request URI
		String[] path = path( req );

		// POST /index
		if ( path.length == 2 && path[1].equals("index") )
		{
			String[] ids = req.getParameterValues("id");
			indexUpdate( ids, req, res );
		}
		// POST /next_id
		else if ( path.length == 2 && path[1].equals("next_id") )
		{
			String idMinter = getParamString( req, "name", minterDefault );
			int count = getParamInt( req, "count", 1 );
			identifierCreate( idMinter, count, req, res );
		}
		// objects
		else if ( path.length > 2 && path[1].equals("objects") )
		{
			// POST /objects/bb1234567x
			if ( path.length == 3 )
			{
				objectCreate( path[2], req, res );
			}
			// POST /objects/bb1234567x/transform
			else if ( path.length == 4 && path[3].equals("transform") )
			{
				objectTransform( path[2], req, res );
			}
			// POST /objects/bb1234567x/index	
			else if ( path.length == 4 && path[3].equals("index") )
			{
				String[] ids = new String[]{ path[2] };
				indexUpdate( ids, req, res );
			}
		}
		// files
		else if ( path.length > 2 && path[1].equals("files") )
		{
			// POST /files/bb1234567x/1-1.tif
			if ( path.length == 4 )
			{
				fileUpload( path[2], path[3], false, req, res );
			}
			// POST /files/bb1234567x/1-1.tif/characterize
			else if ( path.length == 5 && path[4].equals("characterize") )
			{
				fileCharacterize( path[2], path[3], req, res );
			}
			// POST /files/bb1234567x/1-1.tif/derivatives
			else if ( path.length == 5 && path[4].equals("derivatives") )
			{
				fileDerivatives( path[2], path[3], req, res );
			}
		}
		else
		{
			error( res.SC_BAD_REQUEST, "Invalid request", req, res );
		}
	}
	/**
	 * HTTP PUT methods to modify objects and datastreams.  Calls to PUT should
	 * be used to modify existing resources.
	**/
	public void doPut( HttpServletRequest req, HttpServletResponse res )
	{
		// parse request URI
		String[] path = path( req );

		// PUT /objects/bb1234567x
		if ( path.length == 3 )
		{
			objectUpdate( path[2], req, res );
		}
		// PUT /objects/bb1234567x/1-1.tif
		else if ( path.length == 4 )
		{
			fileUpload( path[2], path[3], true, req, res );
		}
		else
		{
			error( res.SC_BAD_REQUEST, "Invalid request", req, res );
		}
	}

	/**
	 * HTTP DELETE methods to delete objects, datastreams and relationships.
	 * Calls to DELETE should be used to delete resources.
	**/
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
		// parse request URI
		String[] path = path( req );

		// DELETE /index
		if ( path.length == 2 && path[1].equals("index") )
		{
			String[] ids = req.getParameterValues("id");
			indexDelete( ids, req, res );
		}
		// DELETE /objects/bb1234567x
		if ( path.length == 3 && path[1].equals("objects") )
		{
			objectDelete( path[2], req, res );
		}
		// DELETE /objects/bb1234567x/index
		else if ( path.length == 4 && path[1].equals("objects")
			&& path[3].equals("index") )
		{
			String[] ids = new String[]{ path[2] };
			indexDelete( ids, req, res );
		}
		// DELETE /files/bb1234567x/1-1.tif
		else if ( path.length == 4 && path[1].equals("files") )
		{
			fileDelete( path[2], path[3], req, res );
		}
		else
		{
			error( res.SC_BAD_REQUEST, "Invalid request", req, res );
		}
	}


	//========================================================================
	// Core Java API
	//========================================================================
	public void clientAuthorize( HttpServletRequest req,
		HttpServletResponse res )
	{
		// output = redirect
		// XXX: should probably not even get here...
	}
	public void clientInfo( HttpServletRequest req, HttpServletResponse res )
	{
		// XXX: AUTH: who is making the request? hydra/isla. or end-user?
		String ip = req.getRemoteAddr();
		String role = getRole( ip ); 
		String user = req.getRemoteUser();
		if ( user == null ) { user = ""; }
		Map info = new LinkedHashMap();
		info.put( "ip", ip );
		info.put( "role", role );
		info.put( "user", user );
		output( res.SC_OK, info, req, res );
	}
	public void collectionCount( String colid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
	}
	public void collectionEmbargo( String colid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of objects (??)
	}
	public void collectionIndexDelete( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
	}
	public void collectionListAll( HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of collection objects
	}
	public void collectionListObjects( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of objects
	}
	public void fileCharacterize( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// if res is null, update triplestore but don't output anything
	}
	public void fileUpload( String objid, String fileid, boolean overwrite,
		 HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		try
		{
			// parse request entity
			InputBundle bundle = input( req );
			Map<String,String> params = bundle.getParams();
			InputStream in = bundle.getInputStream();

			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				error( res.SC_BAD_REQUEST, "Object identifier required", req, res );
				return;
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				error( res.SC_BAD_REQUEST, "File identifier required", req, res );
				return;
			}
	
			// make sure request is multipart with a file upload
			if ( !ServletFileUpload.isMultipartContent(req) )
			{
				error( res.SC_BAD_REQUEST, "Multipart required", req, res );
				return;
			}
			if ( in == null )
			{
				error( res.SC_BAD_REQUEST, "File upload required", req, res );
				return;
			}

			// check upload count and abort if at limit
			if ( uploadCount >= maxUploadCount )
			{
				log.info("Upload: refused");
				error(
					res.SC_SERVICE_UNAVAILABLE, "Too many concurrent uploads",
					req, res
				);
				return;
			}
			else
			{
				uploadCount++;
				log.info("Upload: start: " + uploadCount);
			}
	
			String fsName = getParamString( req, "fs", fsDefault );
			fs = FileStoreUtil.getFileStore( props, fsName );
	
			// make sure appropriate method is being used to create/update
			if ( !overwrite && fs.exists( objid, fileid ) )
			{
				error(
					HttpServletResponse.SC_FORBIDDEN,
					"File already exists, use PUT to overwrite", req, res
				);
				return;
			}
			else if ( overwrite && !fs.exists( objid, fileid ) )
			{
				error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist, use POST to create", req, res
				);
				return;
			}
	
			// upload file
			fs.write( objid, fileid, in );
			boolean successful = fs.exists(objid,fileid)
				&& fs.length(objid,fileid) > 0;
			in.close();
	
			String type = overwrite ? "file modification" : "file creation";
			if ( successful )
			{
				createEvent(
					req, null, objid, fileid, type, true, "EVENT_DETAIL_SPEC", null
				);
				status( "File uploaded successfully", req, res );
			}
			else
			{
				createEvent(
					req, null, objid, fileid, type, false, "EVENT_DETAIL_SPEC", "Failed to upload file XXX"
				);
				error( "Failed to upload file: " + objid + "/" + fileid, req, res );
				// FILE_META: update file metadata
				fileDeleteMetadata( req, objid, fileid );
				fileCharacterize( objid, fileid, req, null );
			}
		}
		catch ( Exception ex )
		{
			error( "Error uploading file: " + ex.toString(), req, res );
			log.warn( "Error uploading file", ex );
		}
		finally
		{
			if ( fs != null )
			{
				try
				{
					fs.close();
				}
				catch ( Exception ex2 )
				{
					log.error("Error closing filestore: " + ex2.toString());
				}
			}
		}
	}
	public void fileDelete( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		try
		{
			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				error( res.SC_BAD_REQUEST, "Object identifier required", req, res );
				return;
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				error( res.SC_BAD_REQUEST, "File identifier required", req, res );
				return;
			}
	
			String fsName = getParamString( req, "fs", fsDefault );
			fs = FileStoreUtil.getFileStore( props, fsName );
	
			// make sure the file exists
			if ( !fs.exists( objid, fileid ) )
			{
				error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist", req, res
				);
				return;
			}
	
			// delete the file
			fs.trash( objid, fileid );
			boolean successful = !fs.exists(objid,fileid);
	
			if ( successful )
			{
				createEvent(
					req, null, objid, fileid, "file deletion", true,
					"Deleting file EVENT_DETAIL_SPEC", null
				);

				// FILE_META: update file metadata
				fileDeleteMetadata( req, objid, fileid );

				status( "File deleted successfully", req, res );
			}
			else
			{
				createEvent( req, null, objid, fileid, "file deletion", false, "Deleting file EVENT_DETAIL_SPEC", "XXX" );
				error( "Failed to delete file: " + objid + "/" + fileid, req, res );
			}
		}
		catch ( Exception ex )
		{
			error( "Error deleting file: " + ex.toString(), req, res );
			log.warn( "Error deleting file", ex );
		}
		finally
		{
			if ( fs != null )
			{
				try
				{
					fs.close();
				}
				catch ( Exception ex2 )
				{
					log.error("Error closing filestore: " + ex2.toString());
				}
			}
		}
	}
	public void fileDerivatives( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
	}
	public void fileExists( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		try
		{
			String fsName = getParamString(req,"fs",fsDefault);
			fs = FileStoreUtil.getFileStore(props,fsName);
			if ( fs.exists( objid, fileid ) )
			{
				status( "File exists", req, res );
			}
			else
			{
				error( res.SC_NOT_FOUND, "File does not exist", req, res );
			}
		}
		catch ( Exception ex )
		{
			error( "Error processing request: " + ex.toString(), req, res );
			log.warn( "Error checking file existence", ex );
		}
		finally
		{
			if ( fs != null )
			{
				try { fs.close(); }
				catch ( Exception ex ){log.info("Error closing filestore", ex);}
			}
		}
	}
	public void fileFixity( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
	}
	public void fileShow( String objid, String fileid, HttpServletRequest req,
		HttpServletResponse res )
	{
		// XXX: access control
		req.setAttribute(
			"edu.ucsd.library.dams.api.DAMSAPIServlet.authorized","true"
		);
		String url = "/file/" + objid + "/" + fileid;
		if ( req.getQueryString() != null && !req.getQueryString().equals("") )
		{
			url += "?" + req.getQueryString();
		}
		try
		{
			//res.sendRedirect( url );
			req.getRequestDispatcher(url).forward( req, res );
		}
		catch ( Exception ex )
		{
			log.error("Error sending redirect: " + ex.toString());
			log.warn( "Error sending redirect", ex );
		}
	}
	public void identifierCreate( String name, int count,
		HttpServletRequest req, HttpServletResponse res )
	{
		// lookup minter URL and add count parameter
		String minterURL = idMinters.get(name);
		if ( minterURL == null )
		{
			error("Unknown id minter: " + name, req, res);
		}
		minterURL += count;

		try
		{
			// generate id and check output
			String result = HttpUtil.get(minterURL);
			if ( result == null || result.trim().equals("") )
			{
				error("Failed to generate id", req, res);
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
				output( res.SC_OK, info, req, res );
			}
		}
		catch ( Exception ex )
		{
			error( "Error generating id", req, res );
		}
	}
	public void indexSearch( HttpServletRequest req, HttpServletResponse res )
	{
		long start = System.currentTimeMillis();

		// make sure char encoding is specified
		if ( req.getCharacterEncoding() == null )
		{
			try
			{
				req.setCharacterEncoding( encodingDefault );
				log.debug("Setting character encoding: " + encodingDefault );
			}
			catch ( UnsupportedEncodingException ex )
			{
				log.warn("Unable to set chararacter encoding", ex);
			}
		}
		else
		{
			log.debug("Browser specified character encoding: " + req.getCharacterEncoding() );
		}

		// load profile
		String profileFilter = null;
		String profile = req.getParameter("profile");
		if ( profile != null )
		{
			try
			{
				profileFilter = props.getProperty( "solr.profile." + profile );
			}
			catch ( Exception ex )
			{
				log.warn("Error looking up profile filter (" + profile + ")", ex );
			}
		}

		// check ip and username
		// XXX: AUTH: who is making the request? hydra/isla. or end-user?
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

		// reformatting
		String xsl = req.getParameter("xsl");       // XSL takes precedence
		String format = req.getParameter("format"); // JSON reformatting

		// velocity
		String name = "select";
		String contentType = mimeDefault;
		String v_template = req.getParameter("v.template");
		if ( (v_template != null && !v_template.equals("")) || (profileFilter != null && profileFilter.indexOf("v.template") != -1) )
		{
			name = "velo";
			contentType = "text/html";
		}

		// datasource param
		String ds = getParamString(req,"ts",tsDefault);
		ds = ds.replaceAll(".*\\/","");

		// build URL
		String url = solrBase + "/" + ds + "/" + name
			+ "?" + req.getQueryString();
		if ( xsl != null && !xsl.equals("") )
		{
			url += "&wt=xml";
		}
		if ( roleFilter != null && !roleFilter.equals("") )
		{
			url += "&fq=" + roleFilter;
		}
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
					error(
						"Parsing error: " + ex.toString(), req, res
					);
					log.warn( "Parsing error", ex );
					return;
				}
			}
			finally
			{
				if ( http != null )
				{
					http.releaseConnection();
				}
			}

			// output reformatting
			Exception formatEx = null;
			if ( output != null && xsl != null && !xsl.equals("") )
			{
				String casGroupTest = null; // XXX: remove???
				if(xsl.indexOf("piclens_rss.xsl") >=0){
					if(req.getParameter("casTest") != null)
						casGroupTest = "casTest";
					else
						casGroupTest = "";
				}
				xsl = xslBase + xsl;
				xsl = xsl.replaceAll("\\.\\.",""); // prevent snooping
				try
				{
					output = SolrFormat.xslt(
						output, xsl, req.getParameterMap(),
						req.getQueryString(), casGroupTest
					);
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
						output, ds, null, req.getParameter("q")
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
				int rows = (req.getParameter("rows") != null) ?
					Integer.parseInt(req.getParameter("rows")) : PAGE_SIZE;
				int page = -1;
				try
				{
					page = Integer.parseInt(req.getParameter("page"));
				}
				catch ( Exception ex ) { page = -1; }
				if(page == -1) { page = 1; }
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
				error( "Processing error: " + msg, req, res );
				return;
			}

			if ( contentType.indexOf("; charset=") == -1 )
			{
				contentType += "; charset=" + encodingDefault;
			}

			// override content type
			if ( req.getParameter("contentType") != null )
			{
				contentType= req.getParameter("contentType");
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
			error( "Error performing Solr search: " + ex.toString(), req, res );
			log.warn( "Error performing Solr search", ex );
		}
		long dur = System.currentTimeMillis() - start;
		log.info("indexSearch: " + dur + "ms, params: " + req.getQueryString());
	}
	public void objectCreate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		objectEdit( objid, true, req, res );
	}
	public void objectUpdate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		objectEdit( objid, false, req, res );
	}
	private void objectEdit( String objid, boolean create, HttpServletRequest req, HttpServletResponse res )
	{
		TripleStore ts = null;
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				error( res.SC_BAD_REQUEST, "No subject provided", req, res );
				return;
			}

			// parse request entity
			InputBundle bundle = input( req );
			Map<String,String> params = bundle.getParams();
			InputStream in = bundle.getInputStream();

			// detect overloaded POST
			String method = params.get("method");
			if ( method != null && method.equalsIgnoreCase("PUT") )
			{
				create = false;
			}

			// connect to triplestore
			String tsName = getParamString(req,"ts",tsDefault);
			ts = TripleStoreUtil.getTripleStore(props,tsName);

	   		// make sure appropriate method is being used to create/update
			if ( !objid.startsWith("http") )
			{
				objid = idNS + objid;
			}
			Identifier id = Identifier.publicURI(objid);
			if ( create && ts.exists(id) )
			{
		   		error(
			   		HttpServletResponse.SC_FORBIDDEN,
			   		"Object already exists, use PUT to update", req, res
		   		);
				return;
			}
			else if ( !create && !ts.exists(id) )
			{
		   		error(
			   		HttpServletResponse.SC_FORBIDDEN,
			   		"Object does not exist, use POST to create", req, res
		  		);
				return;
			}

			// process uploaded file if present
			if ( in != null )
			{
				// DAMS_MGR
			}
			// otherwise, look for JSON adds
			else
			{
				String adds = params.get("adds");
				String updates = null;
				String deletes = null;
				if ( !create )
				{
					updates = params.get("updates");
					deletes = params.get("deletes");
				}
				if ( adds != null && !adds.equals("") )
				{
					// save data to the triplestore
					Edit edit = new Edit(
						backupDir, adds, updates, deletes, objid, ts,
						idNS, prNS, owlSameAs, rdfLabel
					);
					edit.saveBackup();
					String type = create ?
						"object creation" : "object modification";
					if ( edit.update() )
					{
						// success
						createEvent( req, ts, objid, null, type, true, "EVENT_DETAIL_SPEC", null );
						status( "Object saved successfully", req, res );
						edit.removeBackup();
					}
					else
					{
						// failure
						String msg = edit.getException().toString();
						createEvent( req, ts, objid, null, type, false, "EVENT_DETAIL_SPEC", msg );
						error( msg, req, res );
					}
				}
				else
				{
					error( res.SC_BAD_REQUEST, "Object metadata must be supplied as a file upload or in the adds parameter", req, res );
				}
			}
		}
		catch ( Exception ex )
		{
			error( "Error editing object: " + ex.toString(), req, res );
			log.warn( "Error editing object", ex );
		}
		finally
		{
			if ( ts != null )
			{
				try
				{
					ts.close();
				}
				catch ( Exception ex2 )
				{
					log.error("Error closing triplestore: " + ex2.toString());
				}
			}
		}
	}
	public void objectDelete( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		TripleStore ts = null;
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				error( res.SC_BAD_REQUEST, "No subject provided", req, res );
				return;
			}

			// connect to triplestore
			String tsName = getParamString(req,"ts",tsDefault);
			ts = TripleStoreUtil.getTripleStore(props,tsName);

	   		// make sure appropriate method is being used to create/update
			if ( !objid.startsWith("http") )
			{
				objid = idNS + objid;
			}
			Identifier id = Identifier.publicURI(objid);
			if ( !ts.exists(id) )
			{
		   		error(
			   		HttpServletResponse.SC_BAD_REQUEST,
			   		"Object does not exist", req, res
		   		);
				return;
			}
			ts.removeObject(id);

			if ( ! ts.exists(id) )
			{
				createEvent( req, ts, objid, null, "object deletion", true, "Deleting object EVENT_DETAIL_SPEC", null );
				status( "Object deleted successfully", req, res );
			}
			else
			{
				createEvent( req, ts, objid, null, "object deletion", false, "Deleting object EVENT_DETAIL_SPEC", "XXX error" );
				error( "Object deletion failed", req, res );
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
			error( "Error deleting object: " + ex.toString(), req, res );
			log.warn( "Error deleting object", ex );
		}
		finally
		{
			if ( ts != null )
			{
				try
				{
					ts.close();
				}
				catch ( Exception ex2 )
				{
					log.error("Error closing triplestore: " + ex2.toString());
				}
			}
		}
	}
	public void indexDelete( String[] ids, HttpServletRequest req,
		HttpServletResponse res )
	{
		// make sure we have some ids to index
		if ( ids == null || ids.length == 0 )
		{
			error( res.SC_BAD_REQUEST, "No identifier specified", req, res );
		}

		// get ds parameter
		String ds = getParamString(req,"ts", tsDefault);

		// connect to solr
		SolrHelper solr = new SolrHelper( solrBase );

		int recordsDeleted = 0;
		try
		{
			// delete individual records
			for ( int i = 0; i < ids.length; i++ )
			{
				if ( solr.delete( ds, ds, ids[i] ) )
				{
					recordsDeleted++;
				}
			}

			// commit changes
			solr.commit( ds );

			// report status
			status( "Solr: deleted " + recordsDeleted + " records", req, res );
		}
		catch ( Exception ex )
		{
			error( "Error deleting records: " + ex.toString(), req, res );
			log.warn( "Error deleting records", ex );
		}
	}
	public void indexUpdate( String[] ids, HttpServletRequest req,
		HttpServletResponse res )
	{
		// make sure we have some ids to index
		if ( ids == null || ids.length == 0 )
		{
			error( res.SC_BAD_REQUEST, "No identifier specified", req, res );
		}

		try
		{
			// connect to solr
			String tsName = getParamString(req,"ts",tsDefault);
			TripleStore ts = TripleStoreUtil.getTripleStore(props,tsName);
			SolrIndexer indexer = new SolrIndexer(
				ts, solrBase, idNS, prNS, owlSameAs, rdfLabel
			);

			// index each record
			for ( int i = 0; i < ids.length; i++ )
			{
				indexer.indexSubject( ids[i] );
			}

			// commit changes
			indexer.flush();
			indexer.commit();

			// output status message
			status( indexer.summary(), req, res );
		}
		catch ( Exception ex )
		{
			error( "Error updating Solr: " + ex.toString(), req, res );
			log.warn( "Error updating Solr", ex );
		}
	}
	public void objectShow( String objid, boolean export,
		HttpServletRequest req, HttpServletResponse res )
	{
		// output = metadata: object
		TripleStore ts = null;
		try
		{
			String tsName = getParamString(req,"ts",tsDefault);
			ts = TripleStoreUtil.getTripleStore(props,tsName);
			DAMSObject obj = new DAMSObject( ts, objid, idNS, prNS, owlSameAs, rdfLabel );
			String format = getParamString(req,"format",formatDefault);
			String content = null;
			String contentType = null;
			if ( format.equals("nt") )
			{
				content = obj.getNTriples(false);
				contentType = "text/plain";
			}
			else if ( format.equals("xml") )
			{
				content = obj.getRDFXML(false);
				contentType = "text/xml";
			}
			else
			{
				error(
					res.SC_BAD_REQUEST, "Unsupported format: " + format,
					req, res
				);
			}

			output( res.SC_OK, content, contentType, res );
		}
		catch ( Exception ex )
		{
			error( "Error processing request: " + ex.toString(), req, res );
			log.warn( "Error showing object", ex );
		}
		finally
		{
			if ( ts != null )
			{
				try { ts.close(); }
				catch (Exception ex){log.info("Error closing triplestore", ex);}
			}
		}
	}
	public void objectTransform( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: object
	}
	public void objectExists( String objid, HttpServletRequest req,
		HttpServletResponse res )
	{
		TripleStore ts = null;
		try
		{
			String tsName = getParamString(req,"ts",tsDefault);
			ts = TripleStoreUtil.getTripleStore(props,tsName);
			if ( !objid.startsWith("http") ) { objid = idNS + objid; }
			Identifier id = Identifier.publicURI(objid);
			if ( ts.exists( id ) )
			{
				status( "Object exists", req, res );
			}
			else
			{
				error( res.SC_NOT_FOUND, "Object does not exist", req, res );
			}
		}
		catch ( Exception ex )
		{
			error( "Error checking object existence: " + ex.toString(), req, res );
			log.warn( "Error checking object existence", ex );
		}
		finally
		{
			if ( ts != null )
			{
				try { ts.close(); }
				catch (Exception ex){log.info("Error closing triplestore", ex);}
			}
		}
	}
	public void objectValidate( String objid, HttpServletRequest req,
		HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: object
	}
	public void predicateList( HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of predicate URIs
	}

	private void fileDeleteMetadata( HttpServletRequest req, String objid,
		String fileid ) throws TripleStoreException
	{
		TripleStore ts = null;
		try
		{
			// identifier object for the file
			Identifier fileIdentifier = Identifier.publicURI(
				idNS + objid + "/" + fileid
			);

			// connect to triplestore
			String tsName = getParamString(req,"ts",tsDefault);
			ts = TripleStoreUtil.getTripleStore(props,tsName);

			// delete file metadata
			ts.removeStatements( fileIdentifier, null, null ); // XXX: bnodes?

			// delete links from object/components
			ts.removeStatements( null, null, fileIdentifier );
		}
		catch ( Exception ex )
		{
			log.error( "Error deleting file metadata", ex );
		}
		finally
		{
			try { ts.close(); }
			catch ( Exception ex2 )
			{
				log.error("Error closing triplestore: " + ex2.toString());
			}
		}
	}
	private void createEvent( HttpServletRequest req, TripleStore ts,
		String objid, String fileid, String type, boolean success,
		String detail, String outcomeNote ) throws TripleStoreException
	{
		// instantiate triplestore if not already loaded
		boolean closeTS = false;
		if ( ts == null )
		{
			closeTS = true;
			try
			{
				String tsName = getParamString(req,"ts",tsDefault);
				ts = TripleStoreUtil.getTripleStore(props,tsName);
			}
			catch ( Exception ex )
			{
				throw new TripleStoreException("Error loading triplestore", ex);
			}
		}

		try
		{
			// mint ARK for event
			String minterURL = idMinters.get(minterDefault) + "1";
			String eventARK = HttpUtil.get(minterURL);
			eventARK = eventARK.replaceAll(".*/","");
			Identifier eventID = Identifier.publicURI( idNS + eventARK );
	
			// lookup user identifier
			// XXX: AUTH: who is making the request? hydra/isla. or end-user?
			Identifier userID = null; // XXX SolrProxy.lookup( req.getRemoteUser() );

			// predicate translator
			DAMSObject trans = new DAMSObject( ts, objid, idNS, prNS, owlSameAs, rdfLabel );
	
			// create event object and save to the triplestore
			String obj = objid.startsWith("http") ? objid : idNS + objid;
			if ( fileid != null ) { obj += "/" + fileid; }
			Identifier subID = Identifier.publicURI( obj );
			Event e = new Event(
				eventID, subID, userID, success, type,
				detail, outcomeNote, trans
			);
			e.save(ts);
		}
		catch ( IOException ex )
		{
			log.error("Error minting event ARK: " + ex.toString());
			log.warn( "Error minting event ARK", ex );
		}
		finally
		{
			if ( closeTS && ts != null )
			{
				try { ts.close(); }
				catch ( Exception ex2 )
				{
					log.error("Error closing triplestore: " + ex2.toString());
				}
			}
		}
	}

	//========================================================================
	// Output formatting
	//========================================================================

	protected void error( String msg, HttpServletRequest req,
		HttpServletResponse res )
	{
		error( res.SC_INTERNAL_SERVER_ERROR, msg, req, res );
	}
	protected void error( int errorCode, String msg, HttpServletRequest req,
		HttpServletResponse res )
	{
		// output = error message
		Map info = new LinkedHashMap();
		info.put( "message", msg );
		output( errorCode, info, req, res );
	}
	protected void status( String msg, HttpServletRequest req,
		HttpServletResponse res )
	{
		// output = error message
		Map info = new LinkedHashMap();
		info.put( "message", msg );
		output( res.SC_OK, info, req, res );
	}
	protected void output( int statusCode, Map<String,String> info,
		HttpServletRequest req, HttpServletResponse res )
	{
		// auto-populate basic request info
		info.put("request",req.getPathInfo());
		if ( statusCode < 400 ) { info.put("status","OK"); }
		else { info.put("status","ERROR"); }

		// convert errors to 200/OK + error message for Flash, etc.
		String flash = getParamString(req,"flash","");
		if ( flash != null && flash.equals("true") )
		{
			info.put("flash","true");
			info.put("statusCode",String.valueOf(statusCode));
			statusCode = res.SC_OK;
		}

		String content = null;
		String format = getParamString(req,"format",formatDefault);
		String contentType = null;
		if ( format.equals("json") )
		{
			content = JSONValue.toJSONString(info);
			contentType = "application/json";
		}
		else if ( format.equals("xml") )
		{
			content = toXML(info);
			contentType = "text/xml";
		}
		else if ( format.equals("html") )
		{
			content = toHTML(info);
			contentType = "text/html";
		}
		output( statusCode, content, contentType, res );
	}

	private void output( int status, String content, String contentType,
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
			log.error("Error sending output: " + ex.toString());
			log.warn( "Error sending output", ex );
		}
	}
	public static String toXML( Map m )
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
					Element sub = e.addElement("value");
					sub.setText( list.get(i).toString() );
				}
			}
		}
		return doc.asXML();
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
			StringBuffer buf = new StringBuffer();
			if ( val instanceof String )
			{
				buf.append( val.toString() );
			}
			else if ( val instanceof List )
			{
				List list = (List)val;
				for ( int i = 0; i < list.size(); i++ )
				{
					if ( i > 0 ) { buf.append(", "); }
					buf.append( list.get(i).toString() );
				}
			}

			Element row = table.addElement("tr");
			Element keyCell = row.addElement("td");
			keyCell.setText(key);
			Element valCell = row.addElement("td");
			valCell.setText(buf.toString());
		}
		return doc.asXML();
	}

	//========================================================================
	// Request parsing, input, etc.
	//========================================================================
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
	protected String getParamString( HttpServletRequest req, String key,
		String defaultValue )
	{
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
	protected boolean getParamBool( HttpServletRequest req, String key,
		boolean defaultValue )
	{
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
	protected int getParamInt( HttpServletRequest req, String key,
		int defaultValue )
	{
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
	protected static String[] path( HttpServletRequest req )
	{
		String pathstr = req.getPathInfo();
		if ( pathstr == null ) { return new String[]{}; }
		else { return pathstr.split("/"); }
	}
	protected static Map<String,String> parameters( HttpServletRequest req )
	{
		Map<String,String> params = new HashMap<String,String>();
		Enumeration<String> e = req.getParameterNames();
		while ( e.hasMoreElements() )
		{
			String key = e.nextElement();
			params.put( key, req.getParameter(key) );
		}
		return params;
	}
	protected InputBundle input( HttpServletRequest req )
		throws IOException, FileUploadException
	{
		// get parameters using vanilla api when there is not file upload
		if ( ! ServletFileUpload.isMultipartContent(req) )
		{
			return new InputBundle( parameters(req), null );
		}

		// process parts
		Map<String,String> params = new HashMap<String,String>();
		InputStream in = null;
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload( factory );
		upload.setSizeMax( maxUploadSize );
		List items = upload.parseRequest( req );
		for ( int i = 0; i < items.size(); i++ )
		{
			FileItem item = (FileItem)items.get(i);
			// form fields go in parameter map
			if ( item.isFormField() )
			{
				params.put( item.getFieldName(), item.getString() );
			}
			// file gets opened as an input stream
			else if ( item.getFieldName().equals("file") )
			{
				in = item.getInputStream();
			}
		}
		return new InputBundle( params, in );
	}
}
class InputBundle
{
	Map<String,String> params;
	InputStream in;
	InputBundle( Map<String,String> params, InputStream in )
	{
		this.params = params;
		this.in = in;
	}
	Map<String,String> getParams() { return params; }
	InputStream getInputStream() { return in; }
}
