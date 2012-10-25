package edu.ucsd.library.dams.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

	/**
	 * Main constructor.
	 * @param ts TripleStore to load metadata from.
	 * @param id Object identifer (can be full or relative to idNS)
	 * @param idNS Namespace for qualifying bare identifiers
	 * @param prNS Namespace for qualifying bare predicates
	**/
	public DAMSObject( TripleStore ts, String id, String idNS, String prNS )
	{
		this.ts = ts;
		this.idNS = idNS;
		this.prNS = prNS;
		String iduri = (id.startsWith("http")) ? id : idNS + id;
		this.id = Identifier.publicURI(iduri);
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
			Statement s = it.nextStatement();
			slist.add(s);
			if ( !s.hasLiteralObject() )
			{
				Identifier o = s.getObject();
				if ( !o.isBlankNode() && !done.contains(o) )
				{
					todo.add(o);
				}
			}
		}
		it.close();
	}

	/**
	 * Get object metadata in NTriples
	**/
	public String getNTriples( boolean recurse )
	{
		// XXX
		return null;
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
