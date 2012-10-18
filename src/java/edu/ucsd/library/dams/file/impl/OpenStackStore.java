package edu.ucsd.library.dams.file.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.activation.FileDataSource;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * FileStore implementation for OpenStack Storage API.
 * @see util/SwiftClient
 * @author escowles
**/
public class OpenStackStore implements FileStore
{
	/* size of segments to break files into */
	private static long SEGMENT_SIZE    = 1073741824L; // 1 GB

	private SwiftClient client = null;
	private String orgCode = null;

/*****************************************************************************/
/********** Constructors *****************************************************/
/*****************************************************************************/

	/**
	 * Create an OpenStackStore object, getting parameters from a Properties
	 *   object.
	 * @param props Properties object containing the following properties:
     *  authUser, authToken, authURL, orgCode, timeout.
	**/
	public OpenStackStore( Properties props ) throws FileStoreException
	{
		try
		{
			client = new SwiftClient( props, System.out );
			orgCode = props.getProperty("orgCode");
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
	}


/*****************************************************************************/
/********** FileStore impl ***************************************************/
/*****************************************************************************/

	public String[] list( String objectID ) throws FileStoreException
	{
		String[] fileArr = null;
		try
		{
			// return empty list if the container doesn't exist
			if ( !client.exists(cn(orgCode,objectID),null) )
			{
				return new String[]{ };
			}

			// list files
			List<String> files = client.listObjects(
				null, cn(orgCode,objectID), path(objectID)
			);

			// exclude manifest.txt
			String path = path(objectID);
			String stem = stem(orgCode,objectID);
			if ( path != null ) { stem = path + "/" + stem; }
			List<String> files2 = new ArrayList<String>();
			for ( int i = 0; i < files.size(); i++ )
			{
				String fn = files.get(i);
				if ( fn.startsWith(stem) )
				{
					fn = fn.substring(stem.length());
				}
				if ( !fn.equals("manifest.txt") && !fn.endsWith("/") )
				{
					files2.add( fn );
				}
			}

			// make array
			fileArr = files2.toArray( new String[files2.size()] );
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
		return fileArr;
	}
	public boolean exists( String objectID, String fileID )
		throws FileStoreException
	{
		boolean exists = false;
		try
		{
			exists = client.exists( cn(orgCode,objectID), fn(orgCode,objectID,fileID) );
		}
		catch ( IOException ex )
		{
			// throw exceptions
			throw new FileStoreException(ex);
		}
		return exists;
	}
	public Map<String,String> meta( String objectID, String fileID )
		throws FileStoreException
	{
		Map<String,String> md = null;
		try
		{
			// retrieve metadata for a file
			md = client.stat(
				cn(orgCode,objectID), fn(orgCode,objectID,fileID)
			);
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
		return md;
	}
	public long length( String objectID, String fileID )
		throws FileStoreException
	{
		// retrieve metadata for a file
		Map<String,String> md = meta(objectID,fileID);

		// parse size field
		return Long.parseLong( md.get("Content-Length") );
	}
	public byte[] read( String objectID, String fileID )
		throws FileStoreException
	{
		try
		{
			InputStream in = client.read( null, cn(orgCode,objectID), fn(orgCode,objectID,fileID) );
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			FileStoreUtil.copy( in, out );
			in.close();
			return out.toByteArray();
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
	}
	public byte[] readManifest( String objectID ) throws FileStoreException
	{
		return read( objectID, "manifest.txt" );
	}
	public void read( String objectID, String fileID, OutputStream out )
		throws FileStoreException
	{
		InputStream in = null;
		try
		{
			in = client.read( null, cn(orgCode,objectID), fn(orgCode,objectID,fileID) );
			FileStoreUtil.copy( in, out );
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
		finally
		{
			try { in.close(); } catch ( Exception ex2 ) { }
		}
	}
	public InputStream getInputStream( String objectID, String fileID )
		throws FileStoreException
	{
		InputStream in = null;
		try
		{
			in = client.read( null, cn(orgCode,objectID), fn(orgCode,objectID,fileID) );
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
		return in;
	}
	public void write( String objectID, String fileID, byte[] data )
		throws FileStoreException
	{
		write( objectID, fileID, new ByteArrayInputStream(data) );
	}
	public void writeManifest( String objectID, byte[] data )
		throws FileStoreException
	{
		write( objectID, "manifest.txt", data );
	}
	public void write( String objectID, String fileID, InputStream in )
		throws FileStoreException
	{
		try
		{
			// determine mime type
			String contentType = new FileDataSource(fileID).getContentType();

			// create container if it doesn't already exist
			if ( !client.exists(cn(orgCode,objectID),null) )
			{
				client.createContainer(cn(orgCode,objectID));
			}

			// check inputstream type
			if ( in instanceof FileInputStream )
			{
				// if the stream is from a file, check file's size
				FileInputStream fis = (FileInputStream)in;
				long size = fis.getChannel().size();
				if ( size > SEGMENT_SIZE )
				{
					client.uploadSegmented( cn(orgCode,objectID), fn(orgCode,objectID,fileID), fis, size );
				}
				else
				{
					// under single-segment upload limit, just upload normally
					client.upload( cn(orgCode,objectID), fn(orgCode,objectID,fileID), in, -1 );
				}
			}
			else
			{
				// don't know how large the source is, just try to upload the
				// whole object and throw an error if the 5GB limit is reached
				client.upload( cn(orgCode,objectID), fn(orgCode,objectID,fileID), in, -1 );
			}
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
	}
	public void close() throws FileStoreException
	{
		client = null;
	}
	public void trash( String objectID, String fileID )
		throws FileStoreException
	{
		try
		{
			// make sure trash container exists
			if ( !client.exists("trash",null) )
			{
				client.createContainer("trash");
			}
			client.copy( cn(orgCode,objectID), fn(orgCode,objectID,fileID), "trash", cn(orgCode,objectID) + "/" + fn(orgCode,objectID,fileID) );
			client.delete( cn(orgCode,objectID), fn(orgCode,objectID,fileID) );
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
	}
	public String orgCode() { return orgCode; }
	public String getPath( String objectID, String fileID )
	{
		return cn(orgCode,objectID) + "/" + fn(orgCode,objectID,fileID);
	}

/*****************************************************************************/
/********** Internal file-naming conventions *********************************/
/*****************************************************************************/
	protected static String cn( String orgCode, String objectID )
	{
		return OpenStackStore.cn( orgCode, objectID );
	}
	protected static String stem( String orgCode, String objectID )
	{
		return OpenStackStore.stem( orgCode, objectID );
	}
	protected static String path( String objectID )
	{
		return OpenStackStore.path( objectID );
	}
	protected static String fn( String orgCode, String objectID, String fileID )
	{
		return OpenStackStore.fn( orgCode, objectID, fileID );
	}
}
