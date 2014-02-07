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

		// if we don't have a comp specified, list all
		String[] comps = null;
		if ( cmpid != null ) { comps = new String[]{ cmpid }; }
		else { comps = fs.listComponents( objid ); }

		// list files
		for ( int i = 0; i < comps.length; i++ )
		{
			String[] files = fs.listFiles( objid, comps[i] );
			for ( int j = 0; j < files.length; j++ )
			{
				long len = fs.length( objid, comps[i], files[j] );
				System.out.println( comps[i] + " " + files[j] + " " + len );
			}
		}

		// close filestore
		fs.close();
	}
}
