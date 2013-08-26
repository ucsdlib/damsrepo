package edu.ucsd.library.dams.api;

// java core api
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.SimpleDateFormat;

// servlet api
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

// xml/xslt
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

// dom4j
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;
import org.dom4j.XPath;
import org.dom4j.io.SAXReader;
import org.dom4j.xpath.DefaultXPath;

// w3c
import org.w3c.dom.NodeList;

// logging
import org.apache.log4j.Logger;

// dams
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.model.Event;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;
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
XML GET    /describe                                 config + xsl
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

	// date format
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	// xslt
	Transformer objectContentTransform;
	Transformer objectProfileTransform;
	Transformer objectDatastreamsTransform;
	Transformer nextPIDTransform;
	Transformer datastreamProfileTransform;
	Transformer datastreamDeleteTransform;
	Transformer systemMetadataTransform;
	Transformer rightsMetadataTransform;
	Transformer linksMetadataTransform;

	private String fulltextPrefix = "fulltext";

	private boolean RECURSIVE_OBJ = false;

    // initialize servlet parameters
    public void init( ServletConfig config ) throws ServletException
    {
		// suppress activemq (hydra will do its own indexing)
		queueEnabled = false;

        // call parent init
        super.init(config);

        String err = config( false );
        if ( err != null )
        {
            log.error( err );
        }
    }
    private String config( boolean reload )
    {
        String err = null;

        // reload parent config
        if ( reload )
        {
            err = super.config();
            if ( err != null ) { return err; }
        }

        // local config
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            objectContentTransform = tf.newTransformer(
                new StreamSource( xslBase + "fedora-object-content.xsl" )
            );
            objectProfileTransform = tf.newTransformer(
                new StreamSource( xslBase + "fedora-object-profile.xsl" )
            );
            objectDatastreamsTransform = tf.newTransformer(
                new StreamSource( xslBase + "fedora-object-datastreams.xsl" )
            );
            nextPIDTransform = tf.newTransformer(
                new StreamSource( xslBase + "fedora-nextPID.xsl" )
            );
            datastreamProfileTransform = tf.newTransformer(
                new StreamSource( xslBase + "fedora-datastream-profile.xsl" )
            );
            datastreamDeleteTransform = tf.newTransformer(
                new StreamSource( xslBase + "fedora-datastream-delete.xsl" )
            );
			systemMetadataTransform = tf.newTransformer(
				new StreamSource( xslBase + "fedora-systemMetadata.xsl" )
			);
			linksMetadataTransform = tf.newTransformer(
				new StreamSource( xslBase + "fedora-linksMetadata.xsl" )
			);
			rightsMetadataTransform = tf.newTransformer(
				new StreamSource( xslBase + "fedora-rightsMetadata.xsl" )
			);
        }
        catch ( Exception ex )
        {
            err = "Error loading stylesheets: " + ex.toString();
        }
        return err;
    }

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

			// GET /describe
			// STATUS: impl
			if ( path.length == 2 && path[1].equals("describe") )
			{
				Map info = systemInfo( req );

				// transform output to fedora format
				String xml = toXMLString( info );
				String content = xslt( xml, "fedora-describe.xsl", null, null );
				output( res.SC_OK, content, "application/xml", res );
			}
			// GET /objects/[oid]
			// STATUS: WORKING
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				es = events(req);
				outputTransform(
					path[2], null, null, true, objectProfileTransform, null,
					"application/xml", res.SC_OK, ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams
			// STATUS: WORKING
			else if ( path.length == 4 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				ts = triplestore(req);
				es = events(req);
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("objectDS",new String[]{fedoraObjectDS});
				String baseURL = req.getScheme() + "://"
					+ req.getServerName() + ":" + req.getServerPort()
					+ req.getContextPath() + req.getServletPath() + "/";
				params.put("baseURL",new String[]{baseURL});
				params.put("fulltextPrefix",new String[]{fulltextPrefix});
				outputTransform(
					path[2], null, null, true, objectDatastreamsTransform,
					params, "application/xml", res.SC_OK, ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				// check if file exists and send 404 if not
				fs = filestore(req);
				if (path[4].equals(fedoraObjectDS)
					|| path[4].equals(fedoraRightsDS)
					|| path[4].equals(fedoraLinksDS)
					|| path[4].equals(fedoraSystemDS)
					|| fs.exists(path[2],cmpid(path[4]),fileid(path[4])) )
				{
					// if the file exists, send profile
					ts = triplestore(req);
					es = events(req);
					Map<String,String[]> params = new HashMap<String,String[]>();
					params.put("dsName",new String[]{path[4]});
					outputTransform(
						path[2], cmpid(path[4]), fileid(path[4]),
						true, datastreamProfileTransform, params,
						"application/xml", res.SC_OK, ts, es, res
					);
				}
				else
				{
					output( res.SC_NOT_FOUND, "File not found: " + req.getPathInfo(), "text/plain", res );
				}
			}
			// GET /objects/[oid]/datastreams/[fedoraObjectDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals( fedoraObjectDS ) )
			{
                ts = triplestore(req);
                es = events(req);
				outputTransform(
					stripPrefix(path[2]), null, null, RECURSIVE_OBJ,
					objectContentTransform, null, "application/xml",
					res.SC_OK, ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams/[fulltextPrefix][dsid]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].startsWith(fulltextPrefix) )
			{
				fs = filestore(req);
				String[] parts = path[4].split("_");
				String cmpid = null;
				String fileid = null;
				if ( parts.length == 2 )
				{
					fileid = parts[1];
				}
				else if ( parts.length == 3 )
				{
					cmpid  = parts[1];
					fileid = parts[2];
				}
				Map info = extractText( path[2], cmpid, fileid, fs );
				String text = (String)info.get("text");
				output( res.SC_OK, text, "text/plain", res );
			}
			// GET /objects/[oid]/datastreams/[fedoraRightsDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals(fedoraRightsDS) )
			{
				ts = triplestore(req);
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("rightsDS", new String[]{} );
				params.put("dsName",new String[]{fedoraRightsDS});
				outputTransform(
					path[2], null, null, true, rightsMetadataTransform, params,
					"application/xml", res.SC_OK, ts, null, res
				);
			}
			// GET /objects/[oid]/datastreams/[fedoraLinksDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals(fedoraLinksDS) )
			{
                ts = triplestore(req);
                es = events(req);
                Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraLinksDS});
                outputTransform(
                    path[2], null, null, RECURSIVE_OBJ, linksMetadataTransform,
					params, "application/xml", res.SC_OK, ts, es, res
                );
			}
			// GET /objects/[oid]/datastreams/[fedoraSystemDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals(fedoraSystemDS) )
			{
                ts = triplestore(req);
                Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraSystemDS});
                outputTransform(
                    path[2], null, null, true, systemMetadataTransform, params,
                    "application/xml", res.SC_OK, ts, null, res
                );
			}
			// GET /objects/[oid]/datastreams/[fid]/content
			// STATUS: WORKING
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content") )
			{
				fileShow(
					stripPrefix(path[2]), cmpid(path[4]), fileid(path[4]),
					req, res
				);
			}
            // GET /system/config
            else if ( path.length == 3 && path[1].equals("system" )
                && path[2].equals("config") )
            {
                String err = config(true);
				if ( err == null )
				{
					output(
						res.SC_OK, "Configuration reloaded", "text/plain", res
					);
				}
				else
				{
					output(
						res.SC_INTERNAL_SERVER_ERROR, err, "text/plain", res
					);
				}
            }
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing GET request", ex );
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
				output( res.SC_OK, content, "application/xml", res );
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
					adds = "[]";
				}
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				String id = stripPrefix(path[2]);

				cacheRemove(id);
				Map info = objectEdit(
					id, true, in, null, adds, null, null, ts, es, fs
				);

				// output id plaintext
				output( res.SC_CREATED, path[2], "text/plain", res );
			}
			// POST /objects/[oid]/datastreams/[fedoraObjectDS]
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
				fs = filestore(req);
				String id = stripPrefix(path[2]);
				cacheRemove(id);
				Identifier id2 = Identifier.publicURI(idNS+id);
				boolean exists = ts.exists(id2);
				InputStream in2 = pruneInput( in, id2.getId() );

				objectEdit(
					id, !exists, in2, "add", null, null, null, ts, es, fs
				);

				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraObjectDS});
				outputTransform(
					path[2], null, null, true, datastreamProfileTransform,
					params, "application/xml", res.SC_CREATED, ts, es, res
				);
			}
			// POST /objects/[oid]/datastreams/[fedoraRightsDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraRightsDS) )
			{
				// ignore XXX: get curator email address from this...
				//InputBundle bundle = input(req);
			}
			// POST /objects/[oid]/datastreams/[fedoraLinksDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraLinksDS) )
			{
				InputBundle bundle = input(req);
				InputStream in = bundle.getInputStream();
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				String id = stripPrefix(path[2]);

				updateModels( id, in, ts, es, fs );

				// send output
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraLinksDS});
				outputTransform(
					path[2], null, null, true, datastreamProfileTransform,
					params, "application/xml", res.SC_CREATED, ts, es, res
				);
			}
			// POST /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				InputBundle bundle = input( req );
				InputStream in = bundle.getInputStream();
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.putAll( bundle.getParams() );
				params.put("dsName",new String[]{path[4]});
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);
				String id = stripPrefix(path[2]);
				cacheRemove(id);

				Map info = fileUpload(
					id, cmpid(path[4]), fileid(path[4]),
					false, in, fs, ts, es, params
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					true, datastreamProfileTransform, params, "application/xml",
					res.SC_CREATED, ts, es, res
				);
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing POST request", ex );
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

			// PUT /objects/[oid]/datastreams/[fedoraObjectDS]
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
				fs = filestore(req);
				String id = stripPrefix(path[2]);
				cacheRemove(id);
				Identifier id2 = createID( id, null, null );
				boolean exists = ts.exists( id2 );
				InputStream in2 = pruneInput( in, id2.getId() );

				objectEdit(
					id, !exists, in2, "all", null, null, null, ts, es, fs
				);

				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraObjectDS});
				outputTransform(
					path[2], null, null, true, datastreamProfileTransform,
					params, "application/xml", res.SC_OK, ts, es, res
				);
			}
			// PUT /objects/[oid]/datastreams/[fedoraRightsDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraRightsDS) )
			{
				// ignore
				//InputBundle bundle = input(req);
			}
			// PUT /objects/[oid]/datastreams/[fedoraLinksDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraLinksDS) )
			{
				InputBundle bundle = input(req);
				InputStream in = bundle.getInputStream();
				ts = triplestore(req);
				es = events(req);
				fs = filestore(req);
				String id = stripPrefix(path[2]);

				updateModels( id, in, ts, es, fs );

				// send output
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraLinksDS});
				outputTransform(
					path[2], null, null, true, datastreamProfileTransform,
					params, "application/xml", res.SC_OK, ts, es, res
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
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.putAll( bundle.getParams() );
				params.put("dsName",new String[]{path[4]});
				fs = filestore(req);
				ts = triplestore(req);
				es = events(req);

				String id = stripPrefix(path[2]);
				cacheRemove(id);
				fileUpload(
					id, cmpid(path[4]), fileid(path[4]),
					true, in, fs, ts, es, params
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					true, datastreamProfileTransform, params, "application/xml",
					res.SC_OK, ts, es, res
				);
			}
			// PUT /objects/[oid]
			// STATUS: WORKING
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				// required for file uploads to work, doesn't need a response
			}
			else
			{
				Map err = error( res.SC_BAD_REQUEST, "Invalid request" );
				output( err, req.getParameterMap(), req.getPathInfo(), res );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing PUT request", ex );
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
				fs = filestore(req);
				String id = stripPrefix(path[2]);
				cacheRemove(id);

				info = objectDelete( id, ts, es, fs );

				outputTransform(
					path[2], null, null, true, datastreamDeleteTransform, null,
					"text/plain", res.SC_NO_CONTENT, ts, es, res
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
				String id = stripPrefix(path[2]);
				cacheRemove(id);
				info = fileDelete(
					id, cmpid(path[4]), fileid(path[4]), fs, ts, es
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					true, datastreamDeleteTransform, null, "text/plain",
					res.SC_NO_CONTENT, ts, es, res
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
			log.warn( "Error processing DELETE request", ex );
		}
		finally
		{
			cleanup( fs, ts, es );
		}
	}

	private void outputTransform( String objid, String cmpid, String fileid,
		boolean export, Transformer xsl, Map<String,String[]> params,
		String contentType, int successCode, TripleStore ts, TripleStore es,
		HttpServletResponse res )
		throws TripleStoreException, TransformerException
	{
/*
		// don't allow empty component or file identifiers
		if (   (cmpid  != null && cmpid.equals(""))
			|| (fileid != null && fileid.equals("")) )
		{
			Map err = error( res.SC_BAD_REQUEST, "Invalid identifier" );
			output( err, params, "", res );
			return;
		}
*/

		// get object metadata
		String rdfxml = null;
		if ( export )
		{
			rdfxml = cacheGet( stripPrefix(objid) );
			if ( rdfxml == null )
			{
				rdfxml = cacheUpdate( stripPrefix(objid), ts, es );
			}
		}
		else
		{
			// Q: check cache & use export if cached?  any impact on xsl?
			Map info = objectShow( stripPrefix(objid), ts, es );
			if ( info.get("obj") != null )
			{
				DAMSObject obj = (DAMSObject)info.get("obj");
				rdfxml = obj.getRDFXML(false);
			}
		}

		// if rdfxml is null, just output the object identifier
		if ( rdfxml == null )
		{
			output( res.SC_NOT_FOUND, objid, "text/plain", res );
			return;
		}

		// output expected XML
		if ( params == null )
		{
			params =  new HashMap<String,String[]>();
		}
		params.put("objid", new String[]{ objid } );
		if ( fileid != null )
		{
			String dsid = dsid( cmpid, fileid );
			params.put("fileid", new String[]{ dsid } );
		}
		else
		{
			if ( rdfxml != null )
			{
				params.put(
					"objectSize",
					new String[]{ String.valueOf(rdfxml.length()) }
				);
			}
		}

		// extract rights
		if ( params.containsKey("rightsDS") )
		{
			try
			{
				params.remove("rightsDS"); // remove dummy param
				Document doc = DocumentHelper.parseText(rdfxml);
				String accessGroup = accessGroup( doc );
				String adminGroup = doc.valueOf(
					"/rdf:RDF/dams:Object//dams:unitGroup"
				);
				if ( !accessGroup.equals(adminGroup) )
				{
					params.put("accessGroup",new String[]{accessGroup});
				}
				params.put("adminGroup", new String[]{adminGroup});
				params.put("superGroup", new String[]{roleSuper});
			}
			catch ( Exception ex )
			{
				log.warn("Error parsing rights metadata", ex);
			}
		}

		// xslt
		try
		{
			String content =  xslt( rdfxml, xsl, params, null );
			output( successCode, content, contentType, res );
		}
		catch ( Exception ex )
		{
			log.warn("Error transforming object", ex );
			output(
				res.SC_INTERNAL_SERVER_ERROR, "Error: " + ex.toString(),
				"text/plain", res
			);
		}
	}

	// determine access group
	private String accessGroup( Document doc )
	{
		String currDate = dateFormat.format( new Date() );

		// lookup unit group code
		String adminGroup = doc.valueOf("/rdf:RDF/dams:Object//dams:unitGroup");

		//0. all collections and units are public
		if ( doc.selectNodes("/rdf:RDF/dams:Object").size() == 0 &&
			(
				doc.selectNodes("/rdf:RDF/dams:Unit").size() > 0
				|| doc.selectNodes("/rdf:RDF/dams:AssembledCollection").size() > 0 
				|| doc.selectNodes("/rdf:RDF/dams:ProvenanceCollection").size() > 0
			)
		)
		{
			return roleDefault;
		}

		//1. restriction[type='display'] and current dates: admin
		List restr = doc.selectNodes(
			"/rdf:RDF/dams:Object//dams:Restriction[dams:type='display']"
		);
		for ( int i = 0; i < restr.size(); i++ )
		{
			Element e = (Element)restr.get(i);
			if ( currentDates( e, currDate ) )
			{
				return adminGroup;
			}
		}

		//2. copyright 'Under copyright -- 1st Party' or 'Public domain': public
		String copyright = doc.valueOf("/rdf:RDF/dams:Object//dams:copyrightStatus");
		if ( copyright != null
			&& (   copyright.equalsIgnoreCase("Under copyright -- 1st Party")
				|| copyright.equalsIgnoreCase("Under copyright--1st Party")
				|| copyright.equalsIgnoreCase("Public domain") )
			)
		{
			return roleDefault;
		}

		//3. permission[type='display'] and current dates: public
		//4. permission[type='localDisplay'] and current dates: local
		HashSet<String> types = new HashSet<String>();
		List perms = doc.selectNodes("/rdf:RDF/dams:Object//dams:Permission");
		for ( int i = 0; i < perms.size(); i++ )
		{
			Element e = (Element)perms.get(i);
			types.add( e.valueOf("dams:type") );
		}
		if ( types.contains("display") ) { return roleDefault; }
		else if ( types.contains("localDisplay") ) { return roleLocal; }

		//5. else: admin
		return adminGroup;
	}

	// check whether a permission/restriction is current
	private static boolean currentDates( Element e, String currDate )
	{
		String begin = e.valueOf("dams:beginDate");
		String end = e.valueOf("dams:endDate");
		if ( (begin == null || begin.trim().equals("") || begin.compareTo(currDate) < 1 ) &&
			(end == null || end.trim().equals("") || end.compareTo(currDate) == 1 ) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	private String stripPrefix( String id )
	{
		if (id != null && id.indexOf(":") > 0 && id.indexOf(":") < id.length())
		{
			return id.substring( id.indexOf(":") + 1 );
		}
		else
		{
			return id;
		}
	}

	/**
	 * Add and remove models from triplestore to match RELS-EXT
	**/
	private void updateModels( String objid, InputStream in, TripleStore ts,
		TripleStore es, FileStore fs ) throws Exception
	{
		cacheRemove(objid);
		Identifier id = createID( objid, null, null );

		// get models for existing objects
		QName rdfRes = new QName("resource",new Namespace("rdf",rdfNS));
		String finfoURI = "info:fedora/fedora-system:def/model#";
		String hasModelURI = finfoURI + "hasModel";
		QName hasModel = new QName( "hasModel", new Namespace("ns0",finfoURI) );
		Set<String> oldModels = new HashSet<String>();
		if ( ts.exists(id) )
		{
			Map info = objectShow( stripPrefix(objid), ts, null );
			if ( info.get("obj") != null )
			{
				DAMSObject obj = (DAMSObject)info.get("obj");
				oldModels = obj.getModels();
			}
		}

		// parse input
		SAXReader parser = new SAXReader();
		Document doc = parser.read(in);
		log.debug("updateModels() doc=" + doc.asXML());

		// find models in input
		Set<String> newModels = new HashSet<String>();
		List linkNodes = doc.selectNodes("/rdf:RDF/rdf:Description/*");
		for ( int i = 0; i < linkNodes.size(); i++ )
		{
			Element e = (Element)linkNodes.get(i);
			String rel = e.getName();
			String uri = e.attributeValue(rdfRes);
			if ( e.getQName().equals(hasModel) )
			{
				if ( !oldModels.contains( uri ) )
				{
					log.debug("newModel: " + uri);
					newModels.add( uri );
				}
			}
			else
			{
				log.debug("Unhandled RELS-EXT link: " + rel + " " + uri);
			}
		}

		// add new models
		if ( newModels.size() > 0 )
		{
			Identifier relP = Identifier.publicURI(prNS + "relatedResource");
			Identifier relT = Identifier.publicURI(prNS + "RelatedResource");
			Identifier rdfT = Identifier.publicURI(rdfNS + "type");
			Identifier damsT = Identifier.publicURI(prNS + "type");
			Identifier damsU = Identifier.publicURI(prNS + "uri");

			boolean success = true;
			String detail = "Add " + newModels.size() + " fedora models";
			String error = null;
			try
			{
				for ( Iterator<String> it = newModels.iterator(); it.hasNext();)
				{	
					String model = it.next();
					Identifier bn1 = ts.blankNode();
					ts.addStatement( id, relP, bn1, id );
					ts.addStatement( bn1, rdfT, relT, id );
					ts.addLiteralStatement( bn1, damsT, "'hydra-afmodel'", id );
					ts.addLiteralStatement( bn1, damsU, "'" + model + "'", id );
				}
			}
			catch ( Exception ex )
			{
				success = false;
				error = ex.toString();
			}
			finally
			{
				// add event
				createEvent(
					ts, es, fs, objid, null, null, Event.RECORD_EDITED,
					success, detail, error
				);
			}
		}
	}
	private InputStream pruneInput( InputStream in, String objURI )
	{
		Document doc = null;
		String xml = null;
		int pruned = 0;
		try
		{
			// parse doc
			SAXReader parser = new SAXReader();
			doc = parser.read(in);

			// fix rdf:about
			Element objElem = (Element)doc.selectSingleNode("/rdf:RDF/*");
			if ( objElem != null )
			{
				QName rdfAbout = new QName("about",new Namespace("rdf",rdfNS));
				Attribute aboutAttrib = objElem.attribute( rdfAbout );
				if ( !aboutAttrib.getValue().equals("objURI") )
				{
					aboutAttrib.setValue( objURI );
				}
			}
			xml = doc.asXML();
		}
		catch ( Exception ex )
		{
			log.warn( "Error pruning input (DOM4J)", ex );
			if ( doc != null ) { log.info("doc: " + doc.asXML());}
		}

		return new ByteArrayInputStream(xml.getBytes());
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
