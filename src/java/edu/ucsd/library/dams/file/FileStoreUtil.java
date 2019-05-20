package edu.ucsd.library.dams.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import edu.ucsd.library.dams.file.impl.LocalStore;

/**
 * Utility for dealing with FileStore implementations.
 * @author escowles@ucsd.edu
**/
public class FileStoreUtil
{
	private static Logger log = Logger.getLogger( FileStoreUtil.class );

	/**
	 * Get an instance of a filestore class.
	 * @param props Properties object holding parameters to initialize the
	 *  triplestore.  Must contain at least the "className" property to
	 *  determine which triplestore class should be created.  Other parameters
	 *  required will depend on the triplestore implementation.
	**/
	public synchronized static FileStore getFileStore( Properties props )
		throws ClassNotFoundException, IllegalAccessException,
			InstantiationException, InvocationTargetException,
			NoSuchMethodException
	{
		String className = props.getProperty("className");
		Class c = Class.forName( className );
		Constructor constructor = c.getConstructor( File.class, String.class);
		return (FileStore)constructor.newInstance( new File (props.getProperty("baseDir")), props.getProperty("orgCode") );
	}

	/**
	 * Get an instance of a filestore class.
	 * @param props Properties object holding parameters to initialize the
	 *  triplestore, prefixed with the triplestore name.
	 * @param name Prefix of the properties in the form "ts.[name].".
	**/
    public static FileStore getFileStore( Properties props, String name )
		throws Exception
    {
        // copy properties for the named filestore to a new properties file
        Properties fprops = new Properties();
        Enumeration e = props.propertyNames();
        String prefix = "fs." + name + ".";
        while ( e.hasMoreElements() )
        {
            String key = (String)e.nextElement();
            if ( key.startsWith(prefix) )
            {
				String key2 = key.substring(prefix.length());
				String val = props.getProperty(key);
                fprops.put( key2, val );
            }
        }

        // load the filestore
        return getFileStore( fprops );
    }


	/**
	 * Convert a string into a pairpath directory tree.
	**/
	public static String pairPath( String s )
	{
		if ( s == null ) { return null; }
		String result = "";
		String leftOver = s;
		while(leftOver.length() > 2) {
			result += leftOver.substring(0, 2) + "/";
			leftOver = leftOver.substring(2);
		}
		return result + leftOver + "/";
	}

	/**
	 * Delete an object from a filestore (trashing existing files).
	 * @param fs Source filestore.
	 * @param objectID Identifier of the object to trash.
	 * @throws FileStoreException On error trashing files.
	 * @return Number of files trashed.
	**/
	public static int trashObject( FileStore fs, String objectID )
		throws FileStoreException
	{
		int trashed = 0;
		try
		{
			// trash existing destination files
			String[] components = fs.listComponents( objectID );
			for ( int i = 0; i < components.length; i++ )
			{
				String[] files = fs.listFiles( objectID, components[i] );
				for ( int j = 0; j < files.length; j++ )
				{
					fs.trash( objectID, components[i], files[j] );
					trashed++;
				}
			}
		}
		catch ( Exception ex )
		{
			throw new FileStoreException( ex );
		}
		return trashed;
	}

	/**
	 * Copy an object from one filestore to another.
	 * @param src Source filestore.
	 * @param dst Destination filestore.
	 * @param objectID Identifier of the object to copy.
	 * @throws FileStoreException On error reading or writing objects in the
	 *   filestores.
	 * @return Number of files copied.
	**/
	public static int copyObject( FileStore src, FileStore dst,
		String objectID ) throws FileStoreException
	{
		int files = 0;
		try
		{
			// trash existing destination files
			trashObject( dst, objectID );

			// copy new files from source
			String[] components = src.listComponents( objectID );
			for ( int i = 0; i < components.length; i++ )
			{
				String[] srcFiles = src.listFiles( objectID, components[i] );
				for ( int j = 0; j < srcFiles.length; j++ )
				{
					copyFile( src, dst, objectID, components[i], srcFiles[j] );
					files++;
				}
			}
		}
		catch ( Exception ex )
		{
			throw new FileStoreException( ex );
		}
		return files;
	}

	/**
	 * Copy a file from one FileStore to another.
	 * @param src FileStore to copy file from.
	 * @param dst FileStore to copy file to.
	 * @param objectID Object identifier.
	 * @param fileID File identifier.
	**/
	public static void copyFile( FileStore src, FileStore dst, String objectID,
		String componentID, String fileID ) throws FileStoreException
	{
		try
		{
			InputStream in = src.getInputStream( objectID, componentID, fileID );
			dst.write( objectID, componentID, fileID, in );
		}
		catch ( Exception ex )
		{
			throw new FileStoreException( ex );
		}
	}

	/**
	 * Copy data from an input stream to an output stream.
	**/
    public static long copy( InputStream in, OutputStream out )
        throws FileStoreException
    {
		long bytesRead = 0;
        try
        {
            BufferedInputStream bufin = new BufferedInputStream(in);
            BufferedOutputStream bufout = new BufferedOutputStream(out);
            for ( int c = -1; (c=bufin.read()) != -1; )
            {   
                bufout.write(c);
				bytesRead++;
            }
			bufout.close();
        }
        catch ( Exception ex ) { throw new FileStoreException(ex); }
		return bytesRead;
    }

    /**
     * Construct file uri
     * @param oid [String] object ark
     * @param cid [String] component id
     * @param fid [String] file id
     */
    public static String getFileUri(String oid, String cid, String fid) {
        return "/" + oid + "/" + (StringUtils.isNotBlank(cid) ? cid + "/" : "") + fid;
    }
}
