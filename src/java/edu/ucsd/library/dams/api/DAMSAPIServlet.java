package edu.ucsd.library.dams.api;
/*
XXX: output
XXX: error reporting
*/

import java.io.InputStream;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementing the DAMS REST API.
 * @author escowles@ucsd.edu
**/
public class DAMSAPIServlet extends APIBase
{
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
		Map<String,String> params = parameters(req);

		// GET /api/search
		if ( path.length == 3 && path[2].equals("search") )
		{
			indexSearch( params );
		}
		// collection
		else if ( path.length > 2 && path[2].equals("collections") )
		{
			// GET /api/collections
			if ( path.length == 3 )
			{
				collectionListAll( params );
			}
			// GET /api/collections/bb1234567x
			else if ( path.length == 4 )
			{
				collectionListObjects( path[3], params );
			}
			// GET /api/collections/bb1234567x/count
			else if ( path[4].equals("count") )
			{
				collectionCount( path[3], params );
			}
			// GET /api/collections/bb1234567x/embargo
			else if ( path[4].equals("embargo") )
			{
				collectionEmbargo( path[3], params );
			}
			// GET /api/collections/bb1234567x/fixity	
			else if ( path[4].equals("fixity") )
			{
				collectionFixity( path[3], params );
			}
			// GET /api/collections/bb1234567x/validate
			else if ( path[4].equals("validate") )
			{
				objectValidate( path[3], params );
			}
		}
		// objects
		else if ( path.length > 2 && path[2].equals("objects") )
		{
			// GET /api/objects/$ark
			if ( path.length == 4 )
			{
				objectShow( path[3], false, params );
			}
			// GET /api/objects/bb1234567x/validate
			else if ( path.length == 5 && path[4].equals("validate") )
			{
				objectValidate( path[3], params );
			}
		}
		// files
		else if ( path.length > 2 && path[2].equals("files") )
		{
			// GET /api/files/bb1234567x/1-1.tif
			if ( path.length == 5 )
			{
				fileShow( path[3], path[4], params );
			}
			// GET /api/files/bb1234567x/1-1.tif/fixity
			else if ( path.length == 6 && path[5].equals("fixity") )
			{
				fileFixity( path[3], path[4], params );
			}
		}
		// client
		else if ( path.length == 4 && path[2].equals("client") )
		{
			// GET /api/client/authorize
			if ( path[3].equals("authorize") )
			{
				clientAuthorize( params );
			}
			// GET /api/client/info
			else if ( path[3].equals("info") )
			{
				clientInfo( params );
			}
		}
		// predicates
		else if ( path.length == 3 && path[2].equals("predicates") )
		{
			predicateList( params );
		}
		else
		{
			error( "Invalid request" );
		}
	}

	/**
	 * HTTP POST methods to create identifiers, objects, datastreams and
	 * relationships.  Calls to POST should be used to create resources.
	**/
	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		// parse request URI
		String[] path = path( req );

		// parse request entity
		Map<String,String> params = new HashMap<String,String>();
		InputStream in = null;
		input( req, params, in );

		// POST /api/next_id
		if ( path.length == 3 && path[2].equals("next_id") )
		{
			identifierCreate( params );
		}
		// collections
		else if ( path.length > 3 && path[2].equals("collections") )
		{
			// POST /api/collections/bb1234567x/characterize
			if ( path[4].equals("characterize") )
			{
				collectionCharacterize( path[3], params );
			}
			// POST /api/collections/bb1234567x/derivatives
			if ( path[4].equals("derivatives") )
			{
				collectionDerivatives( path[3], params );
			}
			// POST /api/collections/bb1234567x/index
			if ( path[4].equals("index") )
			{
				collectionIndexUpdate( path[3], params );
			}
			// POST /api/collections/bb1234567x/transform
			if ( path[4].equals("transform") )
			{
				collectionTransform( path[3], params );
			}
		}
		// objects
		else if ( path.length > 3 && path[2].equals("objects") )
		{
			// POST /api/objects/$ark
			if ( path.length == 4 )
			{
				objectCreate( path[3], params, in );
			}
			// POST /api/objects/bb1234567x/transform
			else if ( path.length == 5 && path[4].equals("transform") )
			{
				objectTransform( path[3], params );
			}
			// POST /api/objects/bb1234567x/index	
			else if ( path.length == 5 && path[4].equals("index") )
			{
				objectIndexUpdate( path[3], params );
			}
		}
		// files
		else if ( path.length > 3 && path[2].equals("files") )
		{
			// POST /api/files/$ark/$file
			if ( path.length == 5 )
			{
				fileCreate( path[3], path[4], params, in );
			}
			// POST /api/files/bb1234567x/1-1.tif/characterize
			else if ( path.length == 6 && path[5].equals("characterize") )
			{
				fileCharacterize( path[3], path[4], params );
			}
			// POST /api/files/bb1234567x/1-1.tif/derivatives
			else if ( path.length == 6 && path[5].equals("derivatives") )
			{
				fileDerivatives( path[3], path[4], params );
			}
		}
		else
		{
			error( "Invalid request" );
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

		// parse request entity
		Map<String,String> params = new HashMap<String,String>();
		InputStream in = null;
		input( req, params, in );

		// PUT /api/objects/$ark
		if ( path.length == 4 )
		{
			objectUpdate( path[3], params, in );
		}
		// PUT /api/objects/$ark/$file
		else if ( path.length == 5 )
		{
			fileUpdate( path[3], path[4], params, in );
		}
		else
		{
			error( "Invalid request" );
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
		Map<String,String> params = parameters(req);

		// DELETE /api/collections/$ark/index
		if ( path.length == 5 && path[2].equals("collections")
			&& path[4].equals("index") )
		{
			collectionIndexDelete( path[3], params );
		}
		// DELETE /api/objects/$ark
		else if ( path.length == 4 && path[2].equals("objects") )
		{
			objectDelete( path[3], params );
		}
		// DELETE /api/objects/$ark/index
		else if ( path.length == 5 && path[2].equals("objects")
			&& path[4].equals("index") )
		{
			objectIndexDelete( path[3], params );
		}
		// DELETE /api/files/$ark/$file
		else if ( path.length == 5 && path[2].equals("files") )
		{
			fileDelete( path[3], path[4], params );
		}
		else
		{
			error( "Invalid request" );
		}
	}
}
