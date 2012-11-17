package edu.ucsd.library.dams.file;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Interface for accessing files in a consistent manner regardless of the
 * underlying storage architecture.
 * @author escowles@ucsd.edu
**/
public interface FileStore
{
	/**
	 * List components in an object.
	**/
	public String[] listComponents( String objectID )
		throws FileStoreException;

	/**
	 * List files in an object.
	**/
	public String[] listFiles( String objectID, String componentID )
		throws FileStoreException;

	/**
	 * Test whether a file exists.
	**/
	public boolean exists( String objectID, String componentID, String fileID )
		throws FileStoreException;

	/**
	 * Returns the length of a file.
	**/
	public long length( String objectID, String componentID, String fileID )
		throws FileStoreException;

	/**
	 * Returns metadata about a file.
	**/
	public Map<String,String> meta( String objectID, String componentID,
		String fileID ) throws FileStoreException;

	/**
	 * Returns the contents of a file as a byte array.
	**/
	public byte[] read( String objectID, String componentID, String fileID )
		throws FileStoreException;

	/**
	 * Returns the object's manifest file, or null if a manifest is not present.
	**/
	public byte[] readManifest( String objectID )
		throws FileStoreException;

	/**
	 * Read a file and write it to an output stream.
	**/
	public void read( String objectID, String componentID, String fileID,
		OutputStream out ) throws FileStoreException;

	/**
	 * Get an InputStream containing file data.
	**/
	public InputStream getInputStream( String objectID, String componentID,
		String fileID ) throws FileStoreException;

	/**
	 * Write the contents of a byte array to a file.
	**/
	public void write( String objectID, String componentID, String fileID,
		byte[] data ) throws FileStoreException;

	/**
	 * Write a manifest for the object.
	**/
	public void writeManifest( String objectID, byte[] data )
		throws FileStoreException;

	/**
	 * Write the contents of an input stream to a file.
	**/
	public void write( String objectID, String componentID, String fileID,
		InputStream in ) throws FileStoreException;

	/**
	 * Close any resources associated with this store.
	**/
	public void close() throws FileStoreException;

	/**
	 * Remove a file from an object and move it to the trash.
	**/
	public void trash( String objectID, String componentID, String fileID )
		throws FileStoreException;

	/**
	 * Get the organization code.
	**/
	public String orgCode() throws FileStoreException;

	/**
	 * Get the full path for a file, useful for logging or debugging.
	**/
	public String getPath( String objectID, String componentID, String fileID )
		throws FileStoreException;
}
