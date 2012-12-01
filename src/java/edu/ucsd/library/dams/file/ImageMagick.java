package edu.ucsd.library.dams.file;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.ArrayList;

import edu.ucsd.library.dams.file.impl.LocalStore;

/**
 * Interface to generate derivatives with ImageMagic.
 * @see http://www.imagemagick.org
 * @author escowles@ucsd.edu
 * @author lsitu@ucsd.edu
**/
public class ImageMagick
{
	private Runtime runtime = null;
	private String magick = null;

	/**
	 * Create an ImageMagick object.
	 * @param magickCommand Full path to the locally-installed ImageMagick
	 *  convert command (typically /usr/bin/convert, /usr/local/bin/convert,
	 *  etc.).
	**/
	public ImageMagick( String magickCommand )
	{
		this.magick = magickCommand;
		if ( !(new File(magickCommand)).exists() )
		{
			throw new IllegalArgumentException(
				"Can't find magick: " + magickCommand
			);
		}
		this.runtime = Runtime.getRuntime();
	}
	
	/**
 	 * Generate a derivative image.
	 * @param fs FileStore to retrive source image from, and store generated
	 *   derivative image in.
	 * @param objectID Object identifier.
	 * @param masterID File identifier for master image.
	 * @param derivID File identifier for generated derivative.
	 * @param width Width, in pixels of derivative image.
	 * @param height Height, in pixels of derivative image.
	 * @throws FileStoreException if an error occurs retrieving master file,
	 *   generating derivative, or storing derivative file.
	**/
	public boolean makeDerivative( FileStore fs, String objectID, String compID,
		String masterID, String derivID, int width, int height)
		throws FileStoreException
	{
		return makeDerivative( fs, objectID, compID,
				masterID, derivID,  width, height, 0 );
	}

	/**
 	 * Generate a derivative image.
	 * @param fs FileStore to retrive source image from, and store generated
	 *   derivative image in.
	 * @param objectID Object identifier.
	 * @param masterID File identifier for master image.
	 * @param derivID File identifier for generated derivative.
	 * @param width Width, in pixels of derivative image.
	 * @param height Height, in pixels of derivative image.
	 * @throws FileStoreException if an error occurs retrieving master file,
	 *   generating derivative, or storing derivative file.
	**/
	public boolean makeDerivative( FileStore fs, String objectID, String compID,
		String masterID, String derivID, int width, int height, int frameNo )
		throws FileStoreException
	{
		boolean status = false;
		try
		{
			File src = null;
			boolean deleteSrc = false;
			//String[] masterFileParts = masterID.split("-", 2);
			//String[] derivFileParts = derivID.split("-", 2);
			if ( fs instanceof LocalStore )
			{
				// files already local, don't need to retrieve
				LocalStore local = (LocalStore)fs;
				src = local.getFile( objectID, compID, masterID );
			}
			else
			{
				// need to retrieve file to local disk
				src = File.createTempFile("magicktmp",masterID);
				deleteSrc = true;
				FileOutputStream fos = new FileOutputStream(src);
				fs.read( objectID, compID, masterID, fos );
				fos.close();
			}

			File dst = File.createTempFile("magicktmp",derivID);
			boolean gen = makeDerivative( src, dst, width, height, frameNo );
			if ( gen )
			{
				// load deriv into fs
				FileInputStream fis = new FileInputStream(dst);
				fs.write( objectID, compID, derivID, fis );
				status = true;
				fis.close();

				// cleanup temp files (trap ex to prevent errors cleaning up
				// from throwing away good output
				try
				{
					dst.delete();
					if ( deleteSrc ) { src.delete(); }
				}
				catch ( Exception ex2 )
				{
					ex2.printStackTrace();
				}
			}
		}
		catch ( FileStoreException ex )
		{
			status = false;
			throw ex;
		}
		catch ( Exception ex )
		{
			status = false;
		}

		// return status
		return status;
	}
	
	/* generate a derivative image for a specific page using image magick */
	private boolean makeDerivative( File src, File dst, int width, int height, int frameNo )
		throws FileStoreException
	{
		// build the command
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add( magick );
		cmd.add( "-auto-orient" ); // auto-rotate images according to metadata
		//cmd.add( "-trim" );        // remove whitespace
		cmd.add( "+profile" );     // remove EXIF, etc. metadata
		cmd.add( "'*'" );
		cmd.add( "-resize" );      // resize to specified pixel dimensions
		cmd.add( width + "x" + height );
		cmd.add( src.getAbsolutePath() + (frameNo!=-1?"[" + frameNo + "]":"") );
		cmd.add( dst.getAbsolutePath() );

		StringBuffer log = new StringBuffer();
		Reader reader = null;
		InputStream in = null;
		BufferedReader buf = null;
		Process proc = null;
		try
		{
			// execute the process and capture stdout messages
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			proc = pb.start();
			
			in = proc.getInputStream();
			reader = new InputStreamReader(in);
			buf = new BufferedReader(reader);
			for ( String line = null; (line=buf.readLine()) != null; )
			{
				log.append( line + "\n" );
			}
			in.close();
			reader.close();
			buf.close();
			in = null;
			reader = null;
			buf = null;
			// wait for the process to finish
			int status = proc.waitFor();
			if ( status == 0 )
			{
				return true;
			}
			else
			{
				// capture any error messages
				in = proc.getErrorStream();
				reader = new InputStreamReader(in);
				buf = new BufferedReader(reader);
				for ( String line = null; (line=buf.readLine()) != null; )
				{
					log.append( line + "\n" );
				}
				throw new FileStoreException( log.toString() );
			}
		}
		catch ( Exception ex )
		{
			throw new FileStoreException( log.toString(), ex );
		}finally{
			if(in != null){
				try {
					in.close();
					in = null;
				} catch (IOException e) {}
			}
			if(reader != null){
				try {
					reader.close();
					reader = null;
				} catch (IOException e) {}
			}
			if(buf != null){
				try {
					buf.close();
					buf = null;
				} catch (IOException e) {}
			}
			if(proc != null){
				proc.destroy();
				proc = null;
			}
		}
	}
}
