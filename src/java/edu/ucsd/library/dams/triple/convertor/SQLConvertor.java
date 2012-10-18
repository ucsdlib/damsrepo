package edu.ucsd.library.dams.triple.convertor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;

/**
 * Class SQLConvertor converts basic SPARQLs used in the DAMS applications to SQL for Oracle execution
 * @author lsitu
 *
 */
public class SQLConvertor
{
	private static Logger log = Logger.getLogger(SQLConvertor.class);
	
	private STSTableSchema tableSchema = null;
	private String sparql = null;
	private List<String> resultVars = null;
	private String sqlQuery = null;
	private boolean isSparqlAsk = false;
	private boolean isSparqlSelect = false;
	private boolean isSparqlDescribe = false;
	
	public SQLConvertor(STSTableSchema tableSchema, String sparql) throws Exception{
		this.tableSchema = tableSchema;
		this.sparql = sparql;
		convertToSQL();
	}
	
	private void convertToSQL() throws Exception{
		//log.info("Converte SPARQL: " + sparql);
		Query query = null;
		//String sqlQuery = null;
		try{
			query = QueryFactory.create(sparql);
			if(query.hasAggregators())
				throw new Exception("SPARQL Aggregate function is not supported: " + query);
			else if(query.hasDatasetDescription())
				throw new Exception("SPARQL DatasetDescription is not supported: " + query);
			else if(query.hasHaving())
				throw new Exception("SPARQL Having is not supported: " + query);
			else if(query.isConstructType())
				throw new Exception("SPARQL Construct is not supported: " + query);
			else if(query.isReduced())
				throw new Exception("SPARQL Reduced is not supported: " + query);
			else if(query.isUnknownType())
				throw new Exception("Unknown SPARQL type: " + query);
			resultVars = query.getResultVars();
			isSparqlAsk = query.isAskType();
			isSparqlSelect = query.isSelectType();
			isSparqlDescribe = query.isDescribeType();
			SQLQueryVisitor visitor = new SQLQueryVisitor(tableSchema);
			query.visit(visitor);			
			if(visitor.isOptional())
				throw new Exception("SPARQL Optioal is not supported: " + query);
			
			sqlQuery = visitor.getSQLQuery();
		}catch (Exception e){
			log.error(e);
			throw e;
		}
		//return sqlQuery;
	}

	public ResultSet executeSql(Connection conn) throws SQLException{
		Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		return stmt.executeQuery(sqlQuery);
	}
	
	public List<String> getResultVars() {
		return resultVars;
	}

	public String getSparql() {
		return sparql;
	}

	public String getSqlQuery() {
		return sqlQuery;
	}

	public String getSqlCountQuery() {
		return "SELECT COUNT(*) FROM ( " + sqlQuery + " )";
	}

	public STSTableSchema getTableSchema() {
		return tableSchema;
	}

	
	public boolean isSparqlAsk() {
		return isSparqlAsk;
	}

	public boolean isSparqlDescribe() {
		return isSparqlDescribe;
	}

	public boolean isSparqlSelect() {
		return isSparqlSelect;
	}

	public static void main(String[] args) throws Exception{
		//PREFIX dev: <http://libraries.ucsd.edu/ark:/20775/> SELECT distinct ?noteValue WHERE { ?subject dev:bb0819944p ?object . ?object dev:bb5564025j ?noteValue . ?object dev:bb2970144m 'Course / Instructor'}
		//PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/> SELECT DISTINCT ?subject ?object WHERE {?subject ns:bb3652744n ?object . ?subject ns:bb98644023 'bb1093000r'} order by ?object
		//PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/> ASK  {?subject ns:bb2765355h 'bf2765355h' . ?subject ns:bb3652744n ?object . FILTER regex(?object, "r", "i") .FILTER (?object >= 'abc' && ?object < 'efg' || !(?object = 'efg')) }
		//PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/> SELECT ?subject ?object WHERE  {{{?subject ns:bb2765355h 'bf2765355h'} . {?subject ns:bb3652744n ?object} . FILTER regex(?object, "r", "i") .FILTER (?object >= 'abc' && ?object < 'efg' || !(?object = 'efg')) } UNION {?subject ns:bb3652744n ?object . ?subject ns:bb98644023 'bb1093000r'}} ORDER BY ?subject ?object
		//PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/> DESCRIBE  ns:bb4642625p ns:bb36186665
		/*String sparql = "PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/>"
	    	+ " SELECT ?subject ?object WHERE  {{{?subject ns:bb2765355h 'bf2765355h'} . {?subject ns:bb3652744n ?object} . FILTER regex(?object, \"r\", \"i\") .FILTER (?object >= 'abc' && ?object < 'efg' || !(?object = 'efg')) } " +
	    			"UNION " +
	    			"{?subject ns:bb3652744n ?object . ?subject ns:bb98644023 'bb1093000r'}}" +
	    		" order by ?subject ?object";*/
		
		String sparql = "PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/>"
	    	+ " SELECT DISTINCT ?subject ?object WHERE  {?subject ns:bb2765355h 'bf2765355h' . ?subject ns:bb3652744n ?object . FILTER regex(?object, \"r\", \"i\") .FILTER (?object >= 'abc' && ?object < 'efg' || !(?object = 'efg')) } " +
	    			" ORDER BY DESC(?subject) ?object LIMIT 10 OFFSET 20";
		
		//String sparql = "PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/>"
	    //	+ " ASK  {?subject ns:bb2765355h 'bf2765355h' . ?subject ns:bb3652744n ?object . FILTER regex(?object, \"r\", \"i\") .FILTER (?object >= 'abc' && ?object < 'efg' || !(?object = 'efg')) }";
		//String sparql = "PREFIX  ns:  <http://libraries.ucsd.edu/ark:/20775/>"
	    //	+ " DESCRIBE  ns:bb3652744n ns:bf2765355h";
		
		String triplesTableName = "DAMS_TRIPLES";
		Column[] columns = {new Column("PARENT", Column.COLUMN_PATENT),new Column("SUBJECT", Column.COLUMN_SUBJECT), new Column("PREDICATE", Column.COLUMN_PREDICATE),new Column("OBJECT", Column.COLUMN_OBJECT)};
		STSTableSchema tSchema = new STSTableSchema(triplesTableName, columns);
		SQLConvertor convertor = new SQLConvertor(tSchema, sparql);
		String sql = convertor.getSqlQuery();
		System.out.print(sql);
	}
}	
