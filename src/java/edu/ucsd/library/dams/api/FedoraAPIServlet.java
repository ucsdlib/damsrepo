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
 * Servlet implementing the key portions of the Fedora REST API.
 * @author escowles@ucsd.edu
**/
public class FedoraAPIServlet extends APIBase
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

		// GET /describe
		if ( path.length == 2 && path[1].equals("describe") )
		{
			describeRepository( parameters(req) );
		}
		// GET /objects
		else if ( path.length == 2 && path[1].equals("objects") )
		{
			searchRepository( parameters(req) );
		}
		// GET /objects/$pid
		else if ( path.length == 3 && path[1].equals("objects") )
		{
			objectProfile( path[2], parameters(req) );
		}
		// GET /objects/$pid/export
		else if ( path.length == 4 && path[3].equals("export") )
		{
			objectMetadata( path[2], true, parameters(req) );
		}
		// GET /objects/$pid/objectXML
		else if ( path.length == 4 && path[3].equals("objectXML") )
		{
			objectMetadata( path[2], false, parameters(req) );
		}
		// GET /objects/$pid/validate
		else if ( path.length == 4 && path[3].equals("validate") )
		{
			validateObject( path[2], parameters(req) );
		}
		// GET /objects/$pid/versions
		else if ( path.length == 4 && path[3].equals("versions") )
		{
			listVersions( path[2], parameters(req) );
		}
		// GET /objects/$pid/datastreams
		else if ( path.length == 4 && path[3].equals("datastreams") )
		{
			listFiles( path[2], parameters(req) );
		}
		// GET /objects/$pid/datastreams/$dsid
		else if ( path.length == 5 && path[3].equals("datastreams") )
		{
			fileMetadata( path[2], path[4], parameters(req) );
		}
		// GET /objects/$pid/datastreams/$dsid/history
		else if ( path.length == 6 && path[3].equals("datastreams")
			&& path[5].equals("history") )
		{
			fileHistory( path[2], path[4], parameters(req) );
		}
		// GET /objects/$pid/datastreams/$dsid/content
		else if ( path.length == 6 && path[3].equals("datastreams")
			&& path[5].equals("content") )
		{
			getFile( path[2], path[4], parameters(req) );
		}
		// GET /objects/$pid/relationships
		else if ( path.length == 4 && path[3].equals("relationships") )
		{
			listRelationships( path[2], parameters(req) );
		}
		// GET /objects/$pid/methods -- NOT IMPLEMENTED
		// GET /objects/$pid/methods/$sdef/$method -- NOT IMPLEMENTED
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

		// POST /risearch -- NOT IMPLEMENTED
		// POST /objects/nextPID
		if ( path.length == 3 && path[2].equals("nextPID") )
		{
			createIdentifier( params );
		}
		// POST /objects/$pid
		else if ( path.length == 3 )
		{
			createObject( path[1], params, in );
		}
		// POST /objects/$pid/datastreams/$dsid
		else if ( path.length == 5 && path[3].equals("datastreams") )
		{
			createFile( path[1], path[3], params, in );
		}
		// POST /objects/$pid/relationships/new
		else if ( path.length == 5 && path[3].equals("relationships")
			&& path[4].equals("new") )
		{
			createRelationship( path[1], params );
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

		// PUT /objects/$pid
		if ( path.length == 3 )
		{
			updateObject( path[1], params, in );
		}
		// PUT /objects/$pid/datastreams/$sid
		else if ( path.length == 5 && path[3].equals("datastreams") )
		{
			updateFile( path[2], path[4], params, in );
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

		// DELETE /objects/$pid
		if ( path.length == 3 )
		{
			deleteObject( path[2], parameters(req) );
		}
		// DELETE /objects/$pid/datastreams/$sid
		else if ( path.length == 5 && path[3].equals("datastreams") )
		{
			deleteFile( path[2], path[4], parameters(req) );
		}
		// DELETE /objects/$pid/relationships
		else if ( path.length == 4 && path[3].equals("relationships") )
		{
			deleteRelationship( path[2], parameters(req) );
		}
		else
		{
			error( "Invalid request" );
		}
	}
}
