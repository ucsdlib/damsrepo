package edu.ucsd.library.dams.triple;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.Set;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
//import com.hp.hpl.jena.rdf.model.Statement;// statement ns conflict
import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.datatypes.BaseDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;


/**
 * Utility methods for working with triplestore objects.
 * @author escowles@ucsd.edu
**/
public class TripleStoreUtil
{
	private static Logger log = Logger.getLogger( TripleStoreUtil.class );
	private static String ns = "http://libraries.ucsd.edu/ark:/20775/";
	private static Model staticModel = ModelFactory.createDefaultModel();

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
		if ( fprops.getProperty("className") != null )
		{
			return TripleStoreUtil.getTripleStore( fprops );
		}
		else
		{
			return null;
		}
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

	public static Literal toLiteral( Model m, String s )
	{
		if ( m == null ) { m = staticModel; }
		// literal with language tag
		if ( s != null && !s.endsWith("\"") && s.indexOf("\"@") > 0 )
		{
			int idx = s.lastIndexOf("\"@");
			String val = s.substring(1,idx);
			String lng = s.substring(idx+2);
			val = StringEscapeUtils.unescapeJava(val);
			return m.createLiteral( val, lng );
		}
		// literal with datatype
		else if ( s != null && !s.endsWith("\"") && s.indexOf("\"^^") > 0 )
		{
			int idx = s.lastIndexOf("\"^^");
			String val = s.substring(1,idx);
			String typ = s.substring(idx+4,s.length()-1);
			val = StringEscapeUtils.unescapeJava(val);
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
			val = StringEscapeUtils.unescapeJava(val);
			return m.createLiteral( val );
		}
		else
		{
			return null;
		}
	}

