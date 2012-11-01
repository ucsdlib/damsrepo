package edu.ucsd.library.dams.triple;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
//import com.hp.hpl.jena.rdf.model.Statement;// statement ns conflict
import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

import org.apache.log4j.Logger;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.solr.SolrHelper;


/**
 * Utility methods for working with triplestore objects.
 * @author escowles
**/
public class TripleStoreUtil
{
	private static Logger log = Logger.getLogger( TripleStoreUtil.class );
	private static String ns = "http://libraries.ucsd.edu/ark:/20775/";

	/**
	 * Get an instance of a triplestore class.
	 * @param props Properties object holding parameters to initialize the 
	 *  triplestore.  Must contain at least the "className" property to
	 *  determine which triplestore class should be created.  Other parameters
	 *  required will depend on the triplestore implementation.
	**/
	public static TripleStore getTripleStore( Properties props )
		throws ClassNotFoundException, IllegalAccessException,
			InstantiationException, InvocationTargetException,
			NoSuchMethodException
	{
		String className = props.getProperty("className");
		Class c = Class.forName( className );
		Constructor constructor = c.getConstructor(new Properties().getClass());
		return (TripleStore)constructor.newInstance( props );
	}

	/**
	 * Get an instance of a triplestore calss.
	 * @param props Properties object holding parameters to initialize the 
	 *  triplestore.
	 * @param name Prefix for the properties in the form "ts.[name]."
	**/
	public static TripleStore getTripleStore( Properties props, String name )
		throws Exception
	{
		// copy properties for the named filestore to a new properties file
		Properties fprops = new Properties();
		Enumeration e = props.propertyNames();
		String prefix = "ts." + name + ".";
		while ( e.hasMoreElements() )
		{
			String key = (String)e.nextElement();
			if ( key.startsWith(prefix) )
			{
				fprops.put(
					key.substring(prefix.length()), props.getProperty(key)
				);
			}
			else if ( key.startsWith("ns.") )
			{
				fprops.put( key, props.getProperty(key));
			}
		}

		// load the filestore
		return TripleStoreUtil.getTripleStore( fprops );
	}

	/**
	 * Output a set of Statements as RDF/XML.
	**/
	public static void outputRDFXML( StatementIterator iter, Writer writer )
		throws TripleStoreException
	{
		outputRDF( iter, writer, "RDF/XML" );
	}
	/**
	 * Output a set of Statements as NTriples.
	**/
	public static void outputNTriples( StatementIterator iter, Writer writer )
		throws TripleStoreException
	{
		outputRDF( iter, writer, "N-TRIPLE" );
	}
	private static void outputRDF( StatementIterator it, Writer writer,
		String format ) throws TripleStoreException
	{
		Model model = ModelFactory.createDefaultModel();
		try
		{
			// load statements into a jena model
			for ( int i = 0; it.hasNext(); i++ )
			{
				model.add( jenaStatement(model,it.nextStatement()) );
			}
			model.write( writer, format );
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException( ex );
		}
	}
	private static com.hp.hpl.jena.rdf.model.Statement jenaStatement(
		Model m, edu.ucsd.library.dams.triple.Statement stmt )
	{
		Resource s = toResource( m, stmt.getSubject() );
		Property p = toProperty( m, stmt.getPredicate() );
		RDFNode  o = stmt.hasLiteralObject() ?
			toLiteral(m,stmt.getLiteral()) : toResource(m,stmt.getObject());
		return m.createStatement(s, p, o);
	}
	private static Resource toResource( Model m, Identifier id )
	{
		Resource res = null;
		if ( id != null && id.isBlankNode() )
		{
			res = m.createResource( new AnonId(id.getId()) );
		}
		else if ( id != null )
		{
			res = m.createResource( id.getId() );
		}
		return res;
	}
	private static Property toProperty( Model m, Identifier id )
	{
		Property prop = null;
		if ( id != null && !id.isBlankNode() )
		{
			prop = m.createProperty( id.getId() );
		}
		return prop;
	}
	private static Literal toLiteral( Model m, String s )
	{
		// literal with language tag
		if ( s != null && !s.endsWith("\"") && s.indexOf("\"@") > 0 )
		{
			int idx = s.lastIndexOf("\"@");
			String val = s.substring(1,idx);
			String lng = s.substring(idx+1);
			return m.createLiteral( val, lng );
		}
		// literal with datatype
		else if ( s != null && !s.endsWith("\"") && s.indexOf("\"^^") > 0 )
		{
			int idx = s.lastIndexOf("\"^^");
			String val = s.substring(1,idx);
			String typ = s.substring(idx+3);
			return m.createTypedLiteral( val, new XSDDatatype(typ) );
		}
		// plain literal
		else if ( s != null )
		{
			String val = s.substring(1,s.length()-1);
			return m.createLiteral( val );
		}
		else
		{
			return null;
		}
	}

