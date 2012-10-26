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
	private String owlSameAs;  // Untranslated URI for owlSameAs
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
			Properties props = new Properties();
			props.load( new FileInputStream(f) );

			// default output format
			formatDefault = props.getProperty( "format.default");

			// editor backup save dir
			String backupDir = props.getProperty("edit.backupDir");

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
			String maxCount = props.getProperty(
				"java:comp/env/dams/maxUploadCount"
			);
			maxUploadCount = Integer.parseInt(maxCount);
			String maxSize = props.getProperty(
				"java:comp/env/dams/maxUploadSize"
			);
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

		// GET /api/index
		if ( path.length == 3 && path[2].equals("index") )
		{
			indexSearch( req, res );
		}
		// GET /api/status/d3b07384d113edec49eaa6238ad5ff00
		if ( path.length == 4 && path[2].equals("status") )
		{
			statusToken( path[3] );
		}
		// collection
		else if ( path.length > 2 && path[2].equals("collections") )
		{
			// GET /api/collections
			if ( path.length == 3 )
			{
				collectionListAll( req, res );
			}
			// GET /api/collections/bb1234567x
			else if ( path.length == 4 )
			{
				collectionListObjects( path[3], req, res );
			}
			// GET /api/collections/bb1234567x/count
			else if ( path[4].equals("count") )
			{
				collectionCount( path[3], req, res );
			}
			// GET /api/collections/bb1234567x/embargo
			else if ( path[4].equals("embargo") )
			{
				collectionEmbargo( path[3], req, res );
			}
		}
		// objects
		else if ( path.length > 2 && path[2].equals("objects") )
		{
			// GET /api/objects/bb1234567x
			if ( path.length == 4 )
			{
				objectShow( path[3], false, req, res );
			}
			// GET /api/objects/bb1234567x/validate
			else if ( path.length == 5 && path[4].equals("validate") )
			{
				objectValidate( path[3], req, res );
			}
		}
		// files
		else if ( path.length > 2 && path[2].equals("files") )
		{
			// GET /api/files/bb1234567x/1-1.tif
			if ( path.length == 5 )
			{
				fileShow( req, res, path[3], path[4] );
			}
			// GET /api/files/bb1234567x/1-1.tif/fixity
			else if ( path.length == 6 && path[5].equals("fixity") )
			{
				fileFixity( path[3], path[4], req, res );
			}
		}
		// client
		else if ( path.length == 4 && path[2].equals("client") )
		{
			// GET /api/client/authorize
			if ( path[3].equals("authorize") )
			{
				clientAuthorize( req, res );
			}
			// GET /api/client/info
			else if ( path[3].equals("info") )
			{
				clientInfo( req, res );
			}
		}
		// predicates
		else if ( path.length == 3 && path[2].equals("predicates") )
		{
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

		// parse request URI
		String[] path = path( req );

		// POST /api/index
		if ( path.length == 3 && path[2].equals("index") )
		{
			String[] ids = req.getParameterValues("id");
			indexUpdate( ids, req, res );
		}
		// POST /api/next_id
		else if ( path.length == 3 && path[2].equals("next_id") )
		{
			String idMinter = getParamString( req, "name", minterDefault );
			int count = getParamInt( req, "count", 1 );
			identifierCreate( idMinter, count, req, res );
		}
		// objects
		else if ( path.length > 3 && path[2].equals("objects") )
		{
			// POST /api/objects/bb1234567x
			if ( path.length == 4 )
			{
				objectCreate( path[3], req, res );
			}
			// POST /api/objects/bb1234567x/transform
			else if ( path.length == 5 && path[4].equals("transform") )
			{
				objectTransform( path[3], req, res );
			}
			// POST /api/objects/bb1234567x/index	
			else if ( path.length == 5 && path[4].equals("index") )
			{
				String[] ids = new String[]{ path[3] };
				indexUpdate( ids, req, res );
			}
		}
		// files
		else if ( path.length > 3 && path[2].equals("files") )
		{
			// POST /api/files/bb1234567x/1-1.tif
			if ( path.length == 5 )
			{
				fileUpload( path[3], path[4], false, req, res );
			}
			// POST /api/files/bb1234567x/1-1.tif/characterize
			else if ( path.length == 6 && path[5].equals("characterize") )
			{
				fileCharacterize( path[3], path[4], req, res );
			}
			// POST /api/files/bb1234567x/1-1.tif/derivatives
			else if ( path.length == 6 && path[5].equals("derivatives") )
			{
				fileDerivatives( path[3], path[4], req, res );
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

		// PUT /api/objects/bb1234567x
		if ( path.length == 4 )
		{
			objectUpdate( path[3], req, res );
		}
		// PUT /api/objects/bb1234567x/1-1.tif
		else if ( path.length == 5 )
		{
			fileUpload( path[3], path[4], true, req, res );
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

		// DELETE /api/index
		if ( path.length == 3 && path[2].equals("index") )
		{
			String[] ids = req.getParameterValues("id");
			indexDelete( ids, req, res );
		}
		// DELETE /api/objects/bb1234567x
		if ( path.length == 4 && path[2].equals("objects") )
		{
			objectDelete( path[3], req, res );
		}
		// DELETE /api/objects/bb1234567x/index
		else if ( path.length == 5 && path[2].equals("objects")
			&& path[4].equals("index") )
		{
			String[] ids = new String[]{ path[3] };
			indexDelete( ids, req, res );
		}
		// DELETE /api/files/bb1234567x/1-1.tif
		else if ( path.length == 5 && path[2].equals("files") )
		{
			fileDelete( path[3], path[4], req, res );
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
			Map<String,String> params = new HashMap<String,String>();
			InputStream in = null;
			input( req, params, in );

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
	
			String fsName = getParamString( req, "fs", "fsDefault" );
			fs = FileStoreUtil.getFileStore( fsName );
	
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
	
			String fsName = getParamString( req, "fs", "fsDefault" );
			fs = FileStoreUtil.getFileStore( fsName );
	
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
	public void fileFixity( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
	}
	public void fileShow( HttpServletRequest req,
		HttpServletResponse res, String objid, String fileid )
	{
		// XXX: access control
		req.setAttribute(
			"edu.ucsd.library.dams.api.DAMSAPIServlet.authorized","true"
		);
		String url = "/file/" + objid + "/" + fileid + "?"
			+ req.getQueryString();
		try
		{
			res.sendRedirect( url );
		}
		catch ( Exception ex )
		{
			log.error("Error sending redirect: " + ex.toString());
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
				log.warn("Unable to set chararacter encoding");
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
				log.warn("Error looking up profile filter (" + profile + "): " + ex.toString());
			}
		}

		// check ip and username
		// XXX: AUTH: who is making the request? hydra/isla. or end-user?
		String username = req.getRemoteUser();
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
				log.warn("Error looking up role filter (" + role + "): " + ex.toString());
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
		String ds = getParamString(req,"ds",tsDefault);
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
			log.error( ex );
			error( "Error performing Solr search: " + ex.toString(), req, res );
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
			Map<String,String> params = new HashMap<String,String>();
			InputStream in = null;
			input( req, params, in );

			// connect to triplestore
			String tsName = getParamString(req,"ts",tsDefault);
			ts = TripleStoreUtil.getTripleStore(tsName);

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
				String adds = getParamString(req,"adds","");
				String updates = null;
				String deletes = null;
				if ( !create )
				{
					updates = getParamString(req,"updates","");
					deletes = getParamString(req,"deletes","");
				}
				if ( adds != null && !adds.equals("") )
				{
					// save data to the triplestore
					Edit edit = new Edit(
						backupDir, adds, updates, deletes, objid, ts,
						idNS, prNS, owlSameAs
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
			ts = TripleStoreUtil.getTripleStore(tsName);

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
			TripleStore ts = TripleStoreUtil.getTripleStore(tsName);
			SolrIndexer indexer = new SolrIndexer(
				ts, solrBase, idNS, prNS, owlSameAs
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
		}
	}
	public void objectShow( String objid, boolean export,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR or VIEW_SERVLET?
		// output = metadata: object
	}
	public void objectTransform( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: object
	}
	public void objectValidate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
	}
	public void predicateList( HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of predicate URIs
	}
	public void statusToken( String jobid )
	{
		// DAMS_MGR: get status from session and send message
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
			ts = TripleStoreUtil.getTripleStore(tsName);

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
				ts = TripleStoreUtil.getTripleStore(tsName);
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
			Identifier eventID = Identifier.publicURI( idNS, eventARK );
	
			// lookup user identifier
			// XXX: AUTH: who is making the request? hydra/isla. or end-user?
			Identifier userID = null; // XXX SolrProxy.lookup( req.getRemoteUser() );
	
			// create event object and save to the triplestore
			String obj = objid.startsWith("http") ? objid : idNS + objid;
			if ( fileid != null ) { obj += "/" + fileid; }
			Identifier subID = Identifier.publicURI( idNS + obj );
			Event e = new Event(
				eventID, subID, userID, success, type,
				detail, outcomeNote, prNS
			);
			e.save(ts);
		}
		catch ( IOException ex )
		{
			log.error("Error minting event ARK: " + ex.toString());
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
		if ( statusCode < 400 ) { info.put("status","ERROR"); }
		else { info.put("status","OK"); }

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
		if ( format.equals("json") )
		{
			content = JSONValue.toJSONString(info);
			res.setContentType( "application/json" );
		}
		else if ( format.equals("xml") )
		{
			content = toXML(info);
			res.setContentType( "text/xml" );
		}
		else if ( format.equals("html") )
		{
			content = toHTML(info);
			res.setContentType( "text/html" );
		}

		// output content
		try
		{
			if ( statusCode < 400 )
			{
				PrintWriter out = res.getWriter();
				out.print( content );
				out.close();
			}
			else
			{
				res.sendError( statusCode, content );
			}
		}
		catch ( Exception ex )
		{
			log.error("Error sending output: " + ex.toString());
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
		if ( value != null || !value.trim().equals("") )
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
	protected void input( HttpServletRequest req,
		Map<String,String> params, InputStream in )
		throws IOException, FileUploadException
	{
		// get parameters using vanilla api when there is not file upload
		if ( ! ServletFileUpload.isMultipartContent(req) )
		{
			params = parameters( req );
			in = null;
			return;
		}

		// process parts
		if ( params == null ) { params = new HashMap<String,String>(); }
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
			else
			{
				in = item.getInputStream();
			}
		}
	}
}
