package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.triple.SubjectIterator;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * List all subjects described in a triplestore.
 * @author escowles@ucsd.edu
**/
public class TripleStoreList
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

		// get TripleStore instance
		TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

		// list Subjects
		System.out.println("listing subjects");
		SubjectIterator subjects = ts.listSubjects();
		while ( subjects.hasNext() )
		{
			System.out.println(subjects.nextSubject() );
		}
		subjects.close();

		// close the model and database connection
		System.out.println("closing connection");
		ts.close();
	}
}
