package edu.ucsd.library.dams.api;

// java core api
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.net.URLEncoder;
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
	protected String fsDefault;	// FileStore to be used when not specified
	protected String tsDefault;	// TripleStore to be used when not specified

	// identifiers and namespaces
	private String minterDefault;	      // ID series when not specified
	private Map<String,String> idMinters; // ID series name=>url map
	private Map<String,String> nsmap;     // URI/name to URI map
	private String idNS;                  // Prefix for unqualified identifiers

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
			nsmap = TripleStoreUtil.namespaceMap(props);
			idNS = nsmap.get("damsid");

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
		Map info = null;
		boolean outputRequired = true; // set to false to suppress status output
		FileStore fs = null;
		TripleStore ts = null;

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
			// GET /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				info = objectShow( path[2], false, ts );
				if ( info.get("obj") != null )
				{
					DAMSObject obj = (DAMSObject)info.get("obj");
					output( obj, false, req.getParameterMap(), req.getPathInfo(), res );
					outputRequired = false;
				}
			}
			// GET /objects/bb1234567x/export
			else if ( path.length == 4  && path[1].equals("objects")
				&& path[3].equals("export") )
			{
				ts = triplestore(req);
				info = objectShow( path[2], true, ts );
				if ( info.get("obj") != null )
				{
					DAMSObject obj = (DAMSObject)info.get("obj");
					output( obj, true, req.getParameterMap(), req.getPathInfo(), res );
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
			// GET /objects/bb1234567x/validate
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("validate") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				info = objectValidate( path[2], fs, ts );
			}
			// GET /files/bb1234567x/1-1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				fileShow( path[2], path[3], req, res );
				outputRequired = false;
			}
			// GET /files/bb1234567x/1-1.tif/exists
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("exists") )
			{
				fs = filestore(req);
				info = fileExists( path[2], path[3], fs );
			}
			// GET /files/bb1234567x/1-1.tif/fixity
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("fixity") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				info = fileFixity( path[2], path[3], fs, ts );
			}
			// GET /client/info
			else if ( path.length == 3 && path[1].equals("client")
				&& path[2].equals("info") )
			{
				String ip = req.getRemoteAddr();
				String user = req.getRemoteUser();
				info = clientInfo( ip, user );
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
			}
			// GET /system/triplestores
			else if ( path.length == 3 && path[1].equals("system" )
				&& path[2].equals("triplestores") )
			{
				List<String> triplestores = list(props,"ts.",".className");
				info = new LinkedHashMap();
				info.put( "triplestores", triplestores );
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
			cleanup( fs, ts );
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

		try
		{
			// parse request URI
			String[] path = path( req );
	
			// POST /index
			if ( path.length == 2 && path[1].equals("index") )
			{
				String[] ids = req.getParameterValues("id");
				ts = triplestore(req);
				info = indexUpdate( ids, ts );
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
					String adds = getParamString(
						bundle.getParams(), "ts", tsDefault
					);
					ts = triplestore(req);
					info = objectCreate( path[2], in, adds, ts );
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
				String xsl = getParamString(req,"xsl",null);
				String dest = getParamString(req,"dest",null);
				boolean export = getParamBool(req,"recursive",false);
				if ( dest != null )
				{
					fs = filestore(req);
				}
				objectTransform(
					path[2], null, export, ts, xsl, fs, dest,
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
				info = indexUpdate( ids, ts );
			}
			// POST /files/bb1234567x/1-1.tif
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
						fs = filestore(req);
						ts = triplestore(req);
						info = fileUpload(path[2], path[3], false, in, fs, ts);
					}
				}
				catch ( Exception ex )
				{
					log.warn("Error uploading file", ex );
					info = error("Error uploading file");
				}
			}
			// POST /files/bb1234567x/1-1.tif/characterize
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("characterize") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				info = fileCharacterize( path[2], path[3], fs, ts );
			}
			// POST /files/bb1234567x/1-1.tif/derivatives
			else if ( path.length == 5 && path[1].equals("files")
				&& path[4].equals("derivatives") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				info = fileDerivatives( path[2], path[3], fs, ts );
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
			log.warn( "Error processing POST request", ex2 );
		}
		finally
		{
			// cleanup
			cleanup( fs, ts );
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

		try
		{
			// parse request URI
			String[] path = path( req );
	
			// PUT /objects/bb1234567x
			if ( path.length == 3 )
			{
				try
				{
					InputBundle bundle = input( req );
					InputStream in = bundle.getInputStream();
					Map<String,String[]> params = bundle.getParams();
					String adds    = getParamString(params,"adds",null);
					String updates = getParamString(params,"updates",null);
					String deletes = getParamString(params,"deletes",null);
					ts = triplestore(req);
					info = objectUpdate(
						path[2], in, adds, updates, deletes, ts
					);
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating object", ex );
					info = error( "Error updating object" );
				}
			}
			// PUT /objects/bb1234567x/1-1.tif
			else if ( path.length == 4 )
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
						fs = filestore(req);
						ts = triplestore(req);
						info = fileUpload( path[2], path[3], true, in, fs, ts );
					}
				}
				catch ( Exception ex )
				{
					log.warn( "Error updating file", ex );
					info = error( "Error updating file" );
				}
			}
			else
			{
				info = error( res.SC_BAD_REQUEST, "Invalid request" );
			}

			// output
			output( info, req.getParameterMap(), req.getPathInfo(), res );
		}
		catch ( Exception ex2 )
		{
			log.warn( "Error processing PUT request", ex2 );
		}
		finally
		{
			// cleanup
			cleanup( fs, ts );
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

		try
		{
			// parse request URI
			String[] path = path( req );

			// DELETE /index
			if ( path.length == 2 && path[1].equals("index") )
			{
				String[] ids = req.getParameterValues("id");
				String tsName = getParamString(req,"ts",tsDefault);
				info = indexDelete( ids, tsName );
			}
			// DELETE /objects/bb1234567x
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				info = objectDelete( path[2], ts );
			}
			// DELETE /objects/bb1234567x/index
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("index") )
			{
				String[] ids = new String[]{ path[2] };
				String tsName = getParamString(req,"ts",tsDefault);
				info = indexDelete( ids, tsName );
			}
			// DELETE /files/bb1234567x/1-1.tif
			else if ( path.length == 4 && path[1].equals("files") )
			{
				fs = filestore(req);
				ts = triplestore(req);
				info = fileDelete( path[2], path[3], fs, ts );
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
			cleanup( fs, ts );
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
	protected static void cleanup( FileStore fs, TripleStore ts )
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
	}


	//========================================================================
	// Core Java API
	//========================================================================
	public Map clientInfo( String ip, String user )
	{
		// ZZZ: AUTH: who is making the request? hydra or end-user?
		String role = getRole( ip ); 
		if ( user == null ) { user = ""; }
		Map info = new LinkedHashMap();
		info.put( "statusCode", HttpServletResponse.SC_OK );
		info.put( "ip", ip );
		info.put( "role", role );
		info.put( "user", user );
		return info;
	}
	public Map collectionCount( String colid, TripleStore ts )
	{
		return null; // DAMS_MGR
		// output = status message
	}
	public Map collectionEmbargo( String colid, TripleStore ts )
	{
		return null; // DAMS_MGR
		// output = metadata: list of objects (??)
	}
	public Map collectionIndexDelete( String colid, String tsName )
	{
		return null; // DAMS_MGR
		// output = status message
	}
	public Map collectionListAll( TripleStore ts )
	{
		return null; // DAMS_MGR
		// output = metadata: list of collection objects
// AAA IMPL
// - list all collection URIs (sparql)
// - get all top-level collection objects (bulk describe)
	}
	public Map collectionListObjects( String colid, TripleStore ts  )
	{
		return null; // DAMS_MGR
		// output = metadata: list of objects
	}
	public Map fileCharacterize( String objid, String fileid, FileStore fs, TripleStore ts )
	{
		return null; // DAMS_MGR
		// output = status message
		// if res is null, update triplestore but don't output anything
	}
	public Map fileUpload( String objid, String fileid, boolean overwrite,
		 InputStream in, FileStore fs, TripleStore ts )
	{
		try
		{
			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				return error(HttpServletResponse.SC_BAD_REQUEST, "Object identifier required");
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "File identifier required");
			}
	
			if ( in == null )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "File upload required");
			}

			// check upload count and abort if at limit
			if ( uploadCount >= maxUploadCount )
			{
				log.info("Upload: refused");
				return error(
					HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Too many concurrent uploads"
				);
			}
			else
			{
				uploadCount++;
				log.info("Upload: start: " + uploadCount);
			}
	
			// make sure appropriate method is being used to create/update
			if ( !overwrite && fs.exists( objid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File already exists, use PUT to overwrite"
				);
			}
			else if ( overwrite && !fs.exists( objid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist, use POST to create"
				);
			}
	
			// upload file
			fs.write( objid, fileid, in );
			boolean successful = fs.exists(objid,fileid)
				&& fs.length(objid,fileid) > 0;
			in.close();
	
			String type = (overwrite) ? "file modification" : "file creation";
			String detail = "EVENT_DETAIL_SPEC: " + type;
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
				createEvent(
					ts, objid, fileid, type, true, detail, null
				);

				// FILE_META: update file metadata
				fileDeleteMetadata( objid, fileid, ts );
				fileCharacterize( objid, fileid, fs, ts );

				return status( status, message );
			}
			else
			{
				if ( overwrite ) { message = "File update failed"; }
				else { message = "File creation failed"; }
				createEvent(
					ts, objid, fileid, type, false, detail,
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
	}
	public Map fileDelete( String objid, String fileid, FileStore fs, TripleStore ts )
	{
		try
		{
			// both objid and fileid are required
			if ( objid == null || objid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "Object identifier required" );
			}
			if ( fileid == null || fileid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "File identifier required" );
			}
	
			// make sure the file exists
			if ( !fs.exists( objid, fileid ) )
			{
				return error(
					HttpServletResponse.SC_FORBIDDEN,
					"File does not exist"
				);
			}
	
			// delete the file
			fs.trash( objid, fileid );
			boolean successful = !fs.exists(objid,fileid);
	
			if ( successful )
			{
				createEvent(
					null, objid, fileid, "file deletion", true,
					"Deleting file EVENT_DETAIL_SPEC", null
				);

				// FILE_META: update file metadata
				fileDeleteMetadata( objid, fileid, ts );

				return status( "File deleted successfully" );
			}
			else
			{
				createEvent(
					null, objid, fileid, "file deletion", false,
					"Deleting file EVENT_DETAIL_SPEC", "outcome detail spec"
				);
				return error(
					"Failed to delete file: " + objid + "/" + fileid
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting file", ex );
			return error( "Error deleting file: " + ex.toString() );
		}
	}
	public Map fileDerivatives( String objid, String fileid,
		FileStore fs, TripleStore ts )
	{
		return null; // DAMS_MGR
	}
	public Map fileExists( String objid, String fileid, FileStore fs )
	{
		try
		{
			if ( fs.exists( objid, fileid ) )
			{
				return status( "File exists" );
			}
			else
			{
				return error( HttpServletResponse.SC_NOT_FOUND, "File does not exist" );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error checking file existence", ex );
			return error( "Error processing request: " + ex.toString() );
		}
	}
	public Map fileFixity( String objid, String fileid, FileStore fs,
		TripleStore ts )
	{
		return null; // DAMS_MGR
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
	public Map objectCreate( String objid, InputStream in, String adds, TripleStore ts )
	{
		return objectEdit( objid, true, in, adds, null, null, ts );
	}
	public Map objectUpdate( String objid, InputStream in, String adds, String updates, String deletes, TripleStore ts )
	{
		return objectEdit( objid, false, in, adds, updates, deletes, ts );
	}
	private Map objectEdit( String objid, boolean create, InputStream in, String adds, String updates, String deletes, TripleStore ts )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "No subject provided" );
			}

	   		// make sure appropriate method is being used to create/update
			if ( !objid.startsWith("http") )
			{
				objid = idNS + objid;
			}
			Identifier id = Identifier.publicURI(objid);
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
				return null; // DAMS_MGR
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
					String detail = "EVENT_DETAIL_SPEC: " + type;
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
						createEvent(
							ts, objid, null, type, true, detail, null
						);
						edit.removeBackup();
						return status( status, message );
					}
					else
					{
						// failure
						String msg = edit.getException().toString();
						createEvent( ts, objid, null, type, false, "EVENT_DETAIL_SPEC", msg );
						return error( msg );
					}
				}
				else
				{
					return error( HttpServletResponse.SC_BAD_REQUEST, "Object metadata must be supplied as a file upload or in the adds parameter" );
				}
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error editing object", ex );
			return error( "Error editing object: " + ex.toString() );
		}
	}
	public Map objectDelete( String objid, TripleStore ts )
	{
		try
		{
			// make sure an identifier is specified
			if ( objid == null || objid.trim().equals("") )
			{
				return error( HttpServletResponse.SC_BAD_REQUEST, "No subject provided" );
			}

	   		// make sure appropriate method is being used to create/update
			if ( !objid.startsWith("http") )
			{
				objid = idNS + objid;
			}
			Identifier id = Identifier.publicURI(objid);
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
				createEvent( ts, objid, null, "object deletion", true, "Deleting object EVENT_DETAIL_SPEC", null );
				return status( "Object deleted successfully" );
			}
			else
			{
				createEvent( ts, objid, null, "object deletion", false, "Deleting object EVENT_DETAIL_SPEC", "outcome detail spec" );
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
	public Map indexDelete( String[] ids, String tsName )
	{
		// make sure we have some ids to index
		if ( ids == null || ids.length == 0 )
		{
			return error( HttpServletResponse.SC_BAD_REQUEST, "No identifier specified" );
		}

		// connect to solr
		SolrHelper solr = new SolrHelper( solrBase );

		int recordsDeleted = 0;
		try
		{
			// delete individual records
			for ( int i = 0; i < ids.length; i++ )
			{
				if ( solr.delete( tsName, tsName, ids[i] ) )
				{
					recordsDeleted++;
				}
			}

			// commit changes
			solr.commit( tsName );

			// report status
			return status( "Solr: deleted " + recordsDeleted + " records" );
		}
		catch ( Exception ex )
		{
			log.warn( "Error deleting records", ex );
			return error( "Error deleting records: " + ex.toString() );
		}
	}
	public Map indexUpdate( String[] ids, TripleStore ts )
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

			// index each record
			for ( int i = 0; i < ids.length; i++ )
			{
				indexer.indexSubject( ids[i] );
			}

			// commit changes
			indexer.flush();
			indexer.commit();

			// output status message
			return status( indexer.summary() );
		}
		catch ( Exception ex )
		{
			log.warn( "Error updating Solr", ex );
			return error( "Error updating Solr: " + ex.toString() );
		}
	}
	public Map objectShow( String objid, boolean export, TripleStore ts )
	{
		// output = metadata: object
		try
		{
			if ( objid == null || objid.equals("") )
			{
				return error(
					HttpServletResponse.SC_BAD_REQUEST, "Object id must be specified"
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

			DAMSObject obj = new DAMSObject( ts, objid, nsmap );
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
	public void objectTransform( String objid, String fileid, boolean export,
		TripleStore ts, String xslName, Map<String,String[]> params,
		String pathInfo, HttpServletResponse res )
	{
		objectTransform(
			objid, fileid, export, ts, xslName,
			null, null, params, pathInfo, res
		);
	}
	/**
	 * Retrieve the RDF/XML for an object, and transform it using XSLT (passing
	 * the fileid as a parameter to the stylesheet) optionally saving the
	 * output as a new file.
	 * @param objid Object identifier.
	 * @param fileid File identifier.
	 * @param ts TripleStore to retrieve the object from.
	 * @param xslName Filename of XSL stylesheet.
	 * @param fs If not null, save the result to this FileStore.
	 * @param destid If not null, save the result as this file.
	**/
	public void objectTransform( String objid, String fileid, boolean export,
		TripleStore ts, String xslName, FileStore fs, String destid,
		Map<String,String[]> params, String pathInfo, HttpServletResponse res )
	{
		try
		{
			// get object from triplestore as Document
			Map m = objectShow( objid, export, ts );
			String rdfxml = null;
			if ( m.get("obj") != null )
			{
				DAMSObject obj = (DAMSObject)m.get("obj");
				rdfxml = obj.getRDFXML(export);
			}

			// then do xslt...
			if ( fileid != null ) { params.put("fileid",new String[]{fileid}); }
			String content = xslt( rdfxml, xslName, params, queryString(params) );
			output( res.SC_OK, content, "text/xml", res );
		}
		catch ( Exception ex )
		{
			output( error("Error transforming metadata"), params, pathInfo, res );
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
	public Map objectValidate( String objid, FileStore fs, TripleStore ts )
	{
		return null; // DAMS_MGR
	}
	public Map predicateList( TripleStore ts )
	{
		try
		{
			// setup damsobject
			DAMSObject trans = new DAMSObject( ts, null, nsmap );
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

	private void fileDeleteMetadata( String objid, String fileid,
		TripleStore ts ) throws TripleStoreException
	{
		try
		{
			// identifier object for the file
			Identifier fileIdentifier = Identifier.publicURI(
				idNS + objid + "/" + fileid
			);

			// delete file metadata
// ZZZ: need to delete bnodes related to any linked SourceCapture and/or
// OtherRights objects
			ts.removeStatements( fileIdentifier, null, null );

			// delete links from object/components
			ts.removeStatements( null, null, fileIdentifier );
		}
		catch ( Exception ex )
		{
			log.error( "Error deleting file metadata", ex );
		}
	}
	private void createEvent( TripleStore ts, String objid, String fileid,
		String type, boolean success, String detail, String outcomeNote )
		throws TripleStoreException
	{
		try
		{
			// mint ARK for event
			String minterURL = idMinters.get(minterDefault) + "1";
			String eventARK = HttpUtil.get(minterURL);
			eventARK = eventARK.replaceAll(".*/","");
			Identifier eventID = Identifier.publicURI( idNS + eventARK );
	
			// lookup user identifier
			// ZZZ: AUTH: who is making the request? hydra or end-user?
			Identifier userID = null;

			// predicate translator
			DAMSObject trans = new DAMSObject( ts, objid, nsmap );
	
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
			log.warn( "Error minting event ARK", ex );
		}
	}

	//=========================================================================
	// Methods that handle their own response
	//=========================================================================
	public void fileShow( String objid, String fileid, HttpServletRequest req,
		HttpServletResponse res )
	{
		// ZZZ: access control
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
		// ZZZ: AUTH: who is making the request? hydra or end-user?
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
					http.releaseConnection();
				}
			}

			// output reformatting
			Exception formatEx = null;
			if ( output != null && xsl != null && !xsl.equals("") )
			{
				xsl = xslBase + xsl;
				xsl = xsl.replaceAll("\\.\\.",""); // prevent snooping
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
					Element sub = e.addElement("value");
					sub.setText( list.get(i).toString() );
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
			StringBuffer buf = new StringBuffer();
			Element row = table.addElement("tr");
			Element keyCell = row.addElement("td");
			keyCell.setText(key);
			Element valCell = row.addElement("td");
			if ( val instanceof String )
			{
				buf.append( val.toString() );
				valCell.setText(buf.toString());
			}
			else if ( val instanceof List )
			{
				List list = (List)val;
				for ( int i = 0; i < list.size(); i++ )
				{
					if ( i > 0 ) { buf.append(", "); }
					buf.append( list.get(i).toString() );
				}
				valCell.setText(buf.toString());
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
    public static String xslt( String xml, String xslURL,
        Map<String,String[]> params, String queryString )
		throws TransformerException
    {
        // params
        String casGroupTest = getParamString(params,"casGroupTest",null);

        // setup the transformer
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer(
            new StreamSource(xslURL)
        );

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
	protected static String getParamString( Map<String,String[]> params,
		String key, String defaultValue )
	{
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
	protected static String getParamString( HttpServletRequest req, String key,
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
	protected static boolean getParamBool( HttpServletRequest req, String key,
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
	protected static int getParamInt( HttpServletRequest req, String key,
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
	protected static int getParamInt( Map<String,String[]> params, String key,
		int defaultValue )
	{
		int value = defaultValue;
		String[] arr = params.get(key);
		if ( arr != null && arr.length > 0 && arr[0] != null )
		{
			try
			{
				int i = Integer.parseInt(arr[0]);
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
		if ( pathstr == null ) { return new String[]{}; }
		else { return pathstr.split("/"); }
	}
	protected InputBundle input( HttpServletRequest req )
		throws IOException, FileUploadException
	{
		// get parameters using vanilla api when there is not file upload
		if ( ! ServletFileUpload.isMultipartContent(req) )
		{
			return new InputBundle( req.getParameterMap(), null );
		}

		// process parts
		Map<String,String[]> params = new HashMap<String,String[]>();
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
				params.put(
					item.getFieldName(), new String[]{item.getString()}
				);
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
