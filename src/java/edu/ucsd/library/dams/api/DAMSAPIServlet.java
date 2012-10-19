package edu.ucsd.library.dams.api;

// java core api
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
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
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.util.HttpUtil;

/**
 * Servlet implementing the DAMS REST API.
 * @author escowles@ucsd.edu
**/
public class DAMSAPIServlet extends HttpServlet
{
	/*************************************************************************/
	/* Servlet init and shared state                                         */
	/*************************************************************************/

	// logging
	private static Logger log = Logger.getLogger(DAMSAPIServlet.class);

	// default output format
	private String formatDefault; // output format to use when not specified

	// default data stores
	private String fsDefault;    // FileStore to be used when not specified
	private String tsDefault;    // TripleStore to be used when not specified

	// id minters
	private String idDefault;    // ID series to be used when not specified
	private Map<String,String> idMinters; // ID series name=>url map

	// uploads
	private int uploadCount = 0; // current number of uploads being processed
	private int maxUploadCount;  // number of concurrent uploads allowed
	private long maxUploadSize;  // largest allowed upload size

	// ip address mapping
	private String roleDefault;           // default role if not matching
	private Map<String,String[]> roleMap; // map of roles to IP addresses

	// initialize servlet parameters
    public void init( ServletConfig config ) throws ServletException
    {
        try
        {
            InitialContext ctx = new InitialContext();

			// default output format
            formatDefault = (String)ctx.lookup("java:comp/env/dams/formatDefault");

			// default data stores
            fsDefault = (String)ctx.lookup("java:comp/env/dams/fsDefault");
            tsDefault = (String)ctx.lookup("java:comp/env/dams/tsDefault");

			// id minters
            idDefault = (String)ctx.lookup("java:comp/env/dams/idDefault");
			idMinters = new HashMap<String,String>();
			String minterConfig = (String)ctx.lookup(
				"java:comp/env/dams/idMinters"
			);
			String[] minterPairs = minterConfig.split(";");
			for ( int i = 0; i < minterPairs.length; i++ )
			{
				String[] minterParts = minterPairs[i].split(",");
				if ( minterParts.length == 2 && minterParts[0] != null
					&& minterParts[1] != null )
				{
					idMinters.put( minterParts[0], minterParts[1] );
				}
			}

			// upload limits
            Integer maxCount = (Integer)ctx.lookup(
                "java:comp/env/dams/maxUploadCount"
            );
            maxUploadCount = maxCount.intValue();
            Long maxSize = (Long)ctx.lookup("java:comp/env/dams/maxUploadSize");
            maxUploadSize = maxSize.longValue();

			// ip address mapping
			roleDefault = (String)ctx.lookup("java:comp/env/dams/roleDefault");
			String roleList = (String)ctx.lookup("java:comp/env/dams/roleList");
			String[] roleArray = roleList.split(",");
			roleMap = new HashMap<String,String[]>();
			for ( int i = 0; i < roleArray.length; i++ )
			{
				String ipList = (String)ctx.lookup(
					"java:comp/env/dams/role" + roleArray[i]
				);
				String[] ipArray = ipList.split(",");
				roleMap.put( roleArray[i], ipArray );
			}
        }
        catch ( Exception ex )
        {
            log.error( "Error initializing", ex );
        }

        super.init(config);
    }


	/*************************************************************************/
	/* REST API methods                                                      */
	/*************************************************************************/

	/**
	 * HTTP GET methods to retrieve objects and datastream metadata and files.
     * Calls to GET should not change state in any way.
	**/
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		// parse request URI
		String[] path = path( req );

