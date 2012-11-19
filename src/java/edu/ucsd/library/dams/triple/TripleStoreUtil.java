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
import java.util.Iterator;
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
import com.hp.hpl.jena.datatypes.BaseDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

import org.apache.log4j.Logger;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.solr.SolrHelper;


/**
 * Utility methods for working with triplestore objects.
 * @author escowles@ucsd.edu
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
	 * Parse a properties file and create a Map of namespace prefixes/names to
	 * URIs.
	**/
	public static Map<String,String> namespaceMap( Properties props )
		throws Exception
	{
		Map<String,String> nsmap = new HashMap<String,String>();
		Enumeration e = props.propertyNames();
		while ( e.hasMoreElements() )
		{
			String key = (String)e.nextElement();
			if ( key.startsWith("ns.") )
			{
				String prefix = key.substring(3);
				prefix = prefix.replaceAll( "\\.", ":" );
				String uri = props.getProperty(key);
				nsmap.put( prefix, uri );
			}
		}

		// load the filestore
		return nsmap;
	}

	/**
	 * Output a set of Statements as RDF/XML.
	**/
	public static void outputRDFXML( StatementIterator iter, Writer writer,
		DAMSObject trans ) throws TripleStoreException
	{
		outputRDF( iter, writer, "RDF/XML-ABBREV", trans );
	}
	/**
	 * Output a set of Statements as NTriples.
	**/
	public static void outputNTriples( StatementIterator iter, Writer writer,
		DAMSObject trans ) throws TripleStoreException
	{
		outputRDF( iter, writer, "N-TRIPLE", trans );
	}
	private static void outputRDF( StatementIterator it, Writer writer,
		String format, DAMSObject trans ) throws TripleStoreException
	{
		Model model = ModelFactory.createDefaultModel();
		try
		{
			// load statements into a jena model
			for ( int i = 0; it.hasNext(); i++ )
			{
				model.add( jenaStatement(model,it.nextStatement(),trans) );
			}

			// register namespace prefixes
			Map<String,String> nsmap = trans.namespaceMap();
			for ( Iterator<String> i2 = nsmap.keySet().iterator(); i2.hasNext();)
			{
				String prefix = i2.next();
				if ( prefix.indexOf(":") == -1 )
				{
					model.setNsPrefix( prefix, nsmap.get(prefix) );
				}
			}

			model.write( writer, format );
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException( ex );
		}
	}
	private static com.hp.hpl.jena.rdf.model.Statement jenaStatement(
		Model m, edu.ucsd.library.dams.triple.Statement stmt, DAMSObject trans )
		throws TripleStoreException
	{
		Resource s = toResource( m, stmt.getSubject(), null );
		Property p = toProperty( m, stmt.getPredicate(), trans );
		RDFNode  o = stmt.hasLiteralObject() ?
			toLiteral(m,stmt.getLiteral()) : toResource(m,stmt.getObject(),trans);
		return m.createStatement(s, p, o);
	}
	private static Resource toResource( Model m, Identifier id,
		DAMSObject trans )
	{
		Resource res = null;
		if ( id != null && id.isBlankNode() )
		{
			res = m.createResource( new AnonId(id.getId()) );
		}
		else if ( id != null )
		{
			if ( trans != null )
			{
				// translate ARKs to URIs
				try
				{
					String uri = trans.arkToPre( id.getId() );
					if ( uri != null ) { id.setId(uri); }
				}
				catch ( Exception ex )
				{
					log.info("Error translating object ARK", ex);
				}
			}
			res = m.createResource( id.getId() );
		}
		return res;
	}
	private static Property toProperty( Model m, Identifier id,
		DAMSObject trans ) throws TripleStoreException
	{
		Property prop = null;
		if ( id != null && !id.isBlankNode() )
		{
			// translate arks to predicate URIs
			String ark = id.getId();
			if ( trans != null )
			{
				String pre = trans.arkToPre( ark );
				if ( pre != null )
				{
					ark = pre;
				}
			}
			prop = m.createProperty( ark );
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
			String lng = s.substring(idx+2);
			return m.createLiteral( val, lng );
		}
		// literal with datatype
		else if ( s != null && !s.endsWith("\"") && s.indexOf("\"^^") > 0 )
		{
			int idx = s.lastIndexOf("\"^^");
			String val = s.substring(1,idx);
			String typ = s.substring(idx+4,s.length()-1);
			try
			{
				return m.createTypedLiteral( val, new BaseDatatype(typ) );
			}
			catch ( NullPointerException ex )
			{
				throw ex;
			}
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
					obj = literalString(s.getLiteral());
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
					parent = sub.getId();
				}
				else
				{
					parent = findParent( parents, sub.getId() );
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
		if ( trans != null )
		{
			String ark = trans.preToArk( pre );
			if ( ark != null )
			{
				pre = ark;
			}
		}
		return Identifier.publicURI( pre );
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
				dst.addLiteralStatement(
					sub, pre, stmt.getLiteral(), id
				);
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
	private static String literalString( Literal literal )
	{
		String lang = literal.getLanguage();
		String type = literal.getDatatypeURI();
		String text = literal.getLexicalForm();
		text = "\"" + text.replaceAll("\\\"","\\\\\\\"") + "\"";
		if ( lang != null && !lang.equals("") )
		{
			text += "@" + lang;
		}
		else if ( type != null && !type.equals("") )
		{
			text += "^^<" + type + ">";
		}
		return text;
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

	/**
	 * Delete all triples that match the specified subject (and optionally
	 * predicate) and recursively delete blank node children.
	 * @param ts TripleStore to delete triples from.
	 * @param sub Subject to delete.
	 * @param pre If null, delete all triples associated with a subject.  If
	 *     not null, only delete triples with this predicate.
	 * @param pre If not null, only delete triples with this object -- useful
	 *     for deleting file or component records, or one out of several
	 *     blank node trees with the same predicate.
	**/
	public static void recursiveDelete( Identifier sub, Identifier pre,
		Identifier obj, TripleStore ts ) throws TripleStoreException
	{
		// iterate through all statements for the object & classify by subject
		Map<String,List<Statement>> map
			= new HashMap<String,List<Statement>>();
		for ( StatementIterator st = ts.sparqlDescribe( sub ); st.hasNext(); )
		{
			Statement s = st.nextStatement();
			List<Statement> children = map.get(s.getSubject().getId());
			if ( children == null )
			{
				children = new ArrayList<Statement>();
			}
			children.add( s );
			map.put( s.getSubject().getId(), children );
		}

		// recursively remove statements
		recursiveDelete( sub, pre, obj, map, ts );

		// if obj is public URI, also delete all children of it
		if ( !obj.isBlankNode() )
		{
			recursiveDelete( obj, null, null, map, ts );
		}
	}

	/**
	 * Recursively delete statements.
	**/
	private static void recursiveDelete( Identifier sub, Identifier pre,
		Identifier obj, Map<String,List<Statement>> map, TripleStore ts )
		throws TripleStoreException
	{
		List<Statement> list = map.get( sub.getId() );
		for ( int i = 0; list != null && i < list.size(); i++ )
		{
			Statement s = list.get(i);
			if ( pre == null || s.getPredicate().equals(pre) )
			{
				if ( obj == null || s.getObject().equals(pre) )
				{
					// remove the statement
					removeStatement( ts, s );

					// if the object is a blank node, remove all child nodes
					if ( !s.hasLiteralObject() )
					{
						Identifier o = s.getObject();
						if ( o.isBlankNode() )
						{
							recursiveDelete( o, null, null, map, ts );
						}
					}
				}
			}
		}
	}
	public static void removeStatement( TripleStore ts, Statement stmt )
		throws TripleStoreException
	{
		if ( stmt.hasLiteralObject() )
		{
			ts.removeLiteralStatements(
				stmt.getSubject(), stmt.getPredicate(), stmt.getLiteral()
			);
		}
		else
		{
			ts.removeStatements(
				stmt.getSubject(), stmt.getPredicate(), stmt.getObject()
			);
		}
	}
}
