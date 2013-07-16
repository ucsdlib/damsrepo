package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Properties;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * Upload files to a filestore.
 * @author escowles@ucsd.edu
**/
public class FileStoreUpload
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String fsName = args[1];
		String objid = args[2];
		String cmpid = args[3];
		String fileid = args[4];
		String src = args[5];

		if ( cmpid.trim().equals("") ) { cmpid = null; }

		// get TripleStore instance
		FileStore fs = FileStoreUtil.getFileStore( props, fsName );

		// upload file
		FileInputStream fin = new FileInputStream(src);
		fs.write( objid, cmpid, fileid, fin );
		fin.close();

		// close the filestore
		fs.close();
	}
}
