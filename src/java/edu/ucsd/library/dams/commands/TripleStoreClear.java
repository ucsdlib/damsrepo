package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Remove the contents of a triplestore.
 * @author escowles@ucsd.edu
**/
public class TripleStoreClear
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

		// get TripleStore instance
		TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

		// model size
		try { System.out.println( "model size: " + ts.size() ); }
		catch ( Exception ex ) { ex.printStackTrace(); }

		// clear the triplestore
		System.out.println("clearing triplestore");
		try { ts.removeAll(); }
		catch ( Exception ex ) { ex.printStackTrace(); }

		// model size
		try { System.out.println( "model size: " + ts.size() ); }
		catch ( Exception ex ) { ex.printStackTrace(); }

		// close the model and database connection
		System.out.println("closing connection");
		ts.close();
	}
}