	public static Set<String> loadNTriples( InputStream in, boolean deleteFirst,
		TripleStore ts, Map<String,String> nsmap, Set<String> validClasses,
		Set<String> validProperties ) throws TripleStoreException
	{
		return loadRDF( in, deleteFirst, ts, "N-TRIPLE", nsmap, validClasses, validProperties );
	}
	public static Set<String> loadRDFXML( InputStream in, boolean deleteFirst,
		TripleStore ts, Map<String,String> nsmap, Set<String> validClasses,
		Set<String> validProperties ) throws TripleStoreException
	{
		return loadRDF( in, deleteFirst, ts, "RDF/XML", nsmap, validClasses, validProperties );
	}
	private static Set<String> loadRDF( InputStream in, boolean deleteFirst,
		TripleStore ts, String format, Map<String,String> nsmap, Set<String> validClasses,
		Set<String> validProperties ) throws TripleStoreException
	{
		// bnode parent tracking
		Map<String,Identifier> bnodes = new HashMap<String,Identifier>();
		Map<String,String> parents = new HashMap<String,String>();
		ArrayList<Statement> orphans = new ArrayList<Statement>();
		String idNS = nsmap.get("damsid");

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

		// validate the model before loading
		Set<String> errors = Validator.validateModel( model, validClasses, validProperties );
		if ( errors != null && errors.size() > 0 )
		{
			return errors;
		}

		// list and delete subjects in the model
		if ( deleteFirst )
		{
			Resource res = null;
			try
			{
				ResIterator subjects = model.listSubjects();
				while ( subjects.hasNext() )
				{
					res = subjects.nextResource();
					if ( !res.isAnon() )
					{
						Identifier id = toIdentifier(res);
						log.debug("removing subject: " + id.toString() );
						ts.removeObject(id);
					}
				}

			}
			catch ( Exception ex )
			{
				log.warn("error removing id: " + res, ex );
				//throw new TripleStoreException("Error removing RDF data", ex);
			}
		}

		// iterate over all statements and load into triplestore
		try
		{
			StmtIterator it = model.listStatements();
			while ( it.hasNext() )
			{
				com.hp.hpl.jena.rdf.model.Statement s = it.nextStatement();
				Statement stmt = null;
				Identifier sub = toIdentifier( s.getSubject(), bnodes, ts );
				Identifier pre = toIdentifier( s.getPredicate() );
				Identifier objId = null;
				String obj = null;
				if ( s.getObject().isLiteral() )
				{
					obj = literalString(s.getLiteral());
					stmt = new Statement( sub, pre, obj, null );
				}
				else
				{
					objId = toIdentifier( s.getResource(), bnodes, ts );
					stmt = new Statement( sub, pre, objId, null );
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

					// strip file/component part to keep in same graph as object
					Identifier objectSubj = objectSubject(parent,idNS);

					// add triple
					log.debug( "s3: " + stmt.toString() );
					ts.addStatement( stmt, objectSubj );
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
			processOrphans( parents, orphans, idNS, ts );

			// warn about unclaimed orphans?
			for ( int i = 0; i < orphans.size(); i++ )
			{
				log.warn("orphan: " + orphans.get(i).toString());
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

		return null;
	}

	/**
	 * Strip any component or file parts from the parent URI to keep component
	 * and file metadata in the same named graph as the object record.
	**/
	private static Identifier objectSubject( String parent, String idNS )
	{
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
		return Identifier.publicURI( objectSubj );
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
		List<Statement> orphans, String idNS, TripleStore ts )
		throws TripleStoreException
	{
		for ( int i = 0; i < orphans.size(); i++ )
		{
			Statement orphan = orphans.get(i);
			Identifier subject = orphan.getSubject();
			String parent = findParent(parents, orphan.getSubject().toString());
			if ( parent != null && !parent.startsWith("_:") )
			{
				// strip file/component parts to keep in same graph as object
				Identifier objectSubj = objectSubject(parent,idNS);

				// add statement and remove from orphans list
				log.debug( "s2: " + orphan.toString() );
				ts.addStatement( orphan, objectSubj );
				orphans.remove( orphan );
				i--;
			}
		}
	}

	/**
	 * Create identifier for a resource
	**/
	private static Identifier toIdentifier( Resource res )
		throws TripleStoreException
	{
		String id = res.getURI();
		return Identifier.publicURI( id );
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
	public static String parseLiteral( String s )
	{
		Literal lit = toLiteral(null,s);
		return literalString(lit);
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
	 * @param keepPre If not null, do not delete triples with this predicate.
	**/
	public static void recursiveDelete( Identifier parent, Identifier sub,
		Identifier pre, Identifier obj, List<Identifier> keepPres, TripleStore ts )
		throws TripleStoreException
	{
		// iterate through all statements for the object & classify by subject
		Map<String,List<Statement>> map
			= new HashMap<String,List<Statement>>();
		for ( StatementIterator st = ts.sparqlDescribe(parent); st.hasNext(); )
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
		recursiveDelete( sub, pre, obj, keepPres, map, ts );

		// if obj is public URI, delete all children of it *within this object*
		if ( obj != null && !obj.isBlankNode() )
		{
			recursiveDelete( obj, null, null, keepPres, map, ts );
		}
	}

	/**
	 * Recursively delete statements.
	**/
	private static void recursiveDelete( Identifier sub, Identifier pre,
		Identifier obj, List<Identifier> keepPres, Map<String,List<Statement>> map,
		TripleStore ts ) throws TripleStoreException
	{
		List<Statement> list = map.get( sub.getId() );
		for ( int i = 0; list != null && i < list.size(); i++ )
		{
			Statement s = list.get(i);
			Identifier currPre = s.getPredicate();
			if ( (keepPres == null || !containsIdentifier(keepPres, currPre))
				&& (pre == null || currPre.equals(pre) ) )
			{
				if ( obj == null || s.getObject().equals(obj) )
				{
					// remove the statement
					removeStatement( ts, s );

					// if the object is a blank node, remove all child nodes
					if ( !s.hasLiteralObject() )
					{
						Identifier o = s.getObject();
						if ( o.isBlankNode() )
						{
							recursiveDelete( o, null, null, keepPres, map, ts );
						}
					}
				}
			}
		}
	}

	/*
	 * Find a match of an identifier
	 * @param pres
	 * @param pre
	 * @return
	 */
	private static boolean containsIdentifier(List<Identifier> ids, Identifier id) {
		if (ids != null) {
			for (Identifier i : ids) {
				if (i.getId().equals(id.getId())) {
					return true;
				}
			}
		}

		return false;
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
