package edu.ucsd.library.dams.file;

import java.io.Closeable;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import edu.ucsd.library.dams.file.impl.LocalStore;

/**
 * Interface to generate derivatives with Ffmpeg.
 * @see www.ffmpeg.org
 * @author tchu@ucsd.edu
 * 
**/
public class Ffmpeg
{
	private Runtime runtime = null;
	private String ffmpegCommand = null;

	/**
	 * Create an Ffmpeg object.
	 * @param ffmpegCommand Full path to the locally-installed Ffmpeg
	 *  convert command (typically /usr/bin/convert, /usr/local/bin/convert,
	 *  etc.).
	**/
	public Ffmpeg( String ffmpegCommand )
	{
		this.ffmpegCommand = ffmpegCommand;
		if ( !(new File(ffmpegCommand)).exists() )
		{
			throw new IllegalArgumentException(
				"Can't find Ffmpeg:: " + this.ffmpegCommand
			);
		}
		this.runtime = Runtime.getRuntime();
	}
	
	/**
 	 * Generate a derivative image.
	 * @param fs FileStore to retrive source audio file from, and store generated
	 *   derivative mp3 file in.
	 * @param objectID Object identifier.
	 * @param masterID File identifier for master audio file.
	 * @param derivID File identifier for generated derivative.
	 * @throws FileStoreException if an error occurs retrieving master file,
	 *   generating derivative, or storing derivative file.
	**/
	public boolean makeDerivative( FileStore fs, String objectID, String compID, String masterID, String derivID)
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
				src = File.createTempFile("ffmpegtmp",masterID);
				deleteSrc = true;
				FileOutputStream fos = new FileOutputStream(src);
				fs.read( objectID, compID, masterID, fos );
				fos.close();
			}

			File dst = File.createTempFile("ffmpegtmp",derivID);
			boolean gen = makeDerivative( src, dst);
			//boolean gen = createDerivative( src, dst);
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
	
	/* generate a derivative mp3 for a specific page using ffmpeg */
	private boolean makeDerivative( File src, File dst)
		throws FileStoreException
	{
		// build the command
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add( ffmpegCommand );
		cmd.add( "-i" );
		cmd.add( src.getAbsolutePath());
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
			ex.printStackTrace();
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

	/**
	 * create thumbnail for videos
	 * @param fs
	 * @param oid
	 * @param cid
	 * @param srcFid - source file id
	 * @param destFid - thumbnail files id
	 * @param scale - size like 150:-1, 450:-1, 768:-1 etc.
	 * @param offset - start position in seconds or 00:00:10.xxx format
	 * @return
	 * @throws Exception 
	 */
	public boolean createThumbnail (FileStore fs, String oid, String cid, String srcFid, String destFid, String scale, String offset) 
			throws Exception 
	{
		File src = ((LocalStore)fs).getFile( oid, cid, srcFid );
		File destTemp = File.createTempFile("ffmpegtmp",destFid);
		if (destTemp.exists())
			destTemp.delete();
		destTemp.deleteOnExit();

		// build the command
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add( ffmpegCommand );
		if (StringUtils.isNotBlank(offset)) {
			cmd.add( "-ss" );					// start point of the input video stream
			cmd.add( offset );
		}
		cmd.add( "-i" );
		cmd.add( src.getAbsolutePath());		// source video file
		cmd.add( "-vf" );						// video filter for thumbnail with scale
		cmd.add( "thumbnail,scale=" + scale );	
		cmd.add( "-vframes" );					// number of frames to extract
		cmd.add( "1" );
		cmd.add( destTemp.getAbsolutePath() );	// temporary thumbnail file
		boolean successful = exec( cmd );

		if ( destTemp.exists() && destTemp.length() > 0 ) {
			// write the thumbnail created to filestore
			FileInputStream fis = null;
			try{
				File dest = ((LocalStore)fs).getFile( oid, cid, destFid );
				if (dest.exists())
					dest.delete();
				fis = new FileInputStream(destTemp);
				fs.write( oid, cid, destFid, fis );
			}finally {
				close(fis);
			}
		}
		if(destTemp.exists())
			destTemp.delete();
		return successful;
	}

	private boolean exec(List<String> cmd) throws Exception 
	{
		InputStream in = null;
		InputStream inErr = null;
		Process proc = null;
		final StringBuffer log = new StringBuffer();
		int status = -1;
		try {
			// Execute the command
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			proc = pb.start();

			// dump metadata and errors
			in = proc.getInputStream();
			inErr = proc.getErrorStream();
			final BufferedReader buf = new BufferedReader(new InputStreamReader(in));
			final BufferedReader bufErr = new BufferedReader(new InputStreamReader(inErr));

			new Thread() {
				@Override
				public void run()
				{
					try {
						for ( String line = null; (line=bufErr.readLine()) != null; ) {
							log.append( line + "\n" );
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();

			new Thread() {
				@Override
				public void run()
				{
					try {
						for ( String line = null; (line=buf.readLine()) != null; ) {
							log.append( line + "\n" );
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();

			// Wait for the process to exit
			status = proc.waitFor();
			if ( status == 0 )
				return true;
			else
				throw new Exception( "Error status code: " + status);

		} catch ( Exception ex ) {
			throw new Exception( log.toString(), ex );
		} finally {
			close(in);
			close(inErr);
			if(proc != null){
				proc.destroy();
				proc = null;
			}
		}
	}

	public void close ( Closeable closeable ) {
		try {
			if (closeable != null)
				closeable.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
