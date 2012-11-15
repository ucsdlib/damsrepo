package edu.ucsd.library.dams.triple;

import java.util.Iterator;

/**
 * Iterator that returns RDF-style subjects.
 * @author escowles@ucsd.edu
**/
public abstract class SubjectIterator implements Iterator
{
	/**
	 * Return the next subject.
	**/
	public abstract String nextSubject();

	public String next()
	{
		return nextSubject();
	}

	/**
	 * Close the iterator and free any resources it was using.
	**/
	public void close() { }
}
