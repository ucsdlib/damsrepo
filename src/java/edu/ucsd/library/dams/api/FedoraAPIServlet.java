package edu.ucsd.library.dams.api;

// java core api
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// servlet api
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// xslt
import javax.xml.transform.TransformerException;

// logging
import org.apache.log4j.Logger;

// dams
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;

/**
 * Partial implementation of the Fedora REST API, intended to support Hydra
 * front-end applications.
 * @author escowles@ucsd.edu
**/
public class FedoraAPIServlet extends DAMSAPIServlet
{
/*

XML = fedora custom xml
FOX = FOXML
BIN = arbitrary data
TXT = plain text
RDF = RDF/XML

Hydra/ActiveFedora/RubyDora API

Out Method REST URI                                  Impl.
--- ------ --------                                  -----
XML GET    /describe                                 static???
XML GET    /objects                                  solr??? no-op??
XML GET    /objects/[oid]                            objectShow + xsl
FOX GET    /objects/[oid]/export                     objectShow + xsl
FOX GET    /objects/[oid]/objectXML                  objectShow + xsl
XML GET    /objects/[oid]/validate (error message)   ???
XML GET    /objects/[oid]/versions                   objectShow + xsl
XML GET    /objects/[oid]/datastreams                objectShow + xsl
XML GET    /objects/[oid]/datastreams/[fid]          objectShow + select + xsl
XML GET    /objects/[oid]/datastreams/[fid]/history  objectShow + select + xsl
BIN GET    /objects/[oid]/datastreams/[fid]/content  fileShow
RDF GET    /objects/[oid]/relationships              ???
XML POST   /objects/nextPID                          identifierCreate + xsl
TXT POST   /objects/[oid] (pid)                      objectEdit
XML POST   /objects/[oid]/datastreams/[fid]          fileUpload
??? POST   /objects/[oid]/relationships/new          ???
TXT PUT    /objects/[oid] (updated timestamp)        objectEdit
XML PUT    /objects/[oid]/datastreams/[fid]          fileUpload
TXT DELETE /objects/[oid] (timestamp/array)          objectDelete
TXT DELETE /objects/[oid]/datastreams/[fid] (ts/arr) fileDelete
??? DELETE /objects/[oid]/relationships              ???


Hydra critical path: create/read/update/delete

Out Method REST URI                                  Impl.
--- ------ --------                                  -----
XML GET    /objects/[oid]                            objectShow + xsl
XML GET    /objects/[oid]/datastreams                objectShow + xsl
XML GET    /objects/[oid]/datastreams/[fid]          objectShow + select + xsl
BIN GET    /objects/[oid]/datastreams/[fid]/content  fileShow
XML POST   /objects/nextPID                          identifierCreate + xsl
TXT POST   /objects/[oid] (pid)                      objectEdit
XML POST   /objects/[oid]/datastreams/[fid]          fileUpload
XML PUT    /objects/[oid]/datastreams/[fid]          fileUpload
TXT DELETE /objects/[oid] (timestamp/array)          objectDelete
TXT DELETE /objects/[oid]/datastreams/[fid] (ts/arr) fileDelete
*/

	// logging
	private static Logger log = Logger.getLogger(FedoraAPIServlet.class);

	/**
	 * HTTP GET methods to retrieve data without changing state.
	**/
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;

		try
		{
			String[] path = path( req );

			// GET /objects/[oid]
			// STATUS: WORKING
			if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				es = events(req);
				outputTransform(
					path[2], null, null, "fedora-object-profile.xsl",
					"text/xml", ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams
			// STATUS: WORKING
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				ts = triplestore(req);
				es = events(req);
				outputTransform(
					path[2], null, null, "fedora-object-datastreams.xsl",
					"text/xml", ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				ts = triplestore(req);
				es = events(req);
				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					"fedora-datastream-profile.xsl", "text/xml", ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]/content
			// STATUS: WORKING
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content") )
			{
				fileShow( path[2], cmpid(path[4]), fileid(path[4]), req, res );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error 1", ex );
		}
		finally
		{
			cleanup( fs, ts, es );
		}
	}

	/**
	 * HTTP POST methods to create new resources.
	**/
	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;

		try
		{
			String[] path = path( req );

			// POST /objects/nextPID
			// STATUS: WORKING
			if ( path.length == 3 && path[1].equals("objects")
				&& path[2].equals("nextPID") )
			{
				String name = getParamString( req, "namespace", minterDefault );
				int count = getParamInt( req, "numPIDs", 1 );
				Map info = identifierCreate( name, count );

				// transform output to fedora format
				String xml = toXMLString( info );
				String content = xslt( xml, "fedora-nextPID.xsl", null, null );
				output( res.SC_OK, content, "text/xml", res );
			}
			// POST /objects/[oid]
			// STATUS: empty: WORKING, file: WORKING
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				InputBundle bundle = input( req );
				InputStream in = bundle.getInputStream();
				String adds = null;
				if ( in == null )
				{
					adds = "[{\"subject\":\"" + path[2] + "\","
					+ "\"predicate\":\"rdf:type\","
					+ "\"object\":\"<dams:Object>\"}]";
				}
				ts = triplestore(req);
				es = events(req);
				Map info = objectCreate( path[2], in, adds, ts, es );

				// output id plaintext
				output( res.SC_OK, path[2], "text/plain", res );
			}
			// POST /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				InputBundle bundle = input( req );
				InputStream in = bundle.getInputStream();
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				Map info = fileUpload(
					path[2], cmpid(path[4]), fileid(path[4]),
					false, in, fs, ts, es
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					"fedora-datastream-profile.xsl", "text/xml", ts, es, res
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error 2", ex );
		}
		finally
		{
			cleanup( fs, ts, es );
		}
	}

