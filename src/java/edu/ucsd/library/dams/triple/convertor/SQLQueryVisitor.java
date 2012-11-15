package edu.ucsd.library.dams.triple.convertor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryVisitor;
import com.hp.hpl.jena.query.SortCondition;
import com.hp.hpl.jena.sparql.core.PathBlock;
import com.hp.hpl.jena.sparql.core.Prologue;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.core.VarExprList;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprAggregator;
import com.hp.hpl.jena.sparql.expr.ExprFunction;
import com.hp.hpl.jena.sparql.expr.ExprFunction0;
import com.hp.hpl.jena.sparql.expr.ExprFunction1;
import com.hp.hpl.jena.sparql.expr.ExprFunction2;
import com.hp.hpl.jena.sparql.expr.ExprFunction3;
import com.hp.hpl.jena.sparql.expr.ExprFunctionN;
import com.hp.hpl.jena.sparql.expr.ExprFunctionOp;
import com.hp.hpl.jena.sparql.expr.ExprVar;
import com.hp.hpl.jena.sparql.expr.ExprVisitor;
import com.hp.hpl.jena.sparql.expr.FunctionLabel;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementAssign;
import com.hp.hpl.jena.sparql.syntax.ElementBind;
import com.hp.hpl.jena.sparql.syntax.ElementData;
import com.hp.hpl.jena.sparql.syntax.ElementDataset;
import com.hp.hpl.jena.sparql.syntax.ElementExists;
import com.hp.hpl.jena.sparql.syntax.ElementFetch;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementMinus;
import com.hp.hpl.jena.sparql.syntax.ElementNamedGraph;
import com.hp.hpl.jena.sparql.syntax.ElementNotExists;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementService;
import com.hp.hpl.jena.sparql.syntax.ElementSubQuery;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitor;

import edu.ucsd.library.dams.triple.convertor.STSTableSchema.DataBaseType;
//XXX import com.hp.hpl.jena.sparql.syntax.ElementUnsaid;

/**
 * Class SQLQueryVisitor convert SPARQL to SQL for the simple triplestore. 
 * @author lsitu@ucsd.edu
 *
 */
public class SQLQueryVisitor implements QueryVisitor{
		private static Logger log = Logger.getLogger(SQLQueryVisitor.class);
		private String sqlQuery = "";
		private STSTableSchema tableSchema = null;
		private Map<String, String> columnsMap = null;
		private boolean isUnion = false;
		private boolean isOptional = false;
		private DataBaseType dbType = null;
		
		public SQLQueryVisitor(STSTableSchema tableSchema) {
			this.tableSchema = tableSchema;
			dbType = tableSchema.getDataBaseType();
		}
		
		public void finishVisit(Query query) {}

		public void startVisit(Query query) {}

		public void visitAskResultForm(Query query) {}

		public void visitConstructResultForm(Query query) {}

		public void visitDatasetDecl(Query query) {}

		public void visitDescribeResultForm(Query query) {
			List resultUris = query.getResultURIs();
			String whereClause = ""; 
			for(int i=0; i<resultUris.size(); i++){
				whereClause += (whereClause.length()>0?" OR ":"") + "t0." + tableSchema.getColumnName(Column.COLUMN_PATENT) + " = '<" + resultUris.get(i) + ">'";
			}
			sqlQuery = "SELECT * FROM " + tableSchema.getTableName() + " t0 " + "\n" + "WHERE " + whereClause + " ORDER BY t0." + tableSchema.getColumnName(Column.COLUMN_PATENT) + ", t0." + tableSchema.getColumnName(Column.COLUMN_SUBJECT);
		}

		public void visitGroupBy(Query query) {}

		public void visitHaving(Query query) {}