	public static void loadNTriples( InputStream in, TripleStore ts,
		DAMSObject trans ) throws TripleStoreException
	{
		loadRDF( in, ts, trans, "N-TRIPLE" );
	}
	public static void loadRDFXML( InputStream in, TripleStore ts,
		DAMSObject trans ) throws TripleStoreException
	{
		loadRDF( in, ts, trans, "RDF/XML" );
	}
	private static void loadRDF( InputStream in, TripleStore ts,
		DAMSObject trans, String format ) throws TripleStoreException
	{
		// bnode parent tracking
		Map<String,Identifier> bnodes = new HashMap<String,Identifier>();
		Map<String,String> parents = new HashMap<String,String>();
		ArrayList<Statement> orphans = new ArrayList<Statement>();

		Model model = ModelFactory.createDefaultModel();
		try
		{
			// read file into jena model
			model.read( in, null, format );
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException("Error reading RDF data", ex);
		}

		// iterate over all statements and load into triplestore
		try
		{
			String idNS = trans.getIdentifierNamespace();
			StmtIterator it = model.listStatements();
			while ( it.hasNext() )
			{
				com.hp.hpl.jena.rdf.model.Statement s = it.nextStatement();
				Statement stmt = null;
				Identifier sub = toIdentifier( s.getSubject(), bnodes, ts );
				Identifier pre = toIdentifier( s.getPredicate(), trans );
				Identifier objId = null;
				String obj = null;
				if ( s.getObject().isLiteral() )
				{
					obj = s.getLiteral().toString(); // check type & lang handling
					stmt = new Statement( sub, pre, obj );
				}
				else
				{
					objId = toIdentifier( s.getResource(), bnodes, ts );
					stmt = new Statement( sub, pre, objId );
				}

				// find blank node parent
				String parent = null;
				if ( !sub.isBlankNode() )
				{
					parent = sub.toString();
				}
				else
				{
					parent = findParent( parents, sub.toString() );
				}

				// add statement if parent is known
				if ( parent != null )
				{
					// parent in cache

					// strip file and component parts of parents to keep
					// them in the same graph as main object
					String objectSubj = parent;
					if ( idNS != null && objectSubj.startsWith(idNS) )
					{
						String id = objectSubj.substring(idNS.length());
						if ( id.indexOf("/") > 0 )
						{
							id = id.substring(0,id.indexOf("/"));
						}
						else if ( id.indexOf("-") > 0 )
						{
							id = id.substring(0,id.indexOf("-"));
						}
						objectSubj = idNS + id;
					}

					// add triple
					ts.addStatement( stmt, toIdentifier(objectSubj) );
				}
				else
				{
					// haven't seen parent yet, try later
					orphans.add( stmt );
					System.out.println("orphans: " + orphans.size());
				}

				// add parent/child link to parent map
				if ( objId != null && objId.isBlankNode() )
				{
					if ( parent != null && !parent.equals( sub ) )
					{
						// make finding deeply-nested parents more efficient
						// by putting ultimate parent
						parents.put( objId.toString(), parent );
					}
					else
					{
						parents.put( objId.toString(), sub.toString() );
					}
				}

			}

			// process orphans
			processOrphans( parents, orphans, ts );

			// warn about unclaimed orphans?
			for ( int i = 0; i < orphans.size(); i++ )
			{
				System.err.println("orphan: " + orphans.get(i).toString());
			}
			if ( bnodes.size() > 0 )
			{
				bnodes.clear();
			}
		}
		catch ( TripleStoreException ex ) { throw ex; }
		catch ( Exception ex )
		{
			throw new TripleStoreException( "Error processing triples", ex );
		}
	}

