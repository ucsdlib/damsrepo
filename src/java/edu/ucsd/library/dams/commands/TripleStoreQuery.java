package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.triple.BindingIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Perform a SPARQL query and list the results.
 * @author escowles@ucsd.edu
**/
public class TripleStoreQuery
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];
		String sparql = args[2];

		// get TripleStore instance
		TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

		// perform query
		BindingIterator bindings = ts.sparqlSelect( sparql );
		String[] fields = bindings.fieldNames();
		while ( bindings.hasNext() )
		{
			Map<String,String> m = bindings.nextBinding();
			for ( int i = 0; i < fields.length; i++ )
			{
				System.out.println( fields[i] + ": " + m.get( fields[i] ) );
			}
		}
		bindings.close();

		// close the model and database connection
		System.out.println("closing connection");
		ts.close();
	}
}