		public void visitLimit(Query query) {
			if(query.isAskType()){
				if(dbType.equals(DataBaseType.ORACLE))
					sqlQuery += " AND ROWNUM = 1";
				else
					sqlQuery += " limit 1";
			}else{
				if(query.hasLimit() && !query.hasOffset() && !query.isAskType()){
					if(dbType.equals(DataBaseType.ORACLE))
						sqlQuery += " AND ROWNUM <= " + query.getLimit();
					else
						sqlQuery += " limit " + query.getLimit();
				}
			}
		}

		public void visitOffset(Query query) {
			if(query.hasOffset()){
				if(dbType.equals(DataBaseType.ORACLE))
					sqlQuery += " AND (ROWNUM > " + query.getOffset() + " AND ROWNUM <= " + (query.getLimit() + query.getOffset()) + ")";
				else
					sqlQuery += " LIMIT " + query.getLimit()  + " OFFSET " + query.getOffset();
			}
			postVisitGroupBy(query);
			postVisitOrderBy(query);
		}

		public void visitOrderBy(Query query) {}

		public void visitPrologue(Prologue prologue) {}

		public void visitQueryPattern(Query query) {
			Element elem = query.getQueryPattern();
			if(elem != null){
				SQLElementVisitor elemVisitor = new SQLElementVisitor(tableSchema, query.getResultVars(), dbType);
				elemVisitor.setDestinct(query.isDistinct());
				elem.visit(elemVisitor);
				isUnion = elemVisitor.isUnion();
				isOptional = elemVisitor.isOptional();
				sqlQuery += elemVisitor.getSQLBody();
				//if(query.isDistinct())
				//	sqlQuery = sqlQuery.replace("SELECT ", "SELECT DISTINCT ");
				columnsMap = elemVisitor.getColumnsMap();
			}
			//System.out.println(sqlQuery);
		}

		public void visitResultForm(Query query) {}

		public void visitSelectResultForm(Query query) {}
		
		public String getSQLQuery(){
			return sqlQuery;
		}
		
		public boolean isOptional(){
			return isOptional;
		}
		
		private void postVisitOrderBy(Query query){

			if(query.hasOrderBy()){
				String varStr = "";
				String direction = "";
				List orderBys = query.getOrderBy();
				for(int i=0; i<orderBys.size(); i++){
					SortCondition sort = (SortCondition)orderBys.get(i);
					if(i==0){
						direction = (sort.direction==Query.ORDER_DESCENDING)?" DESC":"";
					}
					
					String orderByField = columnsMap.get(sort.getExpression().getVarName());
					if(isUnion){
						orderByField = orderByField.substring(orderByField.indexOf('.')+1);
					}
					varStr += (varStr.length()>0?", ":"") + orderByField;
				}
				sqlQuery += "\n ORDER BY " + varStr + direction;
			}	
		
		}
		
