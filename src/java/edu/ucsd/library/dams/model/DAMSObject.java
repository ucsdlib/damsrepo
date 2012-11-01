package edu.ucsd.library.dams.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;
import edu.ucsd.library.dams.triple.StatementListIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;

/**
 * Model representing a DAMS Object.
 * @author lsitu@ucsd.edu
 * @author escowles@ucsd.edu
**/
public class DAMSObject
{
	private TripleStore ts;
	private String tsName;
	private Identifier id;
	private String idNS;
	private String prNS;
	private String owlSameAs;

	private Map<String,String> preMap = null;
	private Map<String,String> arkMap = null;

	/**
	 * Main constructor.
	 * @param ts TripleStore to load metadata from.
	 * @param id Object identifer (can be full or relative to idNS)
	 * @param idNS Namespace for qualifying bare identifiers
	 * @param prNS Namespace for qualifying bare predicates
	**/
	public DAMSObject( TripleStore ts, String id, String idNS, String prNS,
		String owlSameAs )
	{
		this.ts = ts;
		this.idNS = idNS;
		this.prNS = prNS;
		this.owlSameAs = owlSameAs;
		String iduri = (id != null && id.startsWith("http")) ? id : idNS + id;
		this.id = Identifier.publicURI(iduri);
	}

	public String getIdentifierNamespace() { return idNS; }
	public String getPredicateNamespace() { return prNS; }
	public String getOwlSameAs() { return owlSameAs; }

	private void loadMap() throws TripleStoreException
	{
		if ( preMap == null && arkMap == null )
		{
			preMap = new HashMap<String,String>();
			String sparql = "select ?ark ?pre "
				+ "where { ?ark <" + owlSameAs + "> ?pre }";
			BindingIterator bindings = ts.sparqlSelect(sparql);
			while ( bindings.hasNext() )
			{
				Map<String,String> binding = bindings.nextBinding();
				preMap.put( binding.get("ark"), binding.get("pre") );
				arkMap.put( binding.get("pre"), binding.get("ark") );
			}
			bindings.close();
		}
	}
	public String arkToPre( String ark ) throws TripleStoreException
	{
		loadMap();
		return arkMap.get(ark);
	}
	public String preToArk( String pre ) throws TripleStoreException
	{
		loadMap();
		return preMap.get(pre);
	}

	/**
	 * Get an iterator of all statements about this object.
	 * @param recurse If true, recursively retrieve triples for records that
	 *   this object links to.  If false, just retrieve triples directly
	 *   attached to this subject.
	**/
	private StatementIterator getStatements( boolean recurse )
		throws TripleStoreException
	{
		// simple top-level describe
		if ( ! recurse )
		{
			return ts.sparqlDescribe( id );
		}

		// recursive describe
		List<Statement> slist = new ArrayList<Statement>();
		Set<Identifier> done  = new HashSet<Identifier>();
		Set<Identifier> todo  = new HashSet<Identifier>();

		// initial describe
		StatementIterator it = ts.sparqlDescribe( id );
		done.add( id );
		try { process( it, slist, done, todo ); }
		finally { it.close(); }

		// recurse over children until no new identifiers are found
		while ( todo.size() > 0 )
		{
			// describe all objects in the todo set
			StatementIterator it2 = ts.sparqlDescribe(todo);

			// move ids from todo to done now that we've gotten their triples
			done.addAll( todo );
			todo.clear();

			// process the batch of statements
			try { process( it2, slist, done, todo ); }
			finally { it2.close(); }
		}

		return new StatementListIterator( slist );
	}
	/**
	 * process all statements in an iterator, adding them to the slist, and
	 * checking for any object URIs that are not in the done set and adding
	 * them to the todo set
	**/
	private void process( StatementIterator it, List<Statement> slist,
		Set<Identifier> done, Set<Identifier> todo ) throws TripleStoreException
	{
		/*
			need to limit recursion to prevent Collection/hasObject or
			RelatedResource from taking us to separate objects
			maybe have a set of predicates that don't get followed?
				dams:hasObject, dams:uri (relatedResource)
		*/
		while ( it.hasNext() )
		{
			Statement stmt = it.nextStatement();
			translatePredicate(stmt);
			slist.add(stmt);
			if ( !stmt.hasLiteralObject() )
			{
				Identifier o = stmt.getObject();
				if ( !o.isBlankNode() && !done.contains(o) )
				{
					todo.add(o);
				}
			}
		}
		it.close();
	}
	private void translatePredicate( Statement stmt )
		throws TripleStoreException
	{
		Identifier ark = stmt.getPredicate();
		String pre = arkToPre( ark.getId() );
		if ( pre == null )
		{
			throw new TripleStoreException(
				"Can't find name for " + ark.getId()
			);
		}
		stmt.setPredicate( Identifier.publicURI(pre) );
	}

	/**
	 * Get object metadata in NTriples
	**/
	public String getNTriples( boolean recurse ) throws TripleStoreException
	{
		StringBuffer buf = new StringBuffer();
		for ( StatementIterator it = getStatements( recurse ); it.hasNext(); )
		{
			buf.append( it.nextStatement().toString() + "\n");
		}
		return buf.toString();
	}

	/**
	 * Get object metadata in RDF/XML
	**/
	public String getRDFXML(boolean recurse)
	{
		// XXX
		return null;
	}

	/**
	 * Get object metadata, converted to JSON
	**/
	public String getSolrJsonData()
	{
		// XXX
		return null;
	}

	/**
	 * Get a list of File identifiers associated with this object.
	**/
	public List<String> listFiles()
	{
		// XXX
		return null;
	}

	/**
	 * Collection records
	**/
	public String getSolrCollectionData()
	{
		// XXX
		return null;
	}

	/**
	 * Map of ARK to human-readable predicate URIs
	**/
	public String getSolrNamespaceMap()
	{
		// XXX
		return null;
	}
}
