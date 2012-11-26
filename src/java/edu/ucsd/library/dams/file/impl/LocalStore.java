package edu.ucsd.library.dams.file.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import java.text.SimpleDateFormat;
import javax.activation.FileDataSource;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * FileStore implementation for local files (or samba, etc. mounted).
 * @author escowles@ucsd.edu
**/
public class LocalStore implements FileStore
{
	protected File baseDir = null;
	protected int baseLength = 0;
	protected File trashDir = null;
	protected static String orgCode = null;
	protected static SimpleDateFormat df = new SimpleDateFormat(
		"EEE, d MMM yyyy HH:mm:ss Z"
	);


/*****************************************************************************/
/********** Constructors *****************************************************/
/*****************************************************************************/

	/**
	 * Create a LocalStore with the given basedir and orgCode.
	 * @param baseDir File directory tree base directory.
	 * @param orgCode Optional organization code to prefix all filenames with.
	 *   The prefix will be omitted if this is null.
	**/
	public LocalStore( File baseDir, String orgCode )
	{
		this.baseDir = baseDir;
		this.baseLength = baseDir.getAbsolutePath().length();
		this.trashDir = new File( baseDir, "trash" );
		this.orgCode = orgCode;
	}

	/**
	 * Create a LocalStore using the provided properties object.
	 * @param props Properties object containing baseDir and optional orgCode
	 *   properties. Filenames will be prefixed with the orgCode if it is
	 *   provided.
	**/
	public LocalStore( Properties props )
	{
		this.baseDir = new File( props.getProperty("baseDir") );
		this.baseLength = baseDir.getAbsolutePath().length();
		this.trashDir = new File( baseDir, "trash" );
		this.orgCode = props.getProperty("orgCode");
	}


/*****************************************************************************/
/********** FileStore impl ***************************************************/
/*****************************************************************************/