	/**
	 * Recursively find parents of an object until a public URI parent is found.
	 * @param parents Map of child->parent relationships
	 * @param subject Blank node to find parents of.
	**/
	private static String findParent(Map<String,String> parents, String subject)
	{
		String parent = parents.get( subject );

		// if parent is blank node, recursively find until public URI is found
		while ( parent != null && parent.startsWith("_:") )
		{
			parent = (String)parents.get( parent );
		}

		return parent;
	}
	/**
	 * Find parents for any orphans and add them to the triplestore.
	 * @param parents Map of child->parent relationships
	 * @param orphans List of statements with unknown parents
	**/
	private static void processOrphans( Map<String,String> parents,
		List<Statement> orphans, TripleStore ts ) throws TripleStoreException
	{
		for ( int i = 0; i < orphans.size(); i++ )
		{
			Statement orphan = orphans.get(i);
			Identifier subject = orphan.getSubject();
			String parent = findParent(parents, orphan.getSubject().toString());
			if ( parent != null && !parent.startsWith("_:") )
			{
				ts.addStatement( orphan, toIdentifier(parent) );
				orphans.remove( orphan );
				i--;
			}
		}
	}

	/**
	 * Create identifier for a resource, with pre->ark translation.
	**/
	private static Identifier toIdentifier( Resource res, DAMSObject trans )
		throws TripleStoreException
	{
		String pre = res.getURI();
		String ark = trans.preToArk( pre );
		if ( ark != null )
		{
			return Identifier.publicURI( ark );
		}
		else
		{
			return Identifier.publicURI( pre );
		}
	}

	/**
	 * Create identifier for a resources, with blank node translation.
	**/
	private static Identifier toIdentifier( Resource res,
		Map<String,Identifier> nodemap, TripleStore ts )
		throws TripleStoreException
	{
		// URI or bnode
		if ( res.isAnon() )
		{
			Identifier id = null;
			if ( nodemap != null && ts != null )
			{
				String oldId = res.getId().getLabelString();
				id = nodemap.get( oldId );
				if ( id == null )
				{
					id = ts.blankNode();
					nodemap.put( oldId, id );
				}
			}
			return id;
		}
		else
		{
			return Identifier.publicURI( res.getURI() );
		}
	}

	/**
	 * Create identifier given a string id, with blank node detection.
	**/
	protected static Identifier toIdentifier( String id )
	{
		Identifier idObj = null;
		if ( id.startsWith("_:") )
		{
			idObj = Identifier.blankNode(id.substring(2));
		}
		else
		{
			idObj = Identifier.publicURI(id);
		}
		return idObj;
	}

	/**
	 * Copy an object from one triplestore to another.
	 * @param src Source triplestore.
	 * @param dst Destination triplestore.
	 * @param id Identifier of the object to copy.
	 * @throws TripleStoreException On error reading or writing objects in the
	 *   triplestores.
	**/
	public static int copy( TripleStore src, TripleStore dst, Identifier id )
		throws TripleStoreException
	{
		int triples = 0;
		String q = "DESCRIBE <" + id.getId() + ">";
		StatementIterator it = src.sparqlDescribe( q );
		Map<String,Identifier> bnodes = new HashMap<String,Identifier>();
		while ( it.hasNext() )
		{
			Statement stmt = it.nextStatement();
			Identifier pre = stmt.getPredicate();

			// map subject bnode ids
			Identifier sub = stmt.getSubject();
			if ( sub.isBlankNode() )
			{
				Identifier newObj = bnodes.get(sub.getId());
				if ( newObj == null )
				{
					newObj = dst.blankNode();
					bnodes.put( sub.getId(), newObj );
				}
				sub = newObj;
			}

			if ( stmt.hasLiteralObject() )
			{
				// add statement
				dst.addLiteralStatement( sub, pre, stmt.getLiteral(), id );
			}
			else
			{
				// map object bnode ids
				Identifier obj = stmt.getObject();
				if ( obj.isBlankNode() )
				{
					Identifier newObj = bnodes.get(obj.getId());
					if ( newObj == null )
					{
						newObj = dst.blankNode();
						bnodes.put( obj.getId(), newObj );
					}
					obj = newObj;
				}

				// add statement
				dst.addStatement( sub, pre, obj, id );
			}

			triples++;
		}
		it.close();
		return triples;
	}

