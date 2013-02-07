package edu.ucsd.library.dams.api;

// java core api
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// servlet api
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

// xslt
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
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

// logging
import org.apache.log4j.Logger;

// dams
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.triple.Identifier;
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

	// xslt
	Transformer objectProfileTransform;
	Transformer objectDatastreamsTransform;
	Transformer nextPIDTransform;
	Transformer datastreamProfileTransform;
	Transformer datastreamDeleteTransform;
	Transformer systemMetadataTransform;
	Transformer rightsMetadataTransform;
	Transformer linksMetadataTransform;

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
				output( res.SC_OK, content, "text/xml", res );
			}
			// GET /objects/[oid]
			// STATUS: WORKING
			else if ( path.length == 3 && path[1].equals("objects") )
			{
				ts = triplestore(req);
				es = events(req);
				outputTransform(
					path[2], null, null, objectProfileTransform, null,
					"text/xml", res.SC_OK, ts, es, res
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
				outputTransform(
					path[2], null, null, objectDatastreamsTransform,
					params, "text/xml", res.SC_OK, ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams/[fid]
			// STATUS: WORKING
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams") )
			{
				ts = triplestore(req);
				es = events(req);
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{path[4]});
				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					datastreamProfileTransform, params, "text/xml",
					res.SC_OK, ts, es, res
				);
			}
			// GET /objects/[oid]/datastreams/[fedoraObjectDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals( fedoraObjectDS ) )
			{
                ts = triplestore(req);
                Map info = objectShow( stripPrefix(path[2]), ts, null );
                if ( info.get("obj") != null )
                {
                    DAMSObject obj = (DAMSObject)info.get("obj");
                    output(
                        obj, false, req.getParameterMap(),
                        req.getPathInfo(), res
                    );
                }
			}
			// GET /objects/[oid]/datastreams/[fedoraRightsDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals(fedoraRightsDS) )
			{
				ts = triplestore(req);
				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("defaultGroup", new String[]{roleDefault} );
				params.put("adminGroup", new String[]{roleAdmin} );
				params.put("dsName",new String[]{fedoraRightsDS});
				outputTransform(
					path[2], null, null, rightsMetadataTransform, params,
					"text/xml", res.SC_OK, ts, null, res
				);
			}
			// GET /objects/[oid]/datastreams/[fedoraLinksDS]/content
			// STATUS: TEST
			else if ( path.length == 6 && path[1].equals("objects")
				&& path[3].equals("datastreams") && path[5].equals("content")
				&& path[4].equals(fedoraLinksDS) )
			{
                ts = triplestore(req);
                Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraLinksDS});
                outputTransform(
                    path[2], null, null, linksMetadataTransform, params,
                    "text/xml", res.SC_OK, ts, null, res
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
                    path[2], null, null, systemMetadataTransform, params,
                    "text/xml", res.SC_OK, ts, null, res
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
					adds = "[]";
				}
				ts = triplestore(req);
				es = events(req);
				Map info = objectCreate(stripPrefix(path[2]), in, adds, ts, es);

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
				Identifier id = Identifier.publicURI(
					idNS + stripPrefix(path[2])
				);
				boolean exists = ts.exists(id);
				InputStream in2 = pruneInput( in, id.getId() );

				objectEdit(
					stripPrefix(path[2]), !exists, in2, "add",
					null, null, null, ts, es
				);

				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraObjectDS});
				outputTransform(
					path[2], null, null, datastreamProfileTransform,
					params, "text/xml", res.SC_CREATED, ts, es, res
				);
			}
			// POST /objects/[oid]/datastreams/[fedoraRightsDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraRightsDS) )
			{
				// ignore XXXX: get curator email address from this...
				InputBundle bundle = input(req);
			}
			// POST /objects/[oid]/datastreams/[fedoraLinksDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraLinksDS) )
			{
				// XXXX: get model link
				InputBundle bundle = input(req);
				InputStream in = bundle.getInputStream();

				Identifier id = createID( stripPrefix(path[2]), null, null );
				String model = getModel( in );

				/* <rdf:RDF>
					<rdf:Description rdf:about="XXXsubXXX">
						<dams:relatedResource>
							<dams:RelatedResource>
								<dams:uri>XXXmodelXXX</dams:uri>
								<dams:type>afmodel</dams:type>
							</dams:RelatedResource>
						</dams:relatedResource>
					</rdf:Description>
				</rdf:RDF> */
				Document doc = DocumentHelper.createDocument();
				Element root = doc.addElement("RDF",rdfNS);
				doc.setRootElement(root);
				Element desc = root.addElement("Description",rdfNS);
				QName rdfAbout = new QName("about",new Namespace("rdf",rdfNS));
				desc.addAttribute( rdfAbout, id.getId() );
				Element relPred = desc.addElement("relatedResource",prNS);
				Element relElem = relPred.addElement("RelatedResource",prNS);
				relElem.addElement("uri",prNS).setText( model );
				relElem.addElement("type",prNS).setText( "hydra-afmodel" );

				// post
				ts = triplestore(req);
				es = events(req);
				boolean exists = ts.exists(id);
				InputStream in2 = new ByteArrayInputStream(
					doc.asXML().getBytes()
				);

				objectEdit(
					stripPrefix(path[2]), !exists, in2, "add",
					null, null, null, ts, es
				);

				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraLinksDS});
				outputTransform(
					path[2], null, null, datastreamProfileTransform,
					params, "text/xml", res.SC_CREATED, ts, es, res
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
				Map info = fileUpload(
					stripPrefix(path[2]), cmpid(path[4]), fileid(path[4]),
					false, in, fs, ts, es, params
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					datastreamProfileTransform, params, "text/xml",
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
				Identifier id = createID( stripPrefix(path[2]), null, null );
				boolean exists = ts.exists(id);
				InputStream in2 = pruneInput( in, id.getId() );

				objectEdit(
					stripPrefix(path[2]), !exists, in2, "all",
					null, null, null, ts, es
				);

				Map<String,String[]> params = new HashMap<String,String[]>();
				params.put("dsName",new String[]{fedoraObjectDS});
				outputTransform(
					path[2], null, null, datastreamProfileTransform,
					params, "text/xml", res.SC_OK, ts, es, res
				);
			}
			// PUT /objects/[oid]/datastreams/[fedoraRightsDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraRightsDS) )
			{
				// ignore
				InputBundle bundle = input(req);
			}
			// PUT /objects/[oid]/datastreams/[fedoraLinksDS]
			else if ( path.length == 5 && path[1].equals("objects")
				&& path[3].equals("datastreams")
				&& path[4].equals(fedoraLinksDS) )
			{
				// ignore
				InputBundle bundle = input(req);
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
				fileUpload(
					stripPrefix(path[2]), cmpid(path[4]), fileid(path[4]),
					true, in, fs, ts, es, params
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					datastreamProfileTransform, params, "text/xml",
					res.SC_OK, ts, es, res
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
				info = objectDelete( stripPrefix(path[2]), ts, es );

				outputTransform(
					path[2], null, null, datastreamDeleteTransform, null,
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
				info = fileDelete(
					stripPrefix(path[2]), cmpid(path[4]), fileid(path[4]),
					fs, ts, es
				);

				outputTransform(
					path[2], cmpid(path[4]), fileid(path[4]),
					datastreamDeleteTransform, null, "text/plain",
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
		Transformer xsl, Map<String,String[]> params, String contentType,
		int successCode, TripleStore ts, TripleStore es,
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
		Map info = objectShow( stripPrefix(objid), ts, es );
		if ( info.get("obj") != null )
		{
			DAMSObject obj = (DAMSObject)info.get("obj");
			rdfxml = obj.getRDFXML(true);
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
		try
		{
			String content =  xslt( rdfxml, xsl, params, null );
			output( successCode, content, contentType, res );
		}
		catch ( Exception ex )
		{
			output(
				res.SC_INTERNAL_SERVER_ERROR, "Error: " + ex.toString(),
				"text/plain", res
			);
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
	private String getModel( InputStream in )
	{
		Document doc = null;
		String model = null;
		try
		{
			// parse doc
			SAXReader parser = new SAXReader();
			doc = parser.read(in);

			// fix rdf:about
			Element modelElem = (Element)doc.selectSingleNode(
				"/rdf:RDF/rdf:Description/ns0:hasModel"
			);
			if ( modelElem != null )
			{
				QName rdfRes = new QName("resource",new Namespace("rdf",rdfNS));
				model = modelElem.attributeValue( rdfRes );
			}
		}
		catch ( Exception ex )
		{
			log.warn("Error extracting model from RELS-EXT", ex );
		}
		return model;
	}
	private InputStream pruneInput( InputStream in, String objURI )
	{
		Document doc = null;
		String xml = null;
		try
		{
			// parse doc
			SAXReader parser = new SAXReader();
			doc = parser.read(in);

			// remove empty rdf:resource links
			XPath xpath1 = new DefaultXPath("//dams:relationship[dams:Relationship/dams:name/@rdf:resource='' and dams:Relationship/dams:role/@rdf:resource='']");
			xpath1.setNamespaceURIs(nsmap);
			XPath xpath2 = new DefaultXPath("//*[@rdf:resource='']");
			xpath2.setNamespaceURIs(nsmap);

			List remove = xpath1.selectNodes(doc);
			List emptyRefs = xpath2.selectNodes(doc);
			remove.addAll( emptyRefs );
			for ( int i = 0; i < remove.size(); i++ )
			{
				Node n = (Node)remove.get(i);
				n.detach();
			}
	
			// fix rdf:about
			Element objElem = (Element)doc.selectSingleNode("/rdf:RDF/*");
			if ( objElem != null )
			{
				QName rdfAbout = new QName("about",new Namespace("rdf",rdfNS));
				Attribute aboutAttrib = objElem.attribute( rdfAbout );
				aboutAttrib.setValue( objURI );
			}
			xml = doc.asXML();
			log.debug("pruned xml: " + xml);
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			if ( doc != null ) { System.err.println("doc: " + doc.asXML());}
		}

		return new ByteArrayInputStream(xml.getBytes());
	}
	
	private static void prune( Document doc, String xpath )
	{
		List matches = doc.selectNodes( xpath );
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
