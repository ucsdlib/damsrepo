package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Count the number of triples in a triplestore.
 * @author escowles@ucsd.edu
**/
public class TripleStoreCount
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

		// get TripleStore instance
		TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

		// model size
		System.out.println( "model size: " + ts.size() );

		// close the model and database connection
		System.out.println("closing connection");
		ts.close();
	}
}