	/**
	 * HTTP PUT methods to update existing resources.
	**/
	public void doPut( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;

		try
		{
			String[] path = path( req );

			// PUT /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraObjectDS) )
			{
				// update metadata with record
				InputBundle bundle = input(req);
				InputStream in = bundle.getInputStream();
				ts = triplestore(req);
				es = events(req);
				objectUpdate(
					path[2], in, "all", null, null, null, ts, es
				);

				outputTransform(
					path[2], null, null, "fedora-datastream-profile.xsl",
					"text/xml", ts, es, res
				);
			}
			// PUT /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				// upload other data files
				InputBundle bundle = input(req);
				InputStream in = bundle.getInputStream();
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				fileUpload(
					path[2], cmpid(path[4]), fileid(path[4]),
					true, in, fs, ts, es
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					"fedora-datastream-profile.xsl", "text/xml", ts, es, res
				);
			}
			else
			{
				Map err = error( res.SC_BAD_REQUEST, "Invalid request" );
				output( err, req.getParameterMap(), req.getPathInfo(), res );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error 3", ex );
		}
		finally
		{
			cleanup( fs, ts, es );
		}
	}

	/**
	 * HTTP DELETE methods to delete resources.
	**/
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;
		TripleStore es = null;
		Map info = null;

		try
		{
			String[] path = path( req );
			// DELETE /objects/[oid]
			// STATUS: WORKING
			if ( path.length == 3 && path[1].equals("objects") )
			{
				// delete object
				ts = triplestore(req);
				es = events(req);
				info = objectDelete( path[2], ts, es );

				outputTransform(
					path[2], null, null, "fedora-datastream-delete.xsl",
					"text/plain", ts, es, res
				);
			}
			// DELETE /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				// delete file
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				info = fileDelete(
					path[2], cmpid(path[4]), fileid(path[4]), fs, ts, es
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					"fedora-datastream-delete.xsl", "text/plain", ts, es, res
				);
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
			log.warn( "Error 4", ex );
		}
		finally
		{
			cleanup( fs, ts, es );
		}
	}

	private void outputTransform( String objid, String cmpid, String fileid,
		String xsl, String contentType, TripleStore ts, TripleStore es,
		HttpServletResponse res )
		throws TripleStoreException, TransformerException
	{
		// get object metadata
		String rdfxml = null;
		Map info = objectShow( objid, ts, es );
		if ( info.get("obj") != null )
		{
			DAMSObject obj = (DAMSObject)info.get("obj");
			rdfxml = obj.getRDFXML(true);
		}

		// if rdfxml is null, throw an error
		if ( rdfxml == null )
		{
			output(
				res.SC_INTERNAL_SERVER_ERROR,
				"Error retrieving object '" + objid + "'", "text/plain", res
			);
			return;
		}

		// output expected XML
		Map<String,String[]> params =  new HashMap<String,String[]>();
		params.put("objid", new String[]{ objid } );
		if ( fileid != null )
		{
			String dsid = dsid( cmpid, fileid );
			params.put("fileid", new String[]{ dsid } );
		}
		else
		{
			params.put("objectDS", new String[]{ fedoraObjectDS } );
			if ( rdfxml != null )
			{
				params.put(
					"objectSize",
					new String[]{ String.valueOf(rdfxml.length()) }
				);
			}
		}
		try
		{
			String content =  xslt( rdfxml, xsl, params, null );
			output( res.SC_OK, content, contentType, res );
		}
		catch ( Exception ex )
		{
			output(
				res.SC_INTERNAL_SERVER_ERROR, "Error: " + ex.toString(),
				"text/plain", res
			);
		}
	}
	private static String cmpid( String s )
	{
		if ( s == null || !s.startsWith("_") ) { return null; }
		int idx = s.indexOf("_",1);
		return (idx > 0) ? s.substring(1,idx) : null;
	}
	private static String fileid( String s )
	{
		if ( s == null || !s.startsWith("_") ) { return null; }
		int idx = s.indexOf("_",1);
		return (idx > 0) ? s.substring(idx+1) : s.substring(1);
	}
	private static String dsid( String cmpid, String fileid )
	{
		return (cmpid != null) ? "/" + cmpid + "/" + fileid : "/" + fileid;
	}
}
