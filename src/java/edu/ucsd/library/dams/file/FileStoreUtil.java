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

import org.apache.log4j.Logger;

import edu.ucsd.library.dams.file.impl.LocalStore;

/**
 * Utility for dealing with FileStore implementations.
 * @author escowles
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
	public static FileStore getFileStore( Properties props )
		throws ClassNotFoundException, IllegalAccessException,
			InstantiationException, InvocationTargetException,
			NoSuchMethodException
	{
		String className = props.getProperty("className");
		Class c = Class.forName( className );
		Constructor constructor = c.getConstructor(new Properties().getClass());
		return (FileStore)constructor.newInstance( props );
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
                fprops.put(
                    key.substring(prefix.length()), props.getProperty(key)
                );
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
		for( int i = 0; i < (s.length() - 1); i += 2 )
		{
			result += s.substring(i,i+2);
			result += "/";
		}
		return result;
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
			String[] files = fs.list( objectID );
			for ( int i = 0; i < files.length; i++ )
			{
				fs.trash( objectID, files[i] );
				trashed++;
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
			String[] srcFiles = src.list( objectID );
			for ( int i = 0; i < srcFiles.length; i++ )
			{
				copyFile( src, dst, objectID, srcFiles[i] );
				files++;
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
		String fileID ) throws FileStoreException
	{
		try
		{
			InputStream in = src.getInputStream( objectID, fileID );
			dst.write( objectID, fileID, in );
		}
		catch ( Exception ex )
		{
			throw new FileStoreException( ex );
		}
	}

	/**
	 * Copy a file from one FileStore to another.
	 * @param src FileStore to perform operation on.
	 * @param dst FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename to copy.
	**/
	public static void copyFile( FileStore src, FileStore dst, String filename )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		copyFile( src, dst, parts[1], parts[2] );
	}
	/**
	 * Test whether a file exists in the FileStore.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	**/
	public static boolean exists( FileStore fs, String filename )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		return fs.exists( parts[1], parts[2] );
	}
	/**
	 * Get the length of a file in bytes.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	**/
	public static long length( FileStore fs, String filename )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		return fs.length( parts[1], parts[2] );
	}
	/**
	 * Read the contents of a file as a byte array.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	**/
	public static byte[] read( FileStore fs, String filename )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		return fs.read( parts[1], parts[2] );
	}
	/**
	 * Get a File object (for local file-based impl. only).
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	**/
	public static File getFile( FileStore fs, String filename )
		throws FileStoreException
	{
		try
		{
			LocalStore local = (LocalStore)fs;
			String[] parts = filename.split("-",3);
			return local.getFile( parts[1], parts[2] );
		}
		catch ( ClassCastException ccx )
		{
			throw new FileStoreException(ccx);
		}
	}
	/**
	 * Get an InputStream to read data from a file.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	**/
	public static InputStream getInputStream( FileStore fs, String filename )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		return fs.getInputStream( parts[1], parts[2] );
	}
	/**
	 * Read the contents of a file to an OutputStream.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	 * @param out OutputStream object to write data to.
	**/
	public static void read( FileStore fs, String filename, OutputStream out )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		fs.read( parts[1], parts[2], out );
	}
	/**
	 * Write the contents of a byte array to a file.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	 * @param data Byte array containing data.
	**/
	public static void write( FileStore fs, String filename, byte[] data )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		fs.write( parts[1], parts[2], data );
	}
	/**
	 * Write the contents of an inputstream to a file.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	 * @param in InputStream object to read data from.
	**/
	public static void write( FileStore fs, String filename, InputStream in )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		fs.write( parts[1], parts[2], in );
	}
	/**
	 * Move a file to the trash.
	 * @param fs FileStore to perform operation on.
	 * @param filename Fully-qualified (org-id-filename) filename.
	**/
	public static void trash( FileStore fs, String filename )
		throws FileStoreException
	{
		String[] parts = filename.split("-",3);
		fs.trash( parts[1], parts[2] );
	}
	
	/**
	 * Copy data from an input stream to an output stream.
	**/
    public static int copy( InputStream in, OutputStream out )
        throws FileStoreException
    {
		int bytesRead = 0;
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
}
