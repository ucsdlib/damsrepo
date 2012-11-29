package edu.ucsd.library.dams.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


// checksumming
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;

/**
 * Utility to compute checksums of files.
 * @author escowles@ucsd.edu
**/
public class Checksum
{
	/**
	 * Generate CRC32, MD5 and/or SHA-1 checksums with a single file read.
	 * @param is InputStream object to read data from (unbuffered is OK)
	 * @param out OutputStream to copy data to, if not null.
	 * @param crcB If true, generate CRC-32 checksum.
	 * @param md5B If true, generate MD5 checksum.
	 * @param sha1B If true, generate SHA-1 checksum.
	 * @param sha256B If true, generate SHA-256 checksum.
	 * @param sha512B If true, generate SHA-512 checksum.
	 * @return Map of checksum algorithm names to checksum values.
	**/
	public static Map<String,String> checksums( InputStream is,
		OutputStream out, boolean crcB, boolean md5B, boolean sha1B,
		boolean sha256B, boolean sha512B ) throws IOException
	{
		Map<String,String> sums = new HashMap<String,String>();
		try
		{
			// setup digesters
			CheckedInputStream cis = null;
			MessageDigest md5digest = null;
			MessageDigest sha1digest = null;
			MessageDigest sha256digest = null;
			MessageDigest sha512digest = null;
			
			if ( crcB ) { cis = new CheckedInputStream( is, new CRC32() ); }
			if ( md5B ) { md5digest = MessageDigest.getInstance("MD5"); }
			if ( sha1B ) { sha1digest = MessageDigest.getInstance("SHA-1"); }
			if ( sha256B){ sha256digest = MessageDigest.getInstance("SHA-256");}
			if ( sha512B){ sha512digest = MessageDigest.getInstance("SHA-512");}

			InputStream i2 = null;
			if ( crcB ) { i2 = cis; } else { i2 = is; }

			// read data
			byte[] buf = new byte[8192];
			for ( int read = -1; (read = i2.read(buf)) >= 0; )
			{
				if ( md5B ) { md5digest.update( buf, 0, read ); }
				if ( sha1B ) { sha1digest.update( buf, 0, read ); }
				if ( sha256B ) { sha256digest.update( buf, 0, read ); }
				if ( sha512B ) { sha512digest.update( buf, 0, read ); }
				if ( out != null )
				{
					out.write( buf, 0, read );
				}
			}

			// convert to hex
			if ( crcB )
			{
				long checksum = cis.getChecksum().getValue();
				String crc32sum = Long.toString( checksum, 16 );
				while ( crc32sum.length() < 8 ) { crc32sum = "0" + crc32sum; }
				sums.put( "crc32", crc32sum );
			}
			if ( md5B )
			{
				sums.put( "md5", convertToHex(md5digest.digest()) );
			}
			if ( sha1B )
			{
				sums.put( "sha1", convertToHex(sha1digest.digest()) );
			}
			if ( sha256B )
			{
				sums.put( "sha256", convertToHex(sha256digest.digest()) );
			}
			if ( sha512B )
			{
				sums.put( "sha512", convertToHex(sha512digest.digest()) );
			}
		}
		catch (Exception e)
		{
			throw new IOException(
				"Error generating checksums", e
			);
		}
		finally
		{
			is.close();
			if ( out != null ) { out.close(); }
		}
		return sums;
	}
	/**
	 * Convert a byte array to hex string.
	**/
	private static String convertToHex(byte[] data)
	{
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++)
		{
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do
			{
				if ((0 <= halfbyte) && (halfbyte <= 9))
				{
					buf.append((char) ('0' + halfbyte));
				}
				else
				{
					buf.append((char) ('a' + (halfbyte - 10)));
				}
				halfbyte = data[i] & 0x0F;
			}
			while(two_halfs++ < 1);
		}
		return buf.toString();
	}

}
