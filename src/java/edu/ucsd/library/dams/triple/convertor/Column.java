package edu.ucsd.library.dams.triple.convertor;

public class Column {
	public static final int COLUMN_UNKNOWN = -1;
	public static final int COLUMN_PATENT = 0;
	public static final int COLUMN_SUBJECT = 1;
	public static final int COLUMN_PREDICATE = 2;
	public static final int COLUMN_OBJECT = 3;

	private String columnName = null;
	private int columnIndex = COLUMN_UNKNOWN;
	
	public Column (){}
	public Column (String columnName, int columnIndex){
		this.columnName = columnName;
		this.columnIndex = columnIndex;
	}
	public int getColumnIndex() {
		return columnIndex;
	}
	public void setColumnIndex(int columnIndex) {
		this.columnIndex = columnIndex;
	}
	public String getColumnName() {
		return columnName;
	}
	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}
}