	public String[] listFiles( String objectID, String componentID )
		throws FileStoreException
	{
		String[] fileArr = null;
		try
		{
			// list files
			File dir = dir( objectID );
			String[] files = dir.list();

			// make a list of file names
			ArrayList<String> fileList = new ArrayList<String>();
			for ( int i = 0; files != null && i < files.length; i++ )
			{
				String fn = files[i];
				if ( fn.indexOf(objectID) > -1 && !fn.endsWith("manifest.txt")
					&& !fn.startsWith(".") )
				{
					// only add files that match objectID (and component ID if specified)
					String prefix = objectID;
					if ( componentID != null )
					{
						prefix += "-" + componentID;
					}
					int offset = fn.indexOf(prefix);
					if ( offset > -1 )
					{
						fileList.add( fn.substring( offset + prefix.length() + 1 ) );
					}
				}
			}

			// make array
			fileArr = fileList.toArray( new String[fileList.size()] );
		}
		catch ( Exception ex )
		{
			throw new FileStoreException(ex);
		}
		return fileArr;
	}
	public String[] listComponents( String objectID ) throws FileStoreException
	{
		String[] cmpArr = null;
		try
		{
			// list files
			File dir = dir( objectID );
			String[] files = dir.list();

			// make a list of file names
			HashSet<String> cmpSet = new HashSet<String>();
			for ( int i = 0; files != null && i < files.length; i++ )
			{
				String fn = files[i];
				if ( fn.indexOf(objectID) > -1 && !fn.endsWith("manifest.txt")
					&& !fn.startsWith(".") )
				{
					int idx1 = fn.indexOf(objectID) + objectID.length() + 1;
					int idx2 = fn.indexOf("-",idx1);
					fn = fn.substring(idx1, idx2);
					cmpSet.add( fn );
				}
			}

			// make array
			cmpArr = cmpSet.toArray( new String[cmpSet.size()] );
		}
		catch ( Exception ex )
		{
			throw new FileStoreException(ex);
		}
		return cmpArr;
	}
	public boolean exists( String objectID, String componentID, String fileID )
		throws FileStoreException
	{
		return getFile(objectID, componentID, fileID).exists();
	}
	public long length( String objectID, String componentID, String fileID )
		throws FileStoreException
	{
		return getFile(objectID, componentID, fileID).length();
	}
	public Map<String,String> meta( String objectID, String componentID, String fileID )
		throws FileStoreException
	{
		File f = getFile(objectID, componentID, fileID);
		Map<String,String> md = new HashMap<String,String>();
    	//Content-Length: 18961492
		md.put( "Content-Length", String.valueOf(f.length()) );

    	//Content-Type: image/tiff
		md.put( "Content-Type", new FileDataSource(fileID).getContentType() );

    	//Date: Mon, 07 May 2012 14:53:50 GMT
		md.put( "Date", df.format(new Date()) );

    	//Last-Modified: Mon, 07 May 2012 14:43:45 GMT
		Date d = new Date( f.lastModified() );
		md.put( "Last-Modified", df.format(d) );

    	//Etag: 10138fc0c679dd74d0ae50d7c38ebe5c
		// XXX: we could generate this...

    	//Accept-Ranges: bytes
		// XXX: not useful

		return md;
	}
	public byte[] read( String objectID, String componentID, String fileID )
		throws FileStoreException
	{
		File f = getFile(objectID, componentID, fileID);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		read( f, bos );
		return bos.toByteArray();
	}
	public byte[] readManifest( String objectID ) throws FileStoreException
	{
		File f = manifest(objectID);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		read( f, bos );
		return bos.toByteArray();
	}
	public void read( String objectID, String componentID, String fileID, OutputStream out )
		throws FileStoreException
	{
		File f = getFile(objectID, componentID, fileID);
		read( f, out );
	}
	protected void read( File f, OutputStream out ) throws FileStoreException
	{
		try
		{
			BufferedInputStream in = new BufferedInputStream(
				new FileInputStream(f)
			);
			FileStoreUtil.copy( in, out );
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
	}
	public InputStream getInputStream( String objectID, String componentID, String fileID )
		throws FileStoreException
	{
		FileInputStream is = null;
		try
		{
			is = new FileInputStream( getFile(objectID, componentID, fileID) );
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
		return is;
	}
	public void write( String objectID, String componentID, String fileID, byte[] data )
		throws FileStoreException
	{
		File f = getFile(objectID, componentID, fileID);
		ByteArrayInputStream bin = new ByteArrayInputStream(data);
		write(f, bin);
	}
	public void write( String objectID, String componentID, String fileID, InputStream in )
		throws FileStoreException
	{
		File f = getFile(objectID, componentID, fileID);
		write(f, in);
	}
	public void writeManifest( String objectID, byte[] data )
		throws FileStoreException
	{
		File f = manifest(objectID);
		ByteArrayInputStream bin = new ByteArrayInputStream(data);
		write(f, bin);
	}
	private void write( File f, InputStream in ) throws FileStoreException
	{
		try
		{
			File parent = f.getParentFile();
			if ( !parent.exists() )
			{
				boolean success = parent.mkdirs();
				if ( !success )
				{
					throw new FileStoreException("Couldn't create parent dir: " + parent.getAbsolutePath());
				}
			}
			BufferedOutputStream out = new BufferedOutputStream(
				new FileOutputStream(f)
			);
			FileStoreUtil.copy( in, out );
			out.flush();
			out.close();
		}
		catch ( IOException ex )
		{
			throw new FileStoreException(ex);
		}
	}
	public void close() throws FileStoreException
	{
		this.baseDir = null;
		this.baseLength = 0;
	}
	public void trash( String objectID, String componentID, String fileID )
		throws FileStoreException
	{
		boolean success = false;
		try
		{
			File file = getFile( objectID, componentID, fileID );
			File trashFile = trashFile( objectID, componentID, fileID );
			File parent = trashFile.getParentFile();
			if ( !parent.exists() ) { parent.mkdirs(); }
			success = file.renameTo( trashFile );
		}
		catch ( Exception ex )
		{
			throw new FileStoreException(ex);
		}

		if ( !success )
		{
			throw new FileStoreException("Trashing failed");
		}
	}
	public String orgCode() { return orgCode; }
    public String getPath( String objectID, String componentID, String fileID )
    {
		File f = getFile( objectID, componentID, fileID );
        return f.getAbsolutePath();
    }


/*****************************************************************************/
/********** Private methods **************************************************/
/*****************************************************************************/

	/* construct the directory based on the object id */
	protected File dir( String objectID )
	{
		String path = FileStoreUtil.pairPath(objectID);
		return new File( baseDir, path );
	}

	/* construct a file object for the object manifest */
	private File manifest( String objectID )
	{
		return getFile( objectID, null, "manifest.txt" );
	}

	/**
	 * Construct a file object based on the object and file ids.
	 * @param objectID The object identifier.
	 * @param fileID The file identifier.
	**/
	public File getFile( String objectID, String componentID, String fileID )
	{
		if ( componentID == null ) { componentID = "0"; }
		String file = stem(objectID) + componentID + "-" + fileID;
		return new File( dir(objectID), file );
	}

	/* construct the orgCode - objectID base */
	private String stem( String objectID )
	{
		String stem = "";
		if ( orgCode != null && !orgCode.equals("") )
		{
			stem = orgCode + "-";
		}
		stem += objectID + "-";
		return stem;
	}
	/* construct a trash file object based on the object and file ids */
	private File trashFile( String objectID, String componentID, String fileID )
	{
		if ( componentID == null ) { componentID = "0"; }
		String file = stem(objectID) + componentID + "-" + fileID;
		File trashPath = new File( trashDir, FileStoreUtil.pairPath(objectID) );
		return new File( trashPath, file );
	}
}
