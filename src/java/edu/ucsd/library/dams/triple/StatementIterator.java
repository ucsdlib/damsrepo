package edu.ucsd.library.dams.triple;

import java.util.Iterator;

/**
 * Iterator that returns RDF-style statements.
 * @author escowles
**/
public abstract class StatementIterator implements Iterator
{
	/**
	 * Return the next statement.
	**/
	public abstract Statement nextStatement();

	public Object next()
	{
		return nextStatement();
	}

	/**
	 * Close the iterator and free any resources it was using.
	**/
	public void close() { }
}
