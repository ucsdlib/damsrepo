package edu.ucsd.library.dams.triple.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.UUID;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.apache.commons.lang3.StringEscapeUtils;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.SubjectIterator;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.convertor.SQLConvertor;
import edu.ucsd.library.dams.triple.convertor.STSTableSchema;

/**
 * Basic triplestore using a relational database for storage and transactions.
 * Provides SPARQL-to-SQL translation, a standard SQL implementation, NTriples
 * import, and basic triple operations.
 * @author escowles@ucsd.edu
**/
public class RelationalTripleStore implements TripleStore
{
	protected static Logger log = Logger.getLogger(
		RelationalTripleStore.class
	);
	protected Connection con = null;
	protected PreparedStatement insertStatement = null;
	protected String tableName = null;
	protected String tsName = null;
	protected String idNS = null;
	protected String prNS = null;
	protected String owlSameAs = null;
	protected int added = 0;
	protected int insertCount = 0;
	protected int selectCount = 0;
	protected long start = 0L;
	protected boolean logIngest = false;
	protected boolean logUpdates = true;
	private String columnDef = null;
	private static Pattern spacePattern = Pattern.compile("\\s+");

	/***********************************************************************/
	/*** Constructor Core **************************************************/
	/***********************************************************************/
	public RelationalTripleStore( Properties props )
	{
		try
		{
			columnDef = props.getProperty("columnDef");
			Class c = Class.forName( props.getProperty("driverClass") );
			Driver driver = (Driver)c.newInstance();
			connect( props, driver );
		}
		catch ( Exception ex )
		{
			log.error("Unable to get Driver object", ex);
		}
	}
	protected void connect( Properties props, Driver driver )
	{
		// tablename
		tsName = props.getProperty("tripleStoreName");
		tableName = tsName + "_triples";
		idNS = props.getProperty("ns.identifiers");
		prNS = props.getProperty("ns.predicates");
		owlSameAs = props.getProperty("ns.owlSameAs");

		// connect to db
		String dsName = props.getProperty("dataSource"); // jndi
		String dsURL  = props.getProperty("dataSourceURL"); // direct
		String dsUser = props.getProperty("dataSourceUser");
		String dsPass = props.getProperty("dataSourcePass");
		if ( dsName != null )
		{
			try
			{
				InitialContext ctx = new InitialContext();
				DataSource ds = (DataSource)ctx.lookup(
					"java:comp/env/" + dsName
				);
				if ( ds != null )
				{
					con = ds.getConnection();
				}
			}
			catch ( Exception ex )
			{
				if ( dsURL == null || dsUser == null || dsPass == null )
				{
					// log exception if not enough info for direct connection
					log.error("Unable to get datasource connection", ex);
				}
				else
				{
					// just log status if we can make direct connection
					log.info("Unable to get datasource connection");
				}
				con = null;
			}
		}

		// fallback on direct connection
		if ( con == null && dsURL != null && dsUser != null && dsPass != null )
		{
			try
			{
				DriverManager.registerDriver( driver );
				con = DriverManager.getConnection( dsURL, dsUser, dsPass );
			}
			catch ( Exception ex )
			{
				log.error( "Unable to get direct connection", ex );
			}
		}
	}


	/***********************************************************************/
	/*** SPARQL-to-SQL translation implementations *************************/
	/***********************************************************************/