		// GET /api/search
		if ( path.length == 3 && path[2].equals("search") )
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
			// GET /api/collections/bb1234567x/fixity	
			else if ( path[4].equals("fixity") )
			{
				collectionFixity( path[3], req, res );
			}
			// GET /api/collections/bb1234567x/validate
			else if ( path[4].equals("validate") )
			{
				objectValidate( path[3], req, res );
			}
		}
		// objects
		else if ( path.length > 2 && path[2].equals("objects") )
		{
			// GET /api/objects/$ark
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
			error( "Invalid request", req, res );
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

		// POST /api/next_id
		if ( path.length == 3 && path[2].equals("next_id") )
		{
			String idMinter = getParamString( req, "name", idDefault );
			int count = getParamInt( req, "count", 1 );
			identifierCreate( idMinter, count, req, res );
		}
		// collections
		else if ( path.length > 3 && path[2].equals("collections") )
		{
			// POST /api/collections/bb1234567x/characterize
			if ( path[4].equals("characterize") )
			{
				collectionCharacterize( path[3], req, res );
			}
			// POST /api/collections/bb1234567x/derivatives
			if ( path[4].equals("derivatives") )
			{
				collectionDerivatives( path[3], req, res );
			}
			// POST /api/collections/bb1234567x/index
			if ( path[4].equals("index") )
			{
				collectionIndexUpdate( path[3], req, res );
			}
			// POST /api/collections/bb1234567x/transform
			if ( path[4].equals("transform") )
			{
				collectionTransform( path[3], req, res );
			}
		}
		// objects
		else if ( path.length > 3 && path[2].equals("objects") )
		{
			// POST /api/objects/$ark
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
				objectIndexUpdate( path[3], req, res );
			}
		}
		// files
		else if ( path.length > 3 && path[2].equals("files") )
		{
			// POST /api/files/$ark/$file
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
			error( "Invalid request", req, res );
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

		// PUT /api/objects/$ark
		if ( path.length == 4 )
		{
			objectUpdate( path[3], req, res );
		}
		// PUT /api/objects/$ark/$file
		else if ( path.length == 5 )
		{
			fileUpload( path[3], path[4], true, req, res );
		}
		else
		{
			error( "Invalid request", req, res );
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

		// DELETE /api/collections/$ark/index
		if ( path.length == 5 && path[2].equals("collections")
			&& path[4].equals("index") )
		{
			collectionIndexDelete( path[3], req, res );
		}
		// DELETE /api/objects/$ark
		else if ( path.length == 4 && path[2].equals("objects") )
		{
			objectDelete( path[3], req, res );
		}
		// DELETE /api/objects/$ark/index
		else if ( path.length == 5 && path[2].equals("objects")
			&& path[4].equals("index") )
		{
			objectIndexDelete( path[3], req, res );
		}
		// DELETE /api/files/$ark/$file
		else if ( path.length == 5 && path[2].equals("files") )
		{
			fileDelete( path[3], path[4], req, res );
		}
		else
		{
			error( "Invalid request", req, res );
		}
	}


	/*************************************************************************/
	/* Core Java API                                                         */
	/*************************************************************************/
	public void clientAuthorize( HttpServletRequest req,
		HttpServletResponse res )
	{
		// output = redirect
		// XXX: should probably not even get here...
	}
	public void clientInfo( HttpServletRequest req, HttpServletResponse res )
	{
		String ip = req.getRemoteAddr();
		String role = getRole( ip ); 
		String user = req.getRemoteUser();
		if ( user == null ) { user = ""; }
		Map info = new LinkedHashMap();
		info.put( "ip", ip );
		info.put( "role", role );
		info.put( "user", user );
		status( info, req, res );
	}
	public void collectionCharacterize( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status token
		// EVENT_META: update event metadata
	}
	public void collectionCount( String colid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void collectionEmbargo( String colid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of objects (??)
	}
	public void collectionDerivatives( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status token
		// EVENT_META: update event metadata
	}
	public void collectionFixity( String colid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status token
		// EVENT_META: update event metadata
	}
	public void collectionIndexDelete( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void collectionIndexUpdate( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status token
		// EVENT_META: update event metadata
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
	public void collectionTransform( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status token
		// EVENT_META: update event metadata
	}
	public void collectionValidate( String colid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status token
		// EVENT_META: update event metadata
	}
	public void fileCharacterize( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void fileUpload( String objid, String fileid, boolean overwrite,
		 HttpServletRequest req, HttpServletResponse res )
	{
		// EVENT_META: update event metadata
		// FILE_META: update file metadata
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
	
        	if ( successful )
			{
				Map info = new LinkedHashMap();
				info.put("status","OK");
				info.put("message","File uploaded successfully");
				info.put("object",objid);
				info.put("file",fileid);
				status( info, req, res );
			}
			else
        	{
            	error( "Failed to upload file: " + objid + "/" + fileid, req, res );
        	}
		}
		finally
		{
			if ( fs != null ) { fs.close(); }
		}
	}
	public void fileDelete( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// EVENT_META: update event metadata
		// FILE_META: update file metadata
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
				Map info = new LinkedHashMap();
				info.put("status","OK");
				info.put("message","File deleted successfully");
				info.put("object",objid);
				info.put("file",fileid);
				status( info, req, res );
			}
			else
        	{
            	error( "Failed to delete file: " + objid + "/" + fileid, req, res );
        	}
		}
		finally
		{
			if ( fs != null ) { fs.close(); }
		}
	}
	public void fileDerivatives( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
		// FILE_META: update file metadata
	}
	public void fileFixity( String objid, String fileid,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public InputStream fileShow( HttpServletRequest req,
		HttpServletResponse res, String objid, String fileid )
	{
		// AAA: delegate to file servlet
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
			status( info, req, res );
		}
	}
	public void indexSearch( HttpServletRequest req, HttpServletResponse res )
	{
		String role = getRole( req.getRemoteAddr() );
		req.setAttribute("X-DAMS-Role",role);
		// output = metadata: search results
		// AAA: delegate to SolrProxy servlet
	}
	public void objectCreate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// XXX: see controller servlet
		// output = status message
		// EVENT_META: update event metadata
	}
	public void objectDelete( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// output = status message
		// EVENT_META: update event metadata
		// XXX: Triplestore.removeObject(subj);
	}
	public void objectIndexDelete( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void objectIndexUpdate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void objectShow( String objid, boolean export,
		HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: object
	}
	public void objectTransform( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: object
		// EVENT_META: update event metadata
	}
	public void objectUpdate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void objectValidate( String objid, HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = status message
		// EVENT_META: update event metadata
	}
	public void predicateList( HttpServletRequest req, HttpServletResponse res )
	{
		// DAMS_MGR
		// output = metadata: list of predicate URIs
	}
	public void statusToken( String jobid )
	{
		// XXX: get status from session and send message
		// DAMS_MGR
	}

	public void error( String msg, HttpServletRequest req,
		HttpServletResponse res )
	{
		error( res.SC_INTERNAL_SERVER_ERROR, msg, req, res );
	}
	public void error( int errorCode, String msg, HttpServletRequest req,
		HttpServletResponse res )
	{
		// output = error message
		// XXX: format as JSON, XML or HTML
	}
	public void status( Map<String,String> info, HttpServletRequest req,
		HttpServletResponse res )
	{
		String response = null;
		String format = getParamString(req,"format",formatDefault);
		if ( format.equals("json") )
		{
			response = JSONValue.toJSONString(info);
			output( res, response, "application/json" );
		}
		else if ( format.equals("xml") )
		{
			response = toXML(info);
			output( res, response, "text/xml" );
		}
		else if ( format.equals("html") )
		{
			response = toHTML(info);
			output( res, response, "text/html" );
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
	private void output( HttpServletResponse res, String content,
		String contentType )
	{
		res.setContentType( contentType );
		PrintWriter out = res.getWriter();
		out.print( content );
		out.close();
	}

	/*************************************************************************/
	/* Utility methods                                                       */
	/*************************************************************************/
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