	/**
	 * Sync recently updated objects from one triplestore to another.  Records
	 * to update are found by performing a Solr search.  Each record is then
	 * deleted from the destination triplestore, and copied from the source
	 * triplestore.
	 * @param src Source triplestore with updated objects.
	 * @param dst Destination triplestore where objects are copied to.
	 * @param solrBase Solr base URL
	 * @param solrCore Solr core.
	 * @param category Optional category to limit search.
	 * @param category Optional: limit search to objects in this category.
	 * @param minutes Optional: limit search to objects updated within this many
	 *   minutes.
	 * @throws IOException On error searching Solr or parsing the results.
	 * @throws TripleStoreException On error reading or writing objects in the
	 *   triplestores.
	**/
	public static void sync( TripleStore src, TripleStore dst, String solrBase,
		String solrCore, String category, int minutes )
		throws IOException, TripleStoreException
	{
		Date d = null;
		if ( minutes > -1 )
		{
			//entereddate:[2010-07-28 TO *]
			Calendar now = Calendar.getInstance();
			now.add( Calendar.MINUTE, (-1*minutes) );
			d = now.getTime();
		}
		sync( src, dst, solrBase, solrCore, category, d );
	}
	
	/**
	 * Sync recently updated objects from one triplestore to another.  Records
	 * to update are found by performing a Solr search.  Each record is then
	 * deleted from the destination triplestore, and copied from the source
	 * triplestore.
	 * @param src Source triplestore with updated objects.
	 * @param dst Destination triplestore where objects are copied to.
	 * @param solrBase Solr base URL
	 * @param solrCore Solr core.
	 * @param category Optional: limit search to objects in this category.
	 * @param date Optional: limit search to objects updated after this date.
	 * @throws IOException On error searching Solr or parsing the results.
	 * @throws TripleStoreException On error reading or writing objects in the
	 *   triplestores.
	**/
	public static void sync( TripleStore src, TripleStore dst, String solrBase,
		String solrCore, String category, Date date )
		throws IOException, TripleStoreException
	{
		// build solr query
		String q = "";
		if ( category != null && !category.equals("") )
		{
			q = "category:" + category;
		}
		if ( date != null )
		{
			//entereddate:[2010-07-28 TO *]
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm");
			String d = fmt.format( date );
			if ( !q.equals("") )
			{
				q += " AND ";
			}
			q += "entereddate:[" + d + " TO *]";
		}
		if ( q.equals("") )
		{
			q = "*:*";
		}

		// search solr
		List srcSub = null;
		try
		{
			SolrHelper solr = new SolrHelper( solrBase );
			String solrXml = solr.search(
				solrCore, q, -1, 100, null, null, null
			);
			Document solrDoc = DocumentHelper.parseText(solrXml);
			srcSub = solrDoc.selectNodes(
				"/response/result/doc/str[@name='subject']"
			);
		}
		catch ( DocumentException ex )
		{
			throw new IOException( "Error parsing Solr XML",ex );
		}

		// copy objects
		int objects = 0;
		int triples = 0;
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(2);
		nf.setMinimumFractionDigits(2);
		nf.setGroupingUsed(false);
		long start = System.currentTimeMillis();
		for ( int i = 0; srcSub != null && i < srcSub.size(); i++ )
		{
			Node n = (Node)srcSub.get(i);
			String id = n.getText();
			log.debug("syncing: " + id);
			Identifier subj = Identifier.publicURI(ns+id);

			// remove stale object from dst
			dst.removeObject( subj );

			// copy updated object
			triples += TripleStoreUtil.copy( src, dst, subj );

			// report
			objects++;
			id = id.replaceAll(".*/","");
			float dur = (float)(System.currentTimeMillis() - start)/1000;
			float rate = (float)objects/dur;
			float pct = (float)((i+1)*100)/srcSub.size();
			log.debug(
				id + ": " + objects + "/" + nf.format(dur) + " secs ("
				+ nf.format(pct) + "%, " + nf.format(rate) + "/sec)"
			);
		}
	}
}