	public boolean sparqlAsk( String query ) throws TripleStoreException
	{
		long count = sparqlCount( query );
		return (count > 0L);
	}
	public long sparqlCount( String query ) throws TripleStoreException
	{
		SQLConvertor conv = convertSparql( query );
		return sqlCount( conv.getSqlQuery() );
	}
	public StatementIterator sparqlDescribe( String query )
		throws TripleStoreException
	{
		String subj = query;

		// remove sparql wrapper
		int last = subj.length() - 1;
		if ( subj.regionMatches(true,0,"describe <",0,10)
			&& subj.charAt(last) == '>' )
		{
		   	subj = subj.substring(10,last);
		}
		return sqlDescribe( "<" + subj + ">" );
	}
	public StatementIterator sparqlDescribe( Identifier id )
		throws TripleStoreException
	{
		return sqlDescribe( id.toString() );
	}
	public StatementIterator sparqlDescribe( Set<Identifier> ids )
		throws TripleStoreException
	{
		List<String> idlist = new ArrayList<String>();
		for ( Iterator<Identifier> it = ids.iterator(); it.hasNext(); )
		{
			idlist.add( it.next().toString() );
		}
		return sqlDescribe( idlist );
	}
	public BindingIterator sparqlSelect( String query )
		throws TripleStoreException
	{
		SQLConvertor conv = convertSparql( query );
		return sqlSelect( conv.getSqlQuery(), conv.getResultVars() );
	}
	public BindingIterator predicateMap( String ark, String pre )
		throws TripleStoreException
	{
		List<String> fields = new ArrayList<String>();
		fields.add( ark );
		fields.add( pre );
		String query = "select subject as " + ark + ", object as " + pre
			+ " from " + tableName()
			+ " where predicate = '<" + owlSameAs + ">'";
		return sqlSelect( query, fields );
	}
	protected SQLConvertor convertSparql( String sparql ) throws
		TripleStoreException
	{
		try
		{
			STSTableSchema schema = new STSTableSchema(tableName(), null);
			//SQLConvertor convertor = new SQLConvertor(schema, sparql);
			//return convertor.getSqlQuery();
			return new SQLConvertor(schema,sparql);
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException(ex);
		}
	}


	/***********************************************************************/
	/*** RDF Import/Export *************************************************/
	/***********************************************************************/

	public void logIngest( boolean b ) { this.logIngest = b; }

	public void loadRDFXML( String filename ) throws TripleStoreException
	{
		try
		{
			DAMSObject trans = new DAMSObject(
				this, "", idNS, prNS, owlSameAs
			);
			FileInputStream in = new FileInputStream(filename);
			TripleStoreUtil.loadRDFXML( in, this, trans );
		}
		catch ( TripleStoreException ex )
		{
			throw ex;
		}
		catch ( IOException ex )
		{
			throw new TripleStoreException(ex);
		}
	}
	public void loadNTriples( String filename ) throws TripleStoreException
	{
		try
		{
			DAMSObject trans = new DAMSObject(
				this, "", idNS, prNS, owlSameAs
			);
			FileInputStream in = new FileInputStream(filename);
			TripleStoreUtil.loadNTriples( in, this, trans );
		}
		catch ( TripleStoreException ex )
		{
			throw ex;
		}
		catch ( IOException ex )
		{
			throw new TripleStoreException(ex);
		}
	}

	public void export( java.io.File f, boolean subjectsOnly )
		throws TripleStoreException
	{
		export( f, subjectsOnly, null );
	}
	public void export( java.io.File f, boolean subjectsOnly, String parent )
		throws TripleStoreException
	{
		StatementIterator it = listStatements( null, null, null, parent );
		try
		{
			PrintWriter out = new PrintWriter(
				new BufferedWriter( new FileWriter(f) )
			);
			while ( it.hasNext() )
			{
				Statement s = it.nextStatement();
				out.println( s.toString() );
			}
			out.close();
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException(ex);
		}
	}


	/***********************************************************************/
	/*** SQL Implementation ************************************************/
	/***********************************************************************/

	/**
	 * Get the table where triples are stored.
	**/
	protected String tableName() { return tableName; }

