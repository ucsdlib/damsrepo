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

// logging
import org.apache.log4j.Logger;

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
	// logging
	private static Logger log = Logger.getLogger(DAMSObject.class);

	private TripleStore ts;
	private TripleStore es;
	private String tsName;
	private Identifier id;
	private Map<String,String> nsmap;
	private String idNS;
	private String owlNS;
	private String rdfNS;
	private String eventPred;

	/**
	 * Main constructor.
	 * @param ts TripleStore to load metadata from.
	 * @param es TripleStore to load events from.
	 * @param id Object identifer (can be full or relative to idNS)
	**/
	public DAMSObject( TripleStore ts, TripleStore es, String id,
		Map<String,String> nsmap )
	{
		this.ts = ts;
		this.es = es;
		this.nsmap = nsmap;
		this.idNS = nsmap.get("damsid");
		this.owlNS = nsmap.get("owl");
		this.rdfNS = nsmap.get("rdf");
		this.eventPred = nsmap.get("dams") + "event";
		String iduri = (id != null && id.startsWith("http")) ? id : idNS + id;
		this.id = Identifier.publicURI(iduri);
	}

	// recursive describe
	List<Statement> slist  = new ArrayList<Statement>();
	Set<String> done       = new HashSet<String>();
	Set<Identifier> todo   = new HashSet<Identifier>();
	Set<Identifier> events = new HashSet<Identifier>();

	/**
	 * Get a list of links for an object.
	**/
	public Set<Statement> getLinks() throws TripleStoreException
	{
		Set<Statement> links = new HashSet<Statement>();
		StatementIterator it = getStatements(false);
		while ( it.hasNext() )
		{
			Statement s = it.nextStatement();
			if ( !s.hasLiteralObject() && !s.getObject().isBlankNode() )
			{
				links.add( s );
			}
		}
		it.close();
		return links;
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

		// initial describe
		StatementIterator it = ts.sparqlDescribe( id );
		done.add( id.getId() );
		try { process( it ); }
		finally { it.close(); }

		// get events from separate triplestore
		if ( es != null && events.size() > 0 )
		{
			// look for outstanding subjects
			StatementIterator it3 = es.sparqlDescribe(events);

			// process the batch of statements
			try { process( it3 ); }
			finally { it3.close(); }
		}

		// recurse over children until no new identifiers are found
		int MAX_RECURSION = 10;
		for ( int i = 0; i < MAX_RECURSION && todo.size() > 0; i++ )
		{
			// describe all objects in the todo set
			StatementIterator it2 = ts.sparqlDescribe(todo);

			// process the batch of statements
			try { process( it2 ); }
			finally { it2.close(); }

			// get events from separate triplestore
			if ( es != null && events.size() > 0 )
			{
				// look for outstanding subjects
				StatementIterator it3 = es.sparqlDescribe(events);
	
				// process the batch of statements
				try { process( it3 ); }
				finally { it3.close(); }
			}
		}

		// output unprocessed statements
		if ( todo.size() > 0 )
		{
			Iterator<Identifier> todoit = todo.iterator();
			while ( todoit.hasNext() )
			{
				Identifier id = todoit.next();
				log.debug( "unprocessed links: " + id.toString() );
			}
		}

		return new StatementListIterator( slist );
	}
	/**
	 * process all statements in an iterator, adding them to the slist, and
	 * checking for any object URIs that are not in the done set and adding
	 * them to the todo set
	**/
	private void process( StatementIterator it ) throws TripleStoreException
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

			// move identifier from todo to done
			Identifier s = stmt.getSubject();
			if ( !s.isBlankNode() )
			{
				todo.remove(s);
				done.add( s.getId() );
			}

			// add child object URIs todo
			if ( !stmt.hasLiteralObject() )
			{
				Identifier o = stmt.getObject();
				if ( !o.isBlankNode() && !done.contains(o.getId()) )
				{
					// don't follow owl predicates
					String p = stmt.getPredicate().getId();
					if ( p.equals(eventPred) )
					{
						events.add(o);
					}
					else if ( !p.equals(rdfNS + "type") && !p.startsWith(owlNS)
						&& o.getId().startsWith(idNS) )
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
