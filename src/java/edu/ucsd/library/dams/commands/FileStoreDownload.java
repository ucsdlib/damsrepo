package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * Download files from an object.
 * @author escowles@ucsd.edu
**/
public class FileStoreDownload
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String fsName = args[1];
		String objid = args[2];
		String cmpid = args[3];
		String fileid = args[4];
		String outfile = args[5];

		if ( cmpid.trim().equals("") ) { cmpid = null; }

		// get TripleStore instance
		FileStore fs = FileStoreUtil.getFileStore( props, fsName );

		// download files
		FileOutputStream fout = new FileOutputStream(outfile);
		fs.read( objid, cmpid, fileid, fout );
		fout.close();

		// close the model and database connection
		System.out.println("closing connection");
		fs.close();
	}
}
