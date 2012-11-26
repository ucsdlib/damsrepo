package edu.ucsd.library.dams.model;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// dams
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;
import edu.ucsd.library.dams.triple.StatementListIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
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
	private Map<String,String> nsmap;
	private String idNS;
	private String owlNS;

	/**
	 * Main constructor.
	 * @param ts TripleStore to load metadata from.
	 * @param id Object identifer (can be full or relative to idNS)
	**/
	public DAMSObject( TripleStore ts, String id, Map<String,String> nsmap )
	{
		this.ts = ts;
		this.nsmap = nsmap;
		this.idNS = nsmap.get("damsid");
		this.owlNS = nsmap.get("owl");
		String iduri = (id != null && id.startsWith("http")) ? id : idNS + id;
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
		int MAX_RECURSION = 10;
		for ( int i = 0; i < MAX_RECURSION && todo.size() > 0; i++ )
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

		// output unprocessed statements
		if ( todo.size() > 0 )
		{
			for ( Iterator<Identifier> todoit = todo.iterator(); it.hasNext(); )
			{
				System.out.println( "todo: " + todoit.next().toString() );
			}
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
			slist.add(stmt);
			if ( !stmt.hasLiteralObject() )
			{
				Identifier o = stmt.getObject();
				if ( !o.isBlankNode() && !done.contains(o) )
				{
					// don't follow owl predicates
					String p = stmt.getPredicate().getId();
					if ( !p.startsWith(owlNS) )
					{
						todo.add(o);
					}
				}
			}
		}
		it.close();
	}

	/**
	 * Get object metadata in NTriples
	**/
	public String getNTriples( boolean recurse ) throws TripleStoreException
	{
		StringBuffer buf = new StringBuffer();
		StatementIterator it = getStatements( recurse );
		StringWriter writer = new StringWriter();
		TripleStoreUtil.outputNTriples( it, writer, nsmap );
		return writer.toString();
	}

	/**
	 * Get object metadata in RDF/XML
	**/
	public String getRDFXML(boolean recurse) throws TripleStoreException
	{
		StringBuffer buf = new StringBuffer();
		StatementIterator it = getStatements( recurse );
		StringWriter writer = new StringWriter();
		TripleStoreUtil.outputRDFXML( it, writer, nsmap );
		return writer.toString();
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
