package edu.ucsd.library.dams.api;

// java core api
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// servlet api
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

		try
		{
			String[] path = path( req );

			// GET /describe
			if ( path.length == 2 && path[1].equals("describe") )
			{
// XXX: IMPL?? what is this used for? which values do we need?
				Map<String,String[]> params = new HashMap<String,String[]>();
				String tsName = getParamString(req,"ts",tsDefault);
				params.put("q", new String[]{"XXX"} );
				params.put( "ts", new String[]{tsName} );
				params.put( "xsl", new String[]{"solr-fedoraSearch.xsl"} );
				indexSearch( params, req.getPathInfo(), res );
			}
			// GET /objects
			else if ( path.length == 2 && path[1].equals("objects") )
			{
// XXX: IMPL?? do we need this? Object.find() doesn't use this...
//  actual searching done w/solr
				String terms = getParamString(req,"terms",null);
				String query = getParamString(req,"query",null);
				String tsName = getParamString(req,"ts",tsDefault);
				String q = (terms != null) ? terms : parseQuery(query);
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put( "q", new String[]{q} );
				params.put( "ts", new String[]{tsName} );
				params.put( "xsl", new String[]{"solr-fedoraSearch.xsl"} );
				indexSearch( params, req.getPathInfo(), res );
			}
			// GET /objects/[oid]
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				objectTransform(
					path[2], null, false, ts, "object-profile.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/export
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("export") )
			{
				objectTransform(
					path[2], null, false, ts, "object-export.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/objectXML
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("objectXML") )
			{
				objectTransform(
					path[2], null, false, ts, "object-objectXML.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/validate (error message)
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("validate") )
			{
				Map info = objectValidate( path[2], fs, ts );
				String xml = toXMLString(info);
				String content = xslt(
					xml, "object-validate.xsl", null, null // XXX pass params??
				);
				output( res.SC_OK, xml, "text/xml", res );
			}
			// GET /objects/[oid]/versions
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("versions") )
			{
				objectTransform(
					path[2], null, false, ts, "object-versions.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/datastreams
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				objectTransform(
					path[2], null, false, ts, "object-datastreams.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				objectTransform(
					path[2], path[4], false, ts, "datastream-profile.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]/history
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("history") )
			{
				objectTransform(
					path[2], path[4], false, ts, "datastream-history.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]/content
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content") )
			{
				fileShow( path[2], path[4], req, res );
			}
			// GET /objects/[oid]/relationships
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("relationships") )
			{
				objectTransform(
					path[2], null, false, ts, "object-relationships.xsl",
					req.getParameterMap(), req.getPathInfo(), res
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( ex );
		}
		finally
		{
			cleanup( fs, ts );
		}
	}
	/**
	 * HTTP POST methods to create new resources.
	**/
	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;

		try
		{
			String[] path = path( req );
		}
		catch ( Exception ex )
		{
			log.warn( ex );
		}
		finally
		{
			cleanup( fs, ts );
		}
	}
	/**
	 * HTTP PUT methods to update existing resources.
	**/
	public void doPut( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;

		try
		{
			String[] path = path( req );
		}
		catch ( Exception ex )
		{
			log.warn( ex );
		}
		finally
		{
			cleanup( fs, ts );
		}
	}
	/**
	 * HTTP DELETE methods to delete resources.
	**/
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
		FileStore fs = null;
		TripleStore ts = null;

		try
		{
			String[] path = path( req );
		}
		catch ( Exception ex )
		{
			log.warn( ex );
		}
		finally
		{
			cleanup( fs, ts );
		}
	}

	/**
	 * Parse a Fedora query and translate into a solr query.
	**/
	private static String parseQuery( String query )
	{
		// XXX IMPL ???
		return null;
	}
}
