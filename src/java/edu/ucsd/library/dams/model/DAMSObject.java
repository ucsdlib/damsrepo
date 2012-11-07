package edu.ucsd.library.dams.model;

import java.io.StringWriter;
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
	private String idNS;
	private String prNS;
	private String owlSameAs;
	private String rdfLabel;
	private Map<String,String> nsmap = null;

	private Map<String,String> preToArkMap = null;
	private Map<String,String> lblToArkMap = null;
	private Map<String,String> arkToPreMap = null;

	/**
	 * Main constructor.
	 * @param ts TripleStore to load metadata from.
	 * @param id Object identifer (can be full or relative to idNS)
	 * @param nsmap Map from prefixes/names to URIs.
	**/
	public DAMSObject( TripleStore ts, String id, Map<String,String> nsmap )
	{
		this.ts = ts;
		this.nsmap = nsmap;
		this.idNS = nsmap.get("damsid");
		this.prNS = nsmap.get("dams");
		this.owlSameAs = nsmap.get("owl:sameAs");
		this.rdfLabel = nsmap.get("rdf:label");
		String iduri = (id != null && id.startsWith("http")) ? id : idNS + id;
		this.id = Identifier.publicURI(iduri);
	}

	public Map<String,String> namespaceMap() { return nsmap; }
	public String getIdentifierNamespace() { return idNS; }
	//public String getPredicateNamespace() { return prNS; }
	//public String getOwlSameAs() { return owlSameAs; }
	//public String getRdfLabel() { return rdfLabel; }

	private void loadMap() throws TripleStoreException
	{
		if ( preToArkMap == null && arkToPreMap == null && lblToArkMap == null )
		{
			preToArkMap = new HashMap<String,String>();
			lblToArkMap = new HashMap<String,String>();
			arkToPreMap = new HashMap<String,String>();
			String sparql = "select ?ark ?pre "
				+ "where { ?ark <" + owlSameAs + "> ?pre }";
			BindingIterator bindings = ts.sparqlSelect(sparql);
			while ( bindings.hasNext() )
			{
				Map<String,String> binding = bindings.nextBinding();
				String ark = binding.get("ark");
				String pre = binding.get("pre");
				arkToPreMap.put( ark, pre );
				preToArkMap.put( pre, ark );
			}
			bindings.close();

			String lblquery = "select ?ark ?lbl "
				+ "where { ?ark <" + rdfLabel + "> ?lbl }";
			BindingIterator lblBindings = ts.sparqlSelect(lblquery);
			while ( lblBindings.hasNext() )
			{
				Map<String,String> binding = lblBindings.nextBinding();
				String ark = binding.get("ark");
				String lbl = binding.get("lbl");
				try { lbl = lbl.substring(1,lbl.length()-1); }
				catch ( Exception ex ) {}
				lblToArkMap.put( lbl, ark );
			}
			lblBindings.close();
		}
	}
	public String arkToPre( String ark ) throws TripleStoreException
	{
		loadMap();
		return arkToPreMap.get(ark);
	}
	public String preToArk( String pre ) throws TripleStoreException
	{
		loadMap();
		return preToArkMap.get(pre);
	}
	public String lblToArk( String lbl ) throws TripleStoreException
	{
		loadMap();
		return lblToArkMap.get(lbl);
	}
	public Map<String,String> predicateMap() throws TripleStoreException
	{
		loadMap();
		return arkToPreMap;
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
		StatementIterator it = getStatements( recurse );
		StringWriter writer = new StringWriter();
		TripleStoreUtil.outputNTriples( it, writer, this );
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
		TripleStoreUtil.outputRDFXML( it, writer, this );
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
