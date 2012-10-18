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
	**/
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		// parse request URI
		String[] path = path( req );

		// GET /api/search
		if ( path.length == 3 && path[2].equals("search") )
		{
			searchRepository( parameters(req) );
		}
		// GET /api/objects/$ark
		else if ( path.length == 4 )
		{
			objectMetadata( path[3], false, parameters(req) );
		}
		// GET /api/objects/$ark/$file
		else if ( path.length == 5 )
		{
			getFile( path[3], path[4], parameters(req) );
		}
		else
		{
			error( "Invalid request" );
		}
	}

	/**
	 * HTTP POST methods to create identifiers, objects, datastreams and
	 * relationships.
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
			createIdentifier( params );
		}
		// POST /api/objects/$ark
		else if ( path.length == 4 )
		{
			createObject( path[3], params, in );
		}
		// POST /api/objects/$ark/$file
		else if ( path.length == 5 )
		{
			createFile( path[3], path[4], params, in );
		}
		else
		{
			error( "Invalid request" );
		}
	}
	/**
	 * HTTP PUT methods to modify objects and datastreams.
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
			updateObject( path[3], params, in );
		}
		// PUT /api/objects/$ark/$file
		else if ( path.length == 5 )
		{
			updateFile( path[3], path[4], params, in );
		}
		else
		{
			error( "Invalid request" );
		}
	}

	/**
	 * HTTP DELETE methods to delete objects, datastreams and relationships.
	**/
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
		// parse request URI
		String[] path = path( req );

		// DELETE /api/objects/$ark
		if ( path.length == 4 )
		{
			deleteObject( path[3], parameters(req) );
		}
		// DELETE /api/objects/$ark/$file
		else if ( path.length == 5 )
		{
			deleteFile( path[3], path[4], parameters(req) );
		}
		else
		{
			error( "Invalid request" );
		}
	}
}
