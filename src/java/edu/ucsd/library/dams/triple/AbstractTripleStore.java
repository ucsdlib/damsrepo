package edu.ucsd.library.dams.triple;

import java.util.ArrayList;

/**
 * Partial implementation of TripleStore to make implementing easier.
 * @author escowles
**/
public abstract class AbstractTripleStore implements TripleStore
{
	public void addStatement( Statement stmt, Identifier parent )
		throws TripleStoreException
	{
		if ( stmt.hasLiteralObject() )
		{
			addLiteralStatement(
				stmt.getSubject(), stmt.getPredicate(),
				stmt.getLiteral(), parent
			);
		}
		else
		{
			addStatement(
				stmt.getSubject(), stmt.getPredicate(), stmt.getObject(), parent
			);
		}
	}
	public void removeStatement( Statement stmt ) throws TripleStoreException
	{
		if ( stmt.hasLiteralObject() )
		{
			removeLiteralStatements(
				stmt.getSubject(), stmt.getPredicate(), stmt.getLiteral()
			);
		}
		else
		{
			removeStatements(
				stmt.getSubject(), stmt.getPredicate(), stmt.getObject()
			);
		}
	}
	public abstract void removeStatements( Identifier subject,
		Identifier predicate, Identifier object ) throws TripleStoreException;
	public abstract void removeLiteralStatements( Identifier subject,
		Identifier predicate, String object ) throws TripleStoreException;

	public void removeObject( Identifier subject ) throws TripleStoreException
	{
		ArrayList<Identifier> blankNodes = new ArrayList<Identifier>();
		ArrayList<Statement> bnTriples = new ArrayList<Statement>();

		// iterate over statements
		StatementIterator iter = listStatements(subject, null, null);
		while(iter.hasNext())
		{
			// remove leaf nodes
			Statement stmt = iter.nextStatement();
			if(stmt.hasLiteralObject())
			{
				//leaf, remove
				removeLiteralStatements(
					stmt.getSubject(),
					stmt.getPredicate(),
					stmt.getLiteral()
				);
			}
			else
			{
				// store blank nodes for later removal
				blankNodes.add(stmt.getObject());
				bnTriples.add(stmt);
			}
		}
		iter.close();

		// recursively remove blank nodes
		if(blankNodes.size() > 0)
		{
			// recursively remove the children of the blank nodes
			for(Identifier bn : blankNodes)
			{
				removeObject( bn );
			}

			// remove the blank node statements themselves
			for(Statement s : bnTriples)
			{
				removeStatements(
					s.getSubject(),
					s.getPredicate(),
					s.getObject()
				);
			}
		}
	}

	public void export( java.io.File f, boolean subjectsOnly, String parent )
		throws TripleStoreException { throw new TripleStoreException("Not implemented"); }
	public void export( java.io.File f, boolean subjectsOnly )
		throws TripleStoreException { throw new TripleStoreException("Not implemented"); }
	public long sparqlCount( String query ) throws TripleStoreException
	{
		int count = 0;
		BindingIterator it = sparqlSelect( query );
		while ( it.hasNext() )
		{
			count++;
			it.next();
		}
		return count;
	}
	
	public void init() throws TripleStoreException { }
	public void optimize() throws TripleStoreException { }
}
