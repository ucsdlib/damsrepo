package edu.ucsd.library.dams.triple;

import java.util.Iterator;
import java.util.Map;

/**
 * Iterator that returns a Map of attribute-value pairs from a SPARQL query.
 * @author escowles
**/
public abstract class BindingIterator implements Iterator
{
	/**
	 * Return the next subject.
	**/
	public abstract Map<String,String> nextBinding();

	/**
	 * Return the list of field names present in the bindings.
	**/
	public abstract String[] fieldNames();

	public Object next()
	{
		return nextBinding();
	}

	/**
	 * Close the iterator and free any resources it was using.
	**/
	public void close() { }
}
