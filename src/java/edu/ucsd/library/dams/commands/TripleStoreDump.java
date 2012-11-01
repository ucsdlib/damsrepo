package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.StatementIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Dump the contents of a triplestore in ntriples format.
 * @author escowles@ucsd.edu
**/
public class TripleStoreDump
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

		Identifier subject = null;
		if ( args.length > 2 && args[2] != null && !args[2].equals("") )
		{
			subject = Identifier.publicURI(args[2]);
		}
		System.err.println("subject: " + subject);

		// get TripleStore instance
		TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

		// model size
		System.out.println( "statements: " + ts.size() );

		// list Statements
		System.out.println("listing statements");
		StatementIterator statements = ts.listStatements( subject, null, null );
		while ( statements.hasNext() )
		{
			System.out.println( statements.nextStatement().toString() );
		}
		statements.close();

		// close the model and database connection
		System.out.println("closing connection");
		ts.close();
	}
}
