package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * List files in an object.
 * @author escowles@ucsd.edu
**/
public class FileStoreList
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String fsName = args[1];
		String objid = args[2];
		String cmpid = null;
		if ( args.length > 3 ) { cmpid = args[3]; }

		// get TripleStore instance
		FileStore fs = FileStoreUtil.getFileStore( props, fsName );

		// list files
		String[] files = fs.listFiles( objid, cmpid );
		for ( int i = 0; i < files.length; i++ )
		{
			System.out.println( i + ": " + files[i] );
		}

		// close the model and database connection
		System.out.println("closing connection");
		fs.close();
	}
}