	/**
	 * Excecute a prepared statement.
	**/
	protected void update( PreparedStatement pstmt ) throws TripleStoreException
	{
		try
		{
			insertCount++;
			int result = pstmt.executeUpdate();
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException( "Error performing update", ex );
		}
	}
	/**
	 * Excecute a SQL update command.
	**/
	protected void update( String sql ) throws TripleStoreException
	{
		java.sql.Statement stmt = null;
		try
		{
			stmt = con.createStatement();
			int result = stmt.executeUpdate( sql );
			if(logUpdates && result == 0)
			{
				//System.out.println("Failed to delete: sql=" + sql);
			}
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException( "Error performing update", ex );
		}
		finally
		{
			try { stmt.close(); }
			catch ( Exception ex1 )
			{
				throw new TripleStoreException(
					"Error closing statement after update",ex1
				);
			}
		}
	}
	/**
	 * Execute a SQL select command.
	**/
	protected ResultSet select( String sql ) throws TripleStoreException
	{
		ResultSet rs = null;
		java.sql.Statement stmt = null;
		try
		{
			selectCount++;
			log.debug("sql: " + sql);
			stmt = con.createStatement();
			rs = stmt.executeQuery( sql );
		}
		catch ( Exception ex )
		{
			System.err.println("sql: " + sql);
			throw new TripleStoreException(
				"Error performing select, sql: " + sql, ex
			);
		}
		return rs;
	}
	/**
	 * Remove surrounding quotes (""), if any, from around a string.
	**/
	protected static String stripQuotes( String s )
	{
		String s2 = s;
		if ( s2 != null && s2.startsWith("\"") )
		{
			s2 = s2.substring(1);
		}
		if ( s2 != null && s2.endsWith("\"") )
		{
			s2 = s2.substring(0,s2.length()-1);
		}
		return s2;
	}

	/**
	 * Remove angle brackets (<>), if any, from around a string.
	**/
	protected static String stripBrackets( String s )
	{
		// new non-regex stripBracket impl:
		try
		{
			int last = s.length() - 1;
	   		if ( s != null && s.charAt(0) == '<' && s.charAt(last) == '>' )
	   		{
		   		return s.substring(1,last);
	   		}
	  		else
	   		{
		   		return s;
			}
		}
		catch ( RuntimeException ex )
		{
			System.err.println("s: " + s);
			throw ex;
		}
	}

