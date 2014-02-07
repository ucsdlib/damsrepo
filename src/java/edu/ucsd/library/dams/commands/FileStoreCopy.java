package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Properties;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * Copy files from one object to another in a triplestore.
 * @author escowles@ucsd.edu
**/
public class FileStoreCopy
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String fsName = args[1];
		String srcObjID = args[2];
		String dstObjID = args[3];

		// get FileStore instances
		FileStore fs = FileStoreUtil.getFileStore( props, fsName );

		// list components
		String[] comps = fs.listComponents( srcObjID );
		for ( int i = 0; i < comps.length; i++ )
		{
			// list files
			System.out.println( comps[i] );
			String[] files = fs.listFiles( srcObjID, comps[i] );
			for ( int j = 0; j < files.length; j++ )
			{
				System.out.println(
					"    " + srcObjID + " -> " + dstObjID
					+ ", " + comps[i] + ", " + files[j]
				);
				fs.copy(
					srcObjID, comps[i], files[j],
					dstObjID, comps[i], files[j]
				);
			}
		}

		// close the triplestores
		System.out.println("closing connection");
		fs.close();
	}
}
