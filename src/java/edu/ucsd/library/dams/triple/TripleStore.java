package edu.ucsd.library.dams.triple;

/**
 * Primary interface for TripleStores.  All general-purposes classes that work
 * with TripleStores should use this interface, not the abstract classes or
 * implementing classes directly.
 * @author escowles
**/
public interface TripleStore
{
    /**************************************************************************/
	/*** Read-Only Triple API *************************************************/
    /**************************************************************************/


    /**
     * Export subjects or statements to a file.
     * @param f File to export to.
     * @param subjectsOnly If true, only list subjects.  If false, export all
     *   statements.
    **/
    public void export( java.io.File f, boolean subjectsOnly )
        throws TripleStoreException;
    /**
     * Export subjects or statements to a file.
     * @param f File to export to.
     * @param subjectsOnly If true, only list subjects.  If false, export all
     *   statements.
	 * @param parent Limit export to objects matching substring.
    **/
    public void export( java.io.File f, boolean subjectsOnly, String parent )
        throws TripleStoreException;

    /**
     * List statements contained in the triplestore, optionally limited by one
     * or more of the parameters (any null parameters will match all values).
     * @param subject Limit to statements about this subject, null matches all.
     * @param predicate Limit to statements containing this predicate, null
     *  matches all.
     * @param object Limit to statements containing this object, null matches
     *  all.
    **/
    public StatementIterator listLiteralStatements( Identifier subject,
        Identifier predicate, String object ) throws TripleStoreException;

    /**
     * List statements contained in the triplestore, optionally limited by one
     * or more of the parameters (any null parameters will match all values).
     * @param subject Limit to statements about this subject, null matches all.
     * @param predicate Limit to statements containing this predicate, null
     *  matches all.
     * @param object Limit to statements containing this object, null matches
     *  all.
    **/
    public StatementIterator listStatements( Identifier subject,
        Identifier predicate, Identifier object ) throws TripleStoreException;

    /**
     * List the subjects described in the triplestore.
    **/
    public SubjectIterator listSubjects() throws TripleStoreException;


    /**************************************************************************/
	/*** Read-Write Triple API ************************************************/
    /**************************************************************************/


	/**
	 * Add a statement to the triplestore, with a literal value object.
	 * @param subject Subject URI or blank node id.
	 * @param predicate Predicate URI.
	 * @param object Literal value.
	**/
	public void addLiteralStatement( Identifier subject, Identifier predicate,
		String object, Identifier parent ) throws TripleStoreException;

	/**
	 * Add a statement to the triplestore.
	 * @param stmt Statement object to add.
	**/
	public void addStatement( Statement stmt, Identifier parent )
		throws TripleStoreException;

	/**
	 * Add a statement to the triplestore, with a URI or blank node object.
	 * @param subject Subject URI or blank node.
	 * @param predicate Predicate URI.
	 * @param object Object URI or blank node.
	**/
	public void addStatement( Identifier subject, Identifier predicate,
		Identifier object, Identifier parent ) throws TripleStoreException;

    /**
     * Create a blank node.
    **/
    public Identifier blankNode() throws TripleStoreException;

    /**
     * Create a blank node with the specified id.
     * @param subject The subject for the object which contains this blank node.
     * @param id The blank node identifier.
    **/
    public Identifier blankNode( Identifier subject, String id )
        throws TripleStoreException;

    /**
     * Remove multiple statements, optionally limited by one or more of the
     * parameters (any null parameters will match all values).
     * @param subject Limit to statements about this subject, null matches all.
     * @param predicate Limit to statements containing this predicate, null
     *  matches all.
     * @param object Limit to statements containing this object, null matches
     *  all.
    **/
    public void removeStatements( Identifier subject, Identifier predicate,
        Identifier object ) throws TripleStoreException;

    /**
     * Remove multiple statements, optionally limited by one or more of the
     * parameters (any null parameters will match all values).
     * @param subject Limit to statements about this subject, null matches all.
     * @param predicate Limit to statements containing this predicate, null
     *  matches all.
     * @param object Limit to statements containing this literal value, null
     *  matches all.
    **/
    public void removeLiteralStatements( Identifier subject,
        Identifier predicate, String object ) throws TripleStoreException;

    /**
     * Recursively remove all statements associated with a subject, including
     * blank nodes and their children.
     * @param subject Subject of the object to delete.
    **/
    public void removeObject( Identifier subject ) throws TripleStoreException;

    /**
     * Remove all statements from the triplestore.
    **/
    public void removeAll() throws TripleStoreException;


    /**************************************************************************/
	/*** Utility API **********************************************************/
    /**************************************************************************/


    /**
     * Close the triplestore and release any resources.
    **/
    public void close() throws TripleStoreException;

	/**
	 * Setup new triplestore.
	**/
	public void init() throws TripleStoreException;

    /**
     * Test whether a triplestore instance is connected and ready to use.
    **/
    public boolean isConnected();

    /**
     * Load triples from an N-triples file on disk.
     * @param filename RDF filename.
    **/
    public void loadNTriples( String filename ) throws TripleStoreException;

	/**
	 * Load triples from an RDF XML file on disk.
	 * @param filename RDF filename.
	**/
	public void loadRDFXML( String filename ) throws TripleStoreException;

    /**
     * Perform any maintenance required after performing triplestore updates.
    **/
    public void optimize() throws TripleStoreException;

    /**
     * Get the number of triples in the triplestore.
    **/
    public long size() throws TripleStoreException;


    /**************************************************************************/
	/*** SPARQL Query API *****************************************************/
    /**************************************************************************/


	/**
	 * Perform a SPARQL ASK query.
	 * @param query SPARQL ASK query to execute.
	**/
	public boolean sparqlAsk( String query ) throws TripleStoreException;

	/**
	 * Perform a SPARQL SELECT query and count the results.
	 * @param query SPARQL SELECT query to execute.
	**/
	public long sparqlCount( String query ) throws TripleStoreException;

	/**
	 * Perform a SPARQL DESCRIBE query.
	 * @param query SPARQL DESCRIBE query to execute.
	**/
	public StatementIterator sparqlDescribe( String query )
		throws TripleStoreException;

	/**
	 * Perform a SPARQL SELECT query.
	 * @param query SPARQL SELECT query to execute.
	**/
	public BindingIterator sparqlSelect( String query )
		throws TripleStoreException;
}