	/**
	 * Escape values for use in insert/delete/update statements.  Handles
	 * special characters (accents, etc.) without munging quotes.
	**/
	protected static String escapeValue( String s )
	{
		if ( s == null ) { return s; }
		String escaped = StringEscapeUtils.escapeJava(s);
		escaped = escaped.replaceAll("\\\\\"","\""); // unescape quotes: \" -> "
		return escaped;
	}
	/**
	 * Escape values for use in insert/delete/update statements.
	**/
	protected static String escapeSql( String s )
	{
		if ( s == null ) { return s; }
		else { return s.replaceAll("'","''"); }
	}
	/**
	 * Convert non-null parameters to WHERE-clause parts.
	**/
	protected String conditions( Identifier subject, Identifier predicate,
		String object )
	{
		String cond = "";
		if ( subject != null )
		{
			cond = "subject = '" + escapeSql(subject.toString()) + "'";
		}

		if ( predicate != null )
		{
			if ( !cond.equals("") ) { cond += " AND "; }
			cond += "predicate = '" + escapeSql(predicate.toString()) + "'";
		}

		if ( object != null )
		{
			if ( !cond.equals("") ) { cond += " AND "; }
			cond += "object = '" + escapeSql(object.toString()) + "'";
		}

		return cond;
	}
	protected static Identifier toIdentifier( String id )
	{
		Identifier idObj = null;
		if ( id.startsWith("_:") )
		{
			idObj = Identifier.blankNode(id.substring(2));
		}
		else
		{
			idObj = Identifier.publicURI(stripBrackets(id));
		}
		return idObj;
	}
	protected static boolean isLiteral( String s )
	{
		if ( s != null && s.startsWith("_:") )
		{
			// blank node
			return false;
		}
		else if (s != null && (s.startsWith("<") && s.indexOf(" ") == -1))
		{
			// URI
			return false;
		}
		else
		{
			return true;
		}
	}
	/**
	 * Select all triples for an object, including blank-node children
	 * recursively.
	 * @param query SQL SELECT query to execute.
	**/
	protected StatementIterator sqlDescribe( String id )
		throws TripleStoreException
	{
		// keep stripBrackets() call to remove any SPARQL lead-in
		String sql = "SELECT * from " + tableName()
			+ " WHERE parent = '" + id + "'";
		ResultSet rs = select(sql);
		return new RelationalStatementIterator(rs);
	}
	/**
	 * Select all triples for a group of objects, including blank-node children
	 * recursively.
	 * @param query SQL SELECT query to execute.
	**/
	protected StatementIterator sqlDescribe( List<String> ids )
		throws TripleStoreException
	{
		// keep stripBrackets() call to remove any SPARQL lead-in
		String sql = "SELECT * from " + tableName() + " WHERE parent in (";
		for ( int i = 0; i < ids.size(); i++ )
		{
			if ( i > 0 ) { sql += ","; }
			sql += "'" + ids.get(i) + "'";
		}
		sql += ")";
		ResultSet rs = select(sql);
		return new RelationalStatementIterator(rs);
	}
	/**
	 * Perform a SQL SELECT query.
	 * @param query SQL SELECT query to execute.
	**/
	protected BindingIterator sqlSelect( String query, List<String> fields )
		throws TripleStoreException
	{
		ResultSet rs = select(query);
		return new RelationalBindingIterator( rs, fields );
	}
	/**
	 * Perform a SQL SELECT query and count the results.
	 * @param query SQL SELECT query to execute.
	**/
	protected long sqlCount( String query ) throws TripleStoreException
	{
		long count = -1L;
		String sql = "select count(*) from (" + query + ")";
		ResultSet rs = select(sql);
		try
		{
			if ( rs.next() )
			{
				count = rs.getLong(1);
			}
			else
			{
				throw new TripleStoreException("Unable to lookup bn id");
			}
		}
		catch ( Exception ex ) { throw new TripleStoreException(ex); }
		finally
		{
			try { rs.close(); }
			catch ( Exception ex1 ) { throw new TripleStoreException(ex1); }
		}
		return count;
	}


	/***********************************************************************/
	/*** Triple API Implementation *****************************************/
	/***********************************************************************/