		private void postVisitGroupBy(Query query) {
			if(query.hasGroupBy()){
				String varStr = "";
				VarExprList groupBys = query.getGroupBy();
				List vars = groupBys.getVars();
				for(int i=0; i<vars.size(); i++){
					Var var = (Var)vars.get(i);
					if(groupBys.hasExpr(var)){
						log.error("Unsupported Group By expression: " + var);
					}else{
						varStr += (varStr.length()>0?", ":"") + columnsMap.get(var.getName());
					}
				}
				sqlQuery += "\n GROUP BY " + varStr;
			}			
		}
		public void visitValues(Query query)
		{
			// XXX: impl
			log.debug("Function NOT implemented - visitValues(Query query): " + query);			
		}
	}
	
	/**
	 * SQLElementVisitor convert SPARQL elements to SQL
	 * @author lsitu@ucsd.edu
	 *
	 */
	class SQLElementVisitor implements ElementVisitor{
		private static Logger log = Logger.getLogger(SQLElementVisitor.class);
		
		private boolean isDistinct = false;
		private Map<String, String> columnsMap = new HashMap<String, String>();
		private List<Triple> triples = new ArrayList<Triple>();
		private List resultVars = null;
		//private List<String> orgResultVars = null;
		//private STSTableSchema tableSchema = null;
		private String selectClause = "";
		private String fromClause = "";
		private String whereClause = "";
		private String literalWhere = "";
		private String exprWhere = "";
		private String sqlQuery = "";
		private boolean assembleQuery = false;
		private boolean isUnion = false;
		private boolean isOptional = false;
		private DataBaseType dbType = null;
		
		String TABLE_NAME = null;
		String COLUMN_SUBJECT = null;
		String COLUMN_PREDICATE = null;
		String COLUMN_OBJECT = null;
		
		public SQLElementVisitor(STSTableSchema tableSchema, List resultVars, DataBaseType dbType){
			//this.tableSchema = tableSchema;
			this.resultVars = resultVars;
			this.dbType = dbType;
			
			TABLE_NAME = tableSchema.getTableName();
			COLUMN_SUBJECT = tableSchema.getColumnName(Column.COLUMN_SUBJECT);
			COLUMN_PREDICATE = tableSchema.getColumnName(Column.COLUMN_PREDICATE);
			COLUMN_OBJECT = tableSchema.getColumnName(Column.COLUMN_OBJECT);
		}
		
		public void visit(ElementTriplesBlock elem) {
			Iterator<Triple> it = elem.patternElts();
			while ( it.hasNext() )
			{
				triples.add( it.next() );
			}
		}

		public void visit(ElementFilter elem) {
			Expr expr = elem.getExpr(); 			
			SQLExprVisitor v = new SQLExprVisitor(triples);
			expr.visit(v);
			exprWhere += (exprWhere.length()>0?" AND ":"") + v.getSQLExpr();
		}

		public void visit(ElementUnion elem) {
			isUnion = true;
			List elems = elem.getElements();
			int pos = 0;
			for(Iterator it=elems.iterator();it.hasNext();){
				if(pos > 0)
					assembleQuery = true;
				
				Element el = (Element)it.next();			
				el.visit(this);
				
				if(pos > 0)
					sqlQuery += "\n UNION \n";
				pos++;
			}			
		}

		public void visit(ElementOptional elem) {
			isOptional = true;
			log.debug("Function NOT implemented - visit(ElementOptional elem): " + elem);			
		}

		public void visit(ElementGroup elem) {
			if(assembleQuery && triples.size() > 0){
				sqlQuery += getQueryBody();
				newQueryBody();
				assembleQuery = false;
			}
			
			List elems = elem.getElements();
			for(int i=0; i<elems.size(); i++){
				Element elemTmp = (Element) elems.get(i);
				elemTmp.visit(this);
			}			
		}

		public void visit(ElementDataset elem) {
			log.debug("Function NOT implemented - visit(ElementDataset elem): " + elem);
		}

		public void visit(ElementNamedGraph elem) {
			log.debug("Function NOT implemented - visit(ElementNamedGraph elem): " + elem);
		}

// XXX		public void visit(ElementUnsaid elem) {
// XXX			log.debug("Function NOT implemented - (ElementUnsaid elem): " + elem);			
// XXX		}
		public void visit(ElementSubQuery elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementSubQuery elem): " + elem);			
		}
		public void visit(ElementFetch elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementFetch elem): " + elem);			
		}
		public void visit(ElementMinus elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementMinus elem): " + elem);			
		}
		public void visit(ElementData elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementData elem): " + elem);			
		}
		public void visit(ElementBind elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementBind elem): " + elem);			
		}
		public void visit(ElementAssign elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementAssign elem): " + elem);			
		}
		public void visit(ElementPathBlock elem) {
			PathBlock pb = elem.getPattern();
			ListIterator<TriplePath> lit=pb.iterator();
			TriplePath tp = null;
			while(lit.hasNext()){
				tp = lit.next();
				triples.add(tp.asTriple());
			}			
		}
		public void visit(ElementNotExists elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementNotExists elem): " + elem);			
		}
		public void visit(ElementExists elem) {
			// XXX
			log.debug("Function NOT implemented - (ElementExists elem): " + elem);			
		}

		public void visit(ElementService elem) {
			log.debug("Function NOT implemented - visit(ElementService elem): " + elem);
		}
		
		public void setDestinct(boolean distinct){
			this.isDistinct = distinct;
		}
		
		public String getSQLBody(){
			if(triples.size() > 0)
				sqlQuery += getQueryBody();
			return sqlQuery;
		}
		
		private void newQueryBody(){
			triples = new ArrayList<Triple>();
			//resultVars = new ArrayList<String>();
			//resultVars.addAll(orgResultVars);
			selectClause = "";
			fromClause = "";
			whereClause = "";
			literalWhere = "";
			exprWhere = "";
		}
		
		private String getQueryBody(){
			String tAlias = null;
			Triple triple = null;
			
			Node sub = null;
			Node pre = null;
			Node obj = null;
			int position = 0;
			Iterator it = triples.iterator();
			while(it.hasNext()){
				tAlias = "t" + position;
				fromClause += (fromClause.length()>0?", ":"") + TABLE_NAME + " " + tAlias;
				
				triple = (Triple) it.next();
				
				sub = triple.getSubject();
				pre = triple.getPredicate();
				obj = triple.getObject();
				
				List<String> matchsVars = null;
				String matchsClause = "";
				if(sub.isVariable()){
					//selectAsResult(tAlias, COLUMN_SUBJECT, sub);
					addColumn(tAlias, COLUMN_SUBJECT, sub);
					matchsVars = getMatchVars(sub, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_SUBJECT + "=" + matchsVars.get(i);
							if(whereClause.indexOf(matchsClause) < 0)
								whereClause += (whereClause.length()>0?" AND ":"") + matchsClause;
						}
					}
				}else if (sub.isLiteral()){					
					matchsVars = getMatchVars(sub, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_SUBJECT + "=" + matchsVars.get(i);
							if(literalWhere.indexOf(matchsClause) < 0)
								literalWhere += (literalWhere.length()>0?" AND ":"") + matchsClause;
						}
					}else
						literalWhere += ((literalWhere.length()>0)?" AND ":"") + tAlias + "." + COLUMN_SUBJECT + "='" + sub.getLiteralValue() + "'";
				}else if (sub.isURI()){
					matchsVars = getMatchVars(sub, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_SUBJECT + "=" + matchsVars.get(i);
							if(literalWhere.indexOf(matchsClause) < 0)
								literalWhere += (literalWhere.length()>0?" AND ":"") + matchsClause;
						}
					}else
						literalWhere += ((literalWhere.length()>0)?" AND ":"") + tAlias + "." + COLUMN_SUBJECT + "='<" + sub.getURI() + ">'";
				}else{
					log.error("Error: unhandle subject type -> " + sub.toString());
				}
				
				if(pre.isVariable()){
					addColumn(tAlias, COLUMN_PREDICATE, pre);
					matchsVars = getMatchVars(pre, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_PREDICATE + "=" + matchsVars.get(i);
							if(whereClause.indexOf(matchsClause) < 0)
								whereClause += (whereClause.length()>0?" AND ":"") + matchsClause;
						}
					}
				}else if (pre.isLiteral()){
					matchsVars = getMatchVars(pre, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_PREDICATE + "=" + matchsVars.get(i);
							if(literalWhere.indexOf(matchsClause) < 0)
								literalWhere += (literalWhere.length()>0?" AND ":"") + matchsClause;
						}
					}else
						literalWhere += ((literalWhere.length()>0)?" AND ":"") + tAlias + "." + COLUMN_PREDICATE + "='" + pre.getLiteralValue() + "'";
				}else if (pre.isURI()){
					matchsVars = getMatchVars(pre, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_PREDICATE + "=" + matchsVars.get(i);
							if(literalWhere.indexOf(matchsClause) < 0)
								literalWhere += (literalWhere.length()>0?" AND ":"") + matchsClause;
						}
					}else
						literalWhere += ((literalWhere.length()>0)?" AND ":"") + tAlias + "." + COLUMN_PREDICATE + "='<" + pre.getURI() + ">'";
				}else{
					log.error("Error: unhandle predicate type -> " + pre.toString());
				}
				
				if(obj.isVariable()){
					addColumn(tAlias, COLUMN_OBJECT, obj);
					matchsVars = getMatchVars(obj, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_OBJECT + "=" + matchsVars.get(i);
							if(whereClause.indexOf(matchsClause) < 0)
								whereClause += (whereClause.length()>0?" AND ":"") + matchsClause;
						}
					}
					
				}else if (obj.isLiteral()){
					matchsVars = getMatchVars(obj, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_OBJECT + "=" + matchsVars.get(i);
							if(literalWhere.indexOf(matchsClause) < 0)
								literalWhere += (literalWhere.length()>0?" AND ":"") + matchsClause;
						}
					}else
						literalWhere += ((literalWhere.length()>0)?" AND ":"") + tAlias + "." + COLUMN_OBJECT + "='" + obj.getLiteralValue() + "'";
				}else if (obj.isURI()){
					matchsVars = getMatchVars(obj, position);
					int varSize = matchsVars.size();
					if(varSize > 0){
						for(int i=0; i<varSize; i++){
							matchsClause = tAlias + "." + COLUMN_OBJECT + "=" + matchsVars.get(i);
							if(literalWhere.indexOf(matchsClause) < 0)
								literalWhere += (literalWhere.length()>0?" AND ":"") + matchsClause;
						}
					}else
						literalWhere += ((literalWhere.length()>0)?" AND ":"") + tAlias + "." + COLUMN_OBJECT + "='<" + obj.getURI() + ">'";
				}else{
					log.error("Error: unhandle object type -> " + obj.toString());
				}
				position++;
			}
			
			if(literalWhere.length()>0)
				whereClause = "\n WHERE " + whereClause + ((whereClause.length()>0 && literalWhere.length()>0)?" AND ":"") + literalWhere;			
			if(exprWhere.length()>0)
				whereClause += (whereClause.length()>0?" AND ":"\n WHERE ") + exprWhere;
			
			fromClause = "\n FROM " + fromClause;
			selectClause = "SELECT " + (isDistinct?"DISTINCT ":"") + getResultColumns();
			return selectClause + fromClause + whereClause;
		}
		
		public boolean isUnion(){
			return isUnion;
		}
		
		public boolean isOptional(){
			return isOptional;
		}
		
		public List<String> getMatchVars(Node var, int position){
			int pos = 0;
			Node sub = null;
			Node pre = null;
			Node obj = null;
			Triple triple = null;
			List<String> matchVars = new ArrayList<String>();
			Iterator it = triples.iterator();
			while(it.hasNext()){
				triple = (Triple)it.next();
				if(pos > position){
					sub = triple.getSubject();
					pre = triple.getPredicate();
					obj = triple.getObject();
					
					String alias = "t" + pos;
					if(var.equals(sub)){
						addmatchVar(matchVars, alias, COLUMN_SUBJECT, var);
					}
					if(var.equals(pre)){
						addmatchVar(matchVars, alias, COLUMN_PREDICATE, var);
					}
					if(var.equals(obj)){
						addmatchVar(matchVars, alias, COLUMN_OBJECT, var);
					}
				}
				pos++;
				if(matchVars.size() > 0)
					break;
			}
			return matchVars;
		}
		
		private void addmatchVar(List<String> matchVars, String alias, String fieldName, Node var){
			String column = alias + "." + fieldName;
			matchVars.add(column);
		}
		
		private void addColumn(String alias, String fieldName, Node var){
			String column = alias + "." + fieldName;
			if(var.isVariable()){
				String varName = var.getName();
				if(columnsMap.get(varName) == null)
					columnsMap.put(varName, column);
			}
		}
		
		public String getResultColumns(){
			String selClause = "";
			int size = resultVars.size();
			if(size == 0){
				selClause = "*";
			}else{
				for(int i=0; i<size;i++){
					String reVar = (String)resultVars.get(i);
					String column = columnsMap.get(reVar);
					if(column != null){
						selClause += (selClause.length()>0?", ":"") + column;
					}else
						log.error("Result column is not bound: " + reVar);
				}
			}
			return selClause;
		}
		
		public Map<String, String> getColumnsMap(){
			return columnsMap;
		}
		
		/**
		 * SQLExprVisitor convert SPARQL expression to Oracle SQL expression
		 * @author lsitu@ucsd.edu
		 *
		 */
		class SQLExprVisitor implements ExprVisitor{
			
			private List<Triple> triples = new ArrayList<Triple>();
			private String sqlExpr = "";
			
			public SQLExprVisitor(List<Triple> triples){
				this.triples= triples;
			}
			public void finishVisit() {
				sqlExpr += ") ";
			}

			public void startVisit() {
				sqlExpr += " (";
			}

			public void visit(ExprFunction func) {
				FunctionLabel funcLabel = func.getFunctionSymbol();
				String funcName = funcLabel.getSymbol();
				List args = func.getArgs();
				if(funcName.equalsIgnoreCase("regex")){
					sqlExpr += convertSymbol(funcName);
					startVisit();
					for(int i=0;i<args.size(); i++){
						Object obj = args.get(i);
						if(obj instanceof ExprVar){
							visit((ExprVar)obj);
						}else if(obj instanceof NodeValue){
							visit((NodeValue)obj);
						}else if(obj instanceof ExprFunction)
							visit((ExprFunction)obj);
						else
							System.out.println("    - Unhandle: " + obj.getClass().getName());
						
						if(i<args.size() - 1)
							sqlExpr += ", ";
					}
					finishVisit();
				}else{
					if(funcName.equalsIgnoreCase("not"))
						sqlExpr += " " + convertSymbol(funcName) + " ";
					
					if(funcName.equalsIgnoreCase("or") || funcName.equalsIgnoreCase("and") )
						startVisit();	
				
					for(int i=0;i<args.size(); i++){
						Object obj = args.get(i);
						
						if(i==1){					
							sqlExpr += " " + convertSymbol(funcName) + " ";
						}
						
						if(obj instanceof ExprVar)
							visit((ExprVar)obj);
						else if(obj instanceof NodeValue)
							visit((NodeValue)obj);
						else if(obj instanceof ExprFunction)
							visit((ExprFunction)obj);
						else
							System.out.println("    - Unhandle: " + obj.getClass().getName());			
					}
					if(funcName.equalsIgnoreCase("or") || funcName.equalsIgnoreCase("and"))
						finishVisit();
				}
			}

			public void visit(NodeValue node) {	
				sqlExpr += "'" + node.getString() + "'";
			}

			public void visit(ExprVar var) {
				sqlExpr += getColumnName(var);
			}
			public void visit(ExprAggregator expr)
			{
				// XXX
				log.debug("Function NOT implemented - visit(ExprAggregator expr): " + expr);			
			}
			public void visit(ExprFunctionN expr)
			{
				sqlExpr += convertSymbol(expr.getFunctionSymbol().getSymbol());
				List<Expr> args = expr.getArgs();
				startVisit();
				for(int i=0;i<args.size(); i++){
					Object obj = args.get(i);
					if(obj instanceof ExprVar){
						visit((ExprVar)obj);
					}else if(obj instanceof NodeValue){
						visit((NodeValue)obj);
					}else if(obj instanceof ExprFunction)
						visit((ExprFunction)obj);
					else
						System.out.println("    - Unhandle: " + obj.getClass().getName());
					
					if(i<args.size() - 1)
						sqlExpr += ", ";
				}
				finishVisit();	
			}
			public void visit(ExprFunction0 expr)
			{
				// XXX
				log.debug("Function NOT implemented - visit(ExprFunction0 expr): " + expr);			
			}
			public void visit(ExprFunction3 expr)
			{
				// XXX
				log.debug("Function NOT implemented - visit(ExprFunction3 expr): " + expr);			
			}
			public void visit(ExprFunction2 expr)
			{
				String funcName = expr.getFunctionSymbol().getSymbol();
				List<Expr> args = expr.getArgs();
				if(funcName.equalsIgnoreCase("not"))
					sqlExpr += " " + convertSymbol(funcName) + " ";
				
				if(funcName.equalsIgnoreCase("or") || funcName.equalsIgnoreCase("and") )
					startVisit();	
			
				for(int i=0;i<args.size(); i++){
					Object obj = args.get(i);
					
					if(i==1){					
						sqlExpr += " " + convertSymbol(funcName) + " ";
					}
					
					if(obj instanceof ExprVar)
						visit((ExprVar)obj);
					else if(obj instanceof NodeValue)
						visit((NodeValue)obj);
					else if(obj instanceof ExprFunction)
						visit((ExprFunction)obj);
					else
						System.out.println("    - Unhandle: " + obj.getClass().getName());
				}
				if(funcName.equalsIgnoreCase("or") || funcName.equalsIgnoreCase("and"))
					finishVisit();
			}
			public void visit(ExprFunction1 expr)
			{
				// XXX
				log.debug("Function NOT implemented - visit(ExprFunction1 expr): " + expr);			
			}
			public void visit(ExprFunctionOp expr)
			{
				// XXX
				log.debug("Function NOT implemented - visit(ExprFunctionOp expr): " + expr);			
			}
			
			public String getSQLExpr(){
				return sqlExpr;
			}
			
			public String getColumnName(ExprVar var){
				int pos = 0;
				Node sub = null;
				Node pre = null;
				Node obj = null;
				Triple triple = null;
				String tableColumn = "";
				Iterator it = triples.iterator();
				while(it.hasNext()){
					triple = (Triple)it.next();
					sub = triple.getSubject();
					pre = triple.getPredicate();
					obj = triple.getObject();
					
					String alias = "t" + pos;
					if(sub.isVariable() && var.getVarName().equals(sub.getName())){
						tableColumn = alias + "." + COLUMN_SUBJECT;
					}
					if(pre.isVariable() && var.getVarName().equals(pre.getName())){
						tableColumn = alias + "." + COLUMN_PREDICATE;
					}
					if(obj.isVariable() && var.getVarName().equals(obj.getName())){
						tableColumn = alias + "." + COLUMN_OBJECT;
					}
					pos++;
				}
				return tableColumn;
			}
			
			/*
			 * Convert sparql symbol to Oracle SQL style
			 */
			public String convertSymbol(String symbol){
				String sqlOp = null;
				if("and".equalsIgnoreCase(symbol))
					sqlOp = "AND";
				else if("or".equalsIgnoreCase(symbol))
					sqlOp = "OR";
				else if("not".equalsIgnoreCase(symbol))
					sqlOp = "NOT";
				else if("LT".equalsIgnoreCase(symbol))
					sqlOp = "<";
				else if("GT".equalsIgnoreCase(symbol))
					sqlOp = ">";
				else if("EQ".equalsIgnoreCase(symbol))
					sqlOp = "=";
				else if("GE".equalsIgnoreCase(symbol))
					sqlOp = ">=";
				else if("LE".equalsIgnoreCase(symbol))
					sqlOp = "<=";
				else if("NE".equalsIgnoreCase(symbol))
					sqlOp = "<>";
				else if("regex".equalsIgnoreCase(symbol))
					if(dbType.equals(DataBaseType.ORACLE))
						sqlOp = "REGEXP_LIKE";
					else if(dbType.equals(DataBaseType.POSTGRESQL))
						sqlOp = "~";
					else
						sqlOp = "REGEXP";
				else
					sqlOp = symbol;
				return sqlOp;
			}
		}
	}

