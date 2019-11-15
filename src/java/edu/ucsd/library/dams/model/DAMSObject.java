package edu.ucsd.library.dams.model;

import java.io.StringWriter;
import java.io.Writer;
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

// jena
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
//import com.hp.hpl.jena.rdf.model.Statement;// statement ns conflict
import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.RDFWriter;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.datatypes.BaseDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

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
	private String prNS;
	private String owlNS;
	private String rdfNS;
	private String eventPred;
	private List<Identifier> childCollectionPredicates = null;
	private List<Identifier> collectionMemberPredicates = null;
	private Resource rootType = null;


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
		this.prNS = nsmap.get("dams");
		this.owlNS = nsmap.get("owl");
		this.rdfNS = nsmap.get("rdf");
		this.eventPred = nsmap.get("dams") + "event";
		String iduri = (id != null && id.startsWith("http")) ? id : idNS + id;
		this.id = Identifier.publicURI(iduri);

		// setup predicate categories
		childCollectionPredicates = new ArrayList<Identifier>();
		childCollectionPredicates.add(
			Identifier.publicURI( prNS + "hasCollection")
		);
		childCollectionPredicates.add(
			Identifier.publicURI( prNS + "hasProvenanceCollection")
		);
		childCollectionPredicates.add(
			Identifier.publicURI( prNS + "hasPart")
		);

		collectionMemberPredicates = new ArrayList<Identifier>();
		collectionMemberPredicates.add(
			Identifier.publicURI( prNS + "assembledCollection")
		);
		collectionMemberPredicates.add(
			Identifier.publicURI( prNS + "provenanceCollection")
		);
		collectionMemberPredicates.add(
			Identifier.publicURI( prNS + "provenanceCollectionPart")
		);
	}

	// recursive describe
	List<Statement> slist = new ArrayList<Statement>();
	Set<String> done	 = new HashSet<String>();
	Set<Identifier> todo = new HashSet<Identifier>();
	Set<Identifier> events = new HashSet<Identifier>();

	/**
	 * Get a list of links for an object.
	**/
	public Set<Statement> getLinks() throws TripleStoreException
	{
		Identifier hasModel = Identifier.publicURI(prNS + "hasModel");
		Set<Statement> links = new HashSet<Statement>();
		StatementIterator it = getStatements(false);
		while ( it.hasNext() )
		{
			Statement s = it.nextStatement();
			if ( !s.hasLiteralObject() && !s.getObject().isBlankNode()
				&& s.getObject().getId().indexOf(idNS) != -1 )
			{
				log.warn("found link: " + s.getPredicate() +" "+ s.getObject());
				links.add( s );
			}
			else if ( s.getLiteral() != null
				&& s.getLiteral().indexOf("info:fedora/afmodel") != -1 )
			{
				log.warn("found model: " + s.getLiteral());
				String model = s.getLiteral();
				if ( model.startsWith("\"") && model.endsWith("\"") )
				{
					model = model.substring(1,model.length()-1);
				}
				links.add( new Statement( id, hasModel, model, id ) );
			}
		}
		it.close();
		return links;
	}

	/**
	 * Get a list of fedora models for an object.
	**/
	public Set<String> getModels() throws TripleStoreException
	{
		Set<String> models = new HashSet<String>();
		StatementIterator it = getStatements(false);
		while ( it.hasNext() )
		{
			Statement s = it.nextStatement();
			if ( s.getLiteral() != null
				&& s.getLiteral().indexOf("info:fedora/afmodel") != -1 )
			{
				String model = s.getLiteral();
				if ( model.startsWith("\"") && model.endsWith("\"") )
				{
					model = model.substring(1,model.length()-1);
				}
				models.add( model );
			}
		}
		it.close();
		return models;
	}

	/**
	 * Get an iterator of all statements about this object.
	 * @param recurse If true, recursively retrieve triples for records that
	 * this object links to. If false, just retrieve triples directly
	 * attached to this subject.
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
				
				// describe all records in the todo set for events 
				if (todo.size() > 0) {
					it3 = es.sparqlDescribe(todo);

					// process the batch of statements
					try { process( it3 ); }
					finally { it3.close(); }
				}
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
			log.debug("process: " + stmt.toString() );
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
					// don't follow owl predicates, rdf:type, external URIs,
					// or upward collection links
					Identifier p = stmt.getPredicate();
					if ( p.getId().equals(eventPred) )
					{
						events.add(o);
					}
					else if ( allowRecursion(p) && o.getId().startsWith(idNS) )
					{
						todo.add(o);
					}
				}
			}
		}
		it.close();
	}
	private boolean allowRecursion( Identifier predicate )
	{
		if ( predicate.getId().equals(rdfNS + "type")
			|| predicate.getId().startsWith(owlNS)
			|| childCollectionPredicates.contains(predicate) )
		{
			return false;
		}
		else
		{
			return true;
		}
	}

	/**
	 * Get object metadata in NTriples
	**/
	public String getNTriples(boolean recurse) throws TripleStoreException
	{
		return getRDF( recurse, "N-TRIPLE" );
	}

	/**
	 * Get object metadata in Turtle format
	**/
	public String getTurtle(boolean recurse) throws TripleStoreException
	{
		return getRDF( recurse, "TURTLE" );
	}

	/**
	 * Get object metadata in RDF/XML
	**/
	public String getRDFXML(boolean recurse) throws TripleStoreException
	{
		return getRDF( recurse, "RDF/XML-ABBREV" );
	}

	/**
	 * Get object metadata in RDF, in any format supported by Jena.
	 * @param recurse If true, follow all links recursively.
	 * @param format Jena RDF language names, current values are: "RDF/XML",
	 * "RDF/XML-ABBREV", "N-TRIPLE", "TURTLE", (and "TTL") and "N3". The
	 * default value, represented by null is "RDF/XML".
	**/
	public String getRDF( boolean recurse, String format )
		throws TripleStoreException
	{
		StringWriter writer = new StringWriter();
		outputRDF( asModel(recurse), writer, format );
		return writer.toString();
	}

	/**
	 * Get object metadata as a Jena model.
	 * @param recurse If true, follow all links recursively.
	**/
	public Model asModel( boolean recurse ) throws TripleStoreException
	{
		StatementIterator it = getStatements( recurse );
		Model m = ModelFactory.createDefaultModel();
		try
		{
			// load statements into a jena model
			for ( int i = 0; it.hasNext(); i++ )
			{
				m.add( jenaStatement(m,it.nextStatement()) );
			}

			// register namespace prefixes
			for (Iterator<String> i2 = nsmap.keySet().iterator(); i2.hasNext();)
			{
				String prefix = i2.next();
				if ( prefix.indexOf(":") == -1 )
				{
					m.setNsPrefix( prefix, nsmap.get(prefix) );
				}
			}

			// if this is a collection, add dynamic extent note
			Resource sub = toResource( m, id );
			Property rdfType = toProperty(
				m, Identifier.publicURI(rdfNS + "type")
			);
			com.hp.hpl.jena.rdf.model.Statement typeSt
				= m.getProperty(sub,rdfType);
			String recordType = null;
			rootType = null;
			try
			{
				if ( typeSt != null )
				{
					Resource typeResource = (Resource)typeSt.getObject();
					rootType = typeResource;
					recordType = typeResource.toString();
				}
			}
			catch ( Exception ex ) { log.warn("Error determining type",ex); }
			if ( recordType != null && ( recordType.endsWith("Collection")
				|| recordType.endsWith("CollectionPart")) )
			{
				long records = countObjectsInCollection(id,ts);

				String recordsLiteral = null;
				if ( records == 1L )
				{
					recordsLiteral = "1 digital object.";
				}
				else if ( records > 1L )
				{
					recordsLiteral = records + " digital objects.";
				}

				if ( recordsLiteral != null )
				{
					// find any existing extent notes
					com.hp.hpl.jena.rdf.model.Statement extSt = null;
					Property damsNote = m.createProperty(prNS + "note");
					Property damsType = m.createProperty(prNS + "type");
					Property damsNoteClass = m.createProperty(prNS + "Note");
					Property rdfValue = m.createProperty(rdfNS + "value");
					NodeIterator nodeit = m.listObjectsOfProperty(sub,damsNote);
					while ( nodeit.hasNext() )
					{
						String noteType = null;
						com.hp.hpl.jena.rdf.model.Statement valSt = null;

						RDFNode noteNode = nodeit.nextNode();
						StmtIterator stIt = m.listStatements(
							noteNode.asResource(), null, (RDFNode)null
						);
						while ( stIt.hasNext() )
						{
							com.hp.hpl.jena.rdf.model.Statement noteSt
								= stIt.nextStatement();
							if ( noteSt.getPredicate().equals( rdfValue ) )
							{
								valSt = noteSt;
							}
							else if ( noteSt.getPredicate().equals(damsType) )
							{
								noteType = noteSt.getLiteral().getLexicalForm();
							}
						}
						if ( valSt != null && noteType != null
							&& noteType.equals("extent") )
						{
							extSt = valSt;
							log.debug("extSt found: " + extSt.toString());
						}
					}

					if ( extSt != null )
					{
						extSt.changeObject( recordsLiteral );
					}
					else
					{
						// if no existing extent note updated, add new one
						Resource bn = m.createResource( new AnonId() );
						m.add( m.createStatement(sub, damsNote, bn) );
						m.add( m.createStatement(bn, damsType, "extent") );
						m.add( m.createStatement(bn,rdfValue,recordsLiteral) );
						m.add( m.createStatement(bn,rdfType,damsNoteClass) );
					}
				}
			}
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException( ex );
		}
		return m;
	}

	public void outputRDF( Model m, Writer writer, String format )
		throws TripleStoreException
	{
		try
		{
			// serialize RDF
			RDFWriter rdfw = m.getWriter(format);
			if ( rootType != null && format.equals("RDF/XML-ABBREV") )
			{
				// tell jena which type should be the root record
				rdfw.setProperty("prettyTypes", new Resource[]{rootType});
			}
			rdfw.write( m, writer, null );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			throw new TripleStoreException( ex );
		}
	}
	private long countObjectsInCollection(Identifier id, TripleStore ts)
	{
		long records = 0L;
		try
		{
			// find all linked collections
			List<Identifier> linkedCols = new ArrayList<Identifier>();
			linkedCollections( id, linkedCols );

			Set<Identifier> allMembers = new HashSet<Identifier>();
			for ( int i = 0; i < linkedCols.size(); i++ )
			{
				Identifier colid = linkedCols.get(i);
				Set<Identifier> members = memberObjects( colid );
				allMembers.addAll( members );
			}

			// count those objects
			records = allMembers.size();
		}
		catch ( TripleStoreException tex )
		{
			records = 0L;
		}
		return records;
	}
	private Set<Identifier> memberObjects( Identifier colid )
		throws TripleStoreException
	{
		Set<Identifier> members = new HashSet<Identifier>();
		StatementIterator it = ts.listStatements(null,null,colid);
		while ( it.hasNext() )
		{
			Statement s = it.nextStatement();
			if ( collectionMemberPredicates.contains(s.getPredicate()) )
			{
				members.add( s.getSubject() );
			}
		}
		it.close();
		return members;
	}
	private void linkedCollections( Identifier id, List<Identifier> linkedCols )
		throws TripleStoreException
	{
		StatementIterator it = ts.listStatements(id,null,null);
		while ( it.hasNext() )
		{
			Statement s = it.nextStatement();
			if ( childCollectionPredicates.contains(s.getPredicate()) )
			{
				Identifier child = s.getObject();
				if (!linkedCols.contains(child) )
				{
					linkedCols.add(child);
					linkedCollections(child,linkedCols);
				}
			}
		}
		it.close();
	}
	private static com.hp.hpl.jena.rdf.model.Statement jenaStatement(
		Model m, edu.ucsd.library.dams.triple.Statement stmt )
		throws TripleStoreException
	{
		Resource s = toResource( m, stmt.getSubject() );
		Property p = toProperty( m, stmt.getPredicate() );
		RDFNode o = null;
		if ( stmt.hasLiteralObject() )
		{
			o = TripleStoreUtil.toLiteral(m,stmt.getLiteral());
		}
		else
		{
			o = toResource(m,stmt.getObject());
		}
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
		throws TripleStoreException
	{
		Property prop = null;
		if ( id != null && !id.isBlankNode() )
		{
			String ark = id.getId();
			prop = m.createProperty( ark );
		}
		return prop;
	}
}