	public void addStatement( Statement stmt, Identifier parent )
		throws TripleStoreException
	{
		if ( stmt.hasLiteralObject() )
		{
			addLiteralStatement(
				stmt.getSubject(), stmt.getPredicate(), stmt.getLiteral(),
				parent
			);
		}
		else
		{
			addStatement(
				stmt.getSubject(), stmt.getPredicate(), stmt.getObject(),
				parent
			);
		}
	}
	public void addStatement( Identifier subject, Identifier predicate,
		Identifier object, Identifier parent ) throws TripleStoreException
	{
		addLiteralStatement( subject, predicate, object.toString(), parent );
	}
	public void addLiteralStatement( Identifier subject, Identifier predicate,
		String object, Identifier parent ) throws TripleStoreException
	{
		try
		{
			if ( parent == null ) { parent = subject; }
			if ( insertStatement != null && insertCount % 500 == 0 )
			{
				// close and reopen to prevent problems
				insertStatement.close();
				insertStatement = null;
			}
			if ( insertStatement == null )
			{
				String sql = "INSERT into " + tableName()
					+ " (subject,predicate,object,parent)"
					+ " VALUES ( ?, ?, ?, ? )";
				insertStatement = con.prepareStatement(sql);
			}
			insertStatement.clearParameters();
			insertStatement.setString( 1, subject.toString() );
			insertStatement.setString( 2, predicate.toString() );
			String escaped = escapeValue(object);
			insertStatement.setString( 3, escaped );
			insertStatement.setString( 4, parent.toString() );
			update( insertStatement );
			if ( logIngest )
			{
				added++;
				if ( added % 1000 == 0 )
				{
					long dur = System.currentTimeMillis() - start;
					float rate = (float)(added * 1000)/dur;
					log.info("added: " + added + " (" + rate + "/sec)");
				}
			}
		}
		catch ( TripleStoreException ex ) { throw ex; }
		catch ( Exception ex )
		{
			throw new TripleStoreException(ex);
		}
	}
	public Identifier blankNode() throws TripleStoreException
	{
		UUID rnd = UUID.randomUUID();
		String id = Long.toString( rnd.getMostSignificantBits(), 16 );
		if ( id.startsWith("-") ) { id = "x" + id.substring(1); } // strip neg
		while ( id.length() < 17 ) { id += "y"; } // constant length
		return Identifier.blankNode( id );
	}
	public Identifier blankNode( Identifier subject, String id )
		throws TripleStoreException
	{
		return Identifier.blankNode( id, subject.toString() );
	}
	public boolean exists( Identifier subject ) throws TripleStoreException
	{
		String sql = "SELECT * from " + tableName()
			+ " WHERE parent = '<" + subject.getId() + ">'";
		long objCount = sqlCount( sql );
		return objCount > 0L;
	}
	public SubjectIterator listSubjects() throws TripleStoreException
	{
		String sql = "SELECT distinct subject from " + tableName()
			+ " where subject like '<%'";
		ResultSet rs = select(sql);
		return new RelationalSubjectIterator(rs);
	}
	public StatementIterator listStatements( Identifier subject,
		Identifier predicate, Identifier object ) throws TripleStoreException
	{
		return listStatements( subject, predicate, object, null );
	}
	public StatementIterator listStatements( Identifier subject,
		Identifier predicate, Identifier object, String parent )
		throws TripleStoreException
	{
		// generate sql
		String sql = "SELECT id,subject,predicate,object,parent ";
		sql += "FROM " + tableName();
		if ( subject != null || predicate != null || object != null
			|| parent != null )
		{
			sql += " WHERE ";
			if ( parent != null )
			{
				sql += " parent like '%" + parent + "%'";
				if ( subject != null || predicate != null || object != null )
				{
					sql += " AND ";
				}
			}
			String obj = null;
			if ( object != null ) { obj = object.toString(); }
			sql += conditions( subject, predicate, obj );
		}

		ResultSet rs = select(sql);
		return new RelationalStatementIterator(rs);
	}
	public StatementIterator listLiteralStatements( Identifier subject,
		Identifier predicate, String object ) throws TripleStoreException
	{
		// generate sql
		String sql = "SELECT id,subject,predicate,object,parent ";
		sql += "FROM " + tableName();
		if ( subject != null || predicate != null || object != null )
		{
			sql += " WHERE ";
			sql += conditions( subject, predicate, object );
		}

		ResultSet rs = select(sql);
		return new RelationalStatementIterator(rs);
	}

