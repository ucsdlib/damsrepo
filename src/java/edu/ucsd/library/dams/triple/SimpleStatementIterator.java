package edu.ucsd.library.dams.triple;

import java.util.Collection;
import java.util.Iterator;

/**
 * StatementIterator implementation that can be instantiated with an Iterator
 * or Collection of Statement objects.
 * @author escowles@ucsd.edu
**/
public class SimpleStatementIterator extends StatementIterator
{
	private Iterator<Statement> iter = null;
	public SimpleStatementIterator( Iterator<Statement> iter )
	{
		this.iter = iter;
	}
	public SimpleStatementIterator( Collection<Statement> col )
	{
		this.iter = col.iterator();
	}
	public SimpleStatementIterator( Iterable<Statement> iter )
	{
		this.iter = iter.iterator();
	}
	public Statement nextStatement()
	{
		return iter.next();
	}
	public boolean hasNext()
	{
		return iter.hasNext();
	}
	public void remove()
	{
		iter.remove();
	}
}
