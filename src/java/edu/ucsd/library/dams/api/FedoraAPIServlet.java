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

// dom4j
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

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
XML GET    /objects                                  solr???
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
				Map<String,String> params = new HashMap<String,String>();
				params.put("q", "XXX");
				params.put("ts",getParamString(req,"ts",tsDefault));
				params.put("xsl", "solr-fedoraRepository.xsl" );
				indexSearch( params, res );
			}
			// GET /objects
			else if ( path.length == 2 && path[1].equals("objects") )
			{
				String terms = getParamString(req,"terms",null);
				String query = getParamString(req,"query",null);
				String q = (terms != null) ? terms : parseQuery(query);
				Map<String,String> params = new HashMap<String,String>();
				params.put("q",q);
				params.put("ts", getParamString(req,"ts",tsDefault));
				params.put( "xsl", "solr-fedoraSearch.xsl" );
				indexSearch( params, res );
			}
			// GET /objects/[oid]
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				objectTransform( path[2], ts, "object-profile.xsl" );
			}
			// GET /objects/[oid]/export
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("export") )
			{
				objectTransform( path[2], ts, "object-export.xsl" );
			}
			// GET /objects/[oid]/objectXML
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("objectXML") )
			{
				objectTransform( path[2], ts, "object-objectXML.xsl" );
			}
			// GET /objects/[oid]/validate (error message)
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("validate") )
			{
				Map info = objectValidate( path[2], fs, ts );
				Document doc = toXML(info);
				transform( doc, "object-validate.xsl", req, res );
			}
			// GET /objects/[oid]/versions
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("versions") )
			{
				objectTransform( path[2], ts, "object-versions.xsl" );
			}
			// GET /objects/[oid]/datastreams
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				objectTransform( path[2], ts, "object-datastreams.xsl" );
			}
			// GET /objects/[oid]/datastreams/[fid]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				objectTransform(
					path[2], path[4], ts, "datastream-profile.xsl"
				);
			}
			// GET /objects/[oid]/datastreams/[fid]/history
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("history") )
			{
				objectTransform(
					path[2], path[4], ts, "datastream-history.xsl"
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
				objectTransform( path[2], ts, "object-relationships.xsl" );
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
		// XXX IMPL
		return null;
	}
}