	public void removeStatements( Identifier subject, Identifier predicate,
		Identifier object ) throws TripleStoreException
	{
		if ( object == null )
		{
			removeLiteralStatements( subject, predicate, null );
		}
		else
		{
			removeLiteralStatements( subject, predicate, object.toString() );
		}
	}
	public void removeLiteralStatements( Identifier subject,
		Identifier predicate, String object ) throws TripleStoreException
	{
		// check for all-null conditions
		if ( subject == null && predicate == null && object == null )
		{
			throw new TripleStoreException(
				"Use TripleStore.removeAll() to delete all triples"
			);
		}

		// generate sql
		String sql = "DELETE FROM " + tableName();
		if ( subject != null || predicate != null || object != null )
		{
			sql += " WHERE ";
			sql += conditions( subject, predicate, escapeValue(object) );
		}	
		update( sql );
	}
	/**
	 * Remove a single statement by id.
	 * @param id The statement id.
	**/
	public void removeStatement( long id ) throws TripleStoreException
	{
		String sql = "DELETE FROM " + tableName() + " WHERE id = " + id;
		update( sql );
	}
	public void removeObject( Identifier subject ) throws TripleStoreException
	{
		String sql = "DELETE from " + tableName() + " WHERE parent = '" 
			+ subject.toString() + "'";
		update( sql );
	}
	public void removeAll() throws TripleStoreException
	{
		String sql = "DELETE from " + tableName();
		update( sql );
	}
	public long size() throws TripleStoreException
	{
		long count = -1L;
		String sql = "select count(*) from " + tableName();
		ResultSet rs = select(sql);
		try
		{
			if ( rs.next() )
			{
				count = rs.getLong(1);
			}
			else
			{
				throw new TripleStoreException("Unable to lookup bn id");
			}
		}
		catch ( Exception ex ) { throw new TripleStoreException(ex); }
		finally
		{
			try { rs.close(); }
			catch ( Exception ex1 ) { throw new TripleStoreException(ex1); }
		}
		return count;
	}
	public void close() throws TripleStoreException
	{
		// close pstmts
		if ( insertStatement != null )
		{
			try
			{
				insertStatement.close();
				insertStatement = null;
			}
			catch ( Exception ex )
			{
				log.warn( "Error closing insert statement", ex );
			}
		}

		// close con
		try
		{
			con.close();
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException(ex);
		}
	}
	public String name() { return tsName; }
	public void init() throws TripleStoreException
	{
		String tbl = tsName + "_triples";
		String[] sql = new String[5];
		sql[0] = "create table " + tbl + " (" + columnDef + ")";
		sql[1] = "create index " + tbl + "_sub ON " + tbl + " (subject)";
		sql[2] = "create index " + tbl + "_pre ON " + tbl + " (predicate)";
		sql[3] = "create index " + tbl + "_obj ON " + tbl + " (object)";
		sql[4] = "create index " + tbl + "_par ON " + tbl + " (parent)";
		init( sql );
	}
	protected void init( String[] ddl ) throws TripleStoreException
	{
		try
		{
			// turn off innappropriate "Failed to delete" message
			logUpdates = false; 

			// execute sql updates
			for ( int i = 0; i < ddl.length; i++ )
			{
				if ( ddl[i] != null )
				{
					update( ddl[i] );
				}
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			throw new TripleStoreException(
				"Error creating triplestore: " + tsName, ex
			);
		}
	}
	public boolean isConnected()
	{
		boolean connected = false;
		try
		{
			if ( con != null )
			{
				connected = !con.isClosed();
			}
		}
		catch ( Exception ex )
		{
			log.error("Error checking connection status",ex);
		}
		return connected;
	}
	public void optimize() throws TripleStoreException
	{
		// no-op
	}
}
class RelationalSubjectIterator extends SubjectIterator
{
	private ResultSet rs = null;
	private boolean checkedState = false;
	private boolean checkedValue = false;
	public RelationalSubjectIterator( ResultSet rs )
	{
		this.rs = rs;
	}
	public boolean hasNext()
	{
		if ( checkedState )
		{
			return checkedValue;
		}
		else
		{
			try
			{
				checkedState = true;
				checkedValue = rs.next();
			}
			catch ( Exception ex )
			{
				checkedValue = false;
			}
			return checkedValue;
		}
	}
	public void remove()
	{
		//rs.remove();
	}
	public String nextSubject()
	{
		String subj = null;
		try
		{
			if ( (checkedState && checkedValue) || rs.next() )
			{
				checkedState = false;
				subj = RelationalTripleStore.stripBrackets(
					rs.getString("subject")
				);
			}
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn("Error listing subjects",ex);
		}
		return subj;
	}
	public void close()
	{
		checkedState = true;
		checkedValue = false;
		try
		{
			java.sql.Statement stmt = rs.getStatement();
			rs.close();
			if ( stmt != null ) { stmt.close(); }
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn("Error closing subject iterator",ex);
		}
	}
}
class RelationalStatementIterator extends StatementIterator
{
	private ResultSet rs = null;
	private boolean checkedState = false;
	private boolean checkedValue = false;
	public RelationalStatementIterator( ResultSet rs )
	{
		this.rs = rs;
	}
	public boolean hasNext()
	{
		if ( checkedState )
		{
			return checkedValue;
		}
		else
		{
			try
			{
				checkedState = true;
				checkedValue = rs.next();
			}
			catch ( Exception ex )
			{
				checkedValue = false;
			}
			return checkedValue;
		}
	}
	public void remove()
	{
		//rs.remove();
	}
	public Statement nextStatement()
	{
		Statement stmt = null;
		try
		{
			if ( (checkedState && checkedValue) || rs.next() )
			{
				checkedState = false;
				String sub = rs.getString("subject");
				String pre = rs.getString("predicate");
				String obj = rs.getString("object");
				Identifier subId = RelationalTripleStore.toIdentifier(sub);
				Identifier preId = RelationalTripleStore.toIdentifier(pre);
				boolean lit = RelationalTripleStore.isLiteral(obj);
				if ( lit )
				{
					stmt = new Statement( subId, preId, obj );
				}
				else
				{
					Identifier objId = RelationalTripleStore.toIdentifier(obj);
					stmt = new Statement( subId, preId, objId );
				}

				// add id
				long id  = rs.getLong("id");
				stmt.setId( id );
			}
			else
			{
				stmt = null;
			}
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn( "Error listing statements", ex );
		}
		return stmt;
	}
	public void close()
	{
		checkedState = true;
		checkedValue = false;
		try
		{
			java.sql.Statement stmt = rs.getStatement();
			rs.close();
			if ( stmt != null ) { stmt.close(); }
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn(
				"Error closing statement iterator", ex
			);
		}
	}
}
class RelationalBindingIterator extends BindingIterator
{
	private ResultSet rs = null;
	private ResultSetMetaData md = null;
	private String[] cols = null;
	private String[] names = null;
	private boolean checkedState = false;
	private boolean checkedValue = false;
	public RelationalBindingIterator( ResultSet rs, List<String> fields )
	{
		this.rs = rs;

		try
		{
			this.md = rs.getMetaData();
			cols = new String[md.getColumnCount()];
			names = new String[md.getColumnCount()];
			for ( int i = 0; i < md.getColumnCount(); i++ )
			{
				cols[i] = md.getColumnName(i+1);
				names[i] = fields.get(i);
			}
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn( "Error listing columns", ex );
		}
	}
	public String[] fieldNames()
	{
		return names;
	}
	public boolean hasNext()
	{
		if ( checkedState )
		{
			return checkedValue;
		}
		else
		{
			try
			{
				checkedState = true;
				checkedValue = rs.next();
			}
			catch ( Exception ex )
			{
				checkedValue = false;
			}
			return checkedValue;
		}
	}
	public void remove()
	{
		//rs.remove();
	}
	public Map<String,String> nextBinding()
	{
		Map<String,String> bindings = new HashMap<String,String>();
		try
		{
			if ( (checkedState && checkedValue) || rs.next() )
			{
				checkedState = false;
				for ( int i = 0; i < cols.length; i++ )
				{
					bindings.put(
						names[i],
						RelationalTripleStore.stripBrackets(
							rs.getString(cols[i])
						)
					);
				}
			}
			else
			{
				bindings = null;
			}
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn( "Error listing bindings", ex );
		}
		return bindings;
	}
	public void close()
	{
		checkedState = true;
		checkedValue = false;
		try
		{
			java.sql.Statement stmt = rs.getStatement();
			rs.close();
			if ( stmt != null ) { stmt.close(); }
		}
		catch ( Exception ex )
		{
			RelationalTripleStore.log.warn("Error closing binding iterator",ex);
		}
	}
}
