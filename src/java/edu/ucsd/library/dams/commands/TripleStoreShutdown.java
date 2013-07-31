package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Properties;

import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Shutdown a triplestore.
 * @author escowles@ucsd.edu
**/
public class TripleStoreShutdown
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

		// get TripleStore instance
		TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

		// shutdown
		ts.shutdown();

		// close the model and database connection
		System.out.println("closing connection");
		ts.close();
	}
}
