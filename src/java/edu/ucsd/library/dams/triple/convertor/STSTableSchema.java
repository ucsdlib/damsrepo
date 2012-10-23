package edu.ucsd.library.dams.triple.convertor;

/**
 * Class SSTTableSchema - Basic table schema for the relational Simple Triplestore
 * @author lsitu
 *
 */
public class STSTableSchema {
	
	public static final String COLUMN_SUBJECT = "SUBJECT";
	public static final String COLUMN_PREDICATE = "PREDICATE";
	public static final String COLUMN_OBJECT = "OBJECT";
	public static final String COLUMN_PARENT = "PARENT";
	public static enum DataBaseType {MYSQL, ORACLE, POSTGRESQL};
	
	private String tableName = null;
	private Column[] columns = null;
	private String dbDriverName = "com.mysql.jdbc.Driver";
	
	public STSTableSchema(){}
	public STSTableSchema(String tableName, Column[] columns){
		this.tableName = tableName;
		this.columns = columns;
	}
	public Column[] getColumns() {
		return columns;
	}
	public void setColumns(Column[] columns) {
		this.columns = columns;
	}
	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public DataBaseType getDataBaseType() {
		if(dbDriverName != null){
			if(dbDriverName.indexOf("oracle") >= 0)
				return DataBaseType.ORACLE;
			else if(dbDriverName.indexOf("postgresql") >= 0)
				return DataBaseType.POSTGRESQL;
			else
				return DataBaseType.MYSQL;
		}else
			return DataBaseType.MYSQL;
	}
	public void setDbDriverName(String dbDriverName) {
		this.dbDriverName = dbDriverName;
	}
	public String getColumnName(int columnIndex) {
		String columnName = null;
		if(columns != null){
			for(int  i=0; i<columns.length; i++){
				if(columns[i].getColumnIndex() == columnIndex){
					columnName = columns[i].getColumnName();
					break;
				}
			}
		}
		if(columnName == null || columnName.length() == 0){
			switch(columnIndex){
				case Column.COLUMN_PATENT:
					columnName = COLUMN_PARENT;
					break;
				case Column.COLUMN_SUBJECT:
					columnName = COLUMN_SUBJECT;
					break;
				case Column.COLUMN_PREDICATE:
					columnName = COLUMN_PREDICATE;
					break;
				case Column.COLUMN_OBJECT:
					columnName = COLUMN_OBJECT;
					break;
				default:
					columnName = "COLUMN" + columnIndex;
			}
		}
		return columnName;
	}
}
