package edu.ucsd.library.dams.triple;

import java.util.Iterator;
import java.util.List;

/**
 * Simple implementation backed by Iterator<Statement>
 * @author escowles@ucsd.edu
**/
public class StatementListIterator extends StatementIterator
{
	private Iterator<Statement> it;

	public StatementListIterator( List<Statement> slist )
	{
		it = slist.iterator();
	}
	public Statement nextStatement() { return it.next(); }
	public boolean hasNext() { return it.hasNext(); }
	public void remove() { it.remove(); }
}
