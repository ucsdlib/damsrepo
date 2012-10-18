package edu.ucsd.library.dams.file.impl;

import java.io.InputStream;
import java.io.IOException;
import java.io.FilterInputStream;

/**
 * InputStream implementation that limits the number of bytes read to a
 * specified number, so a large stream of data can be read in smaller chunks.
 * @author escowles
**/
public class SegmentedInputStream extends FilterInputStream
{
	long segmentSize;
	long bytesRead;

	public SegmentedInputStream( InputStream in, long segmentSize )
	{
		super(in);
		this.segmentSize = segmentSize;
		this.bytesRead = 0L;
	}

	/**
	 * Reset the counter that tracks the number of bytes read this session.
	 * Must be called before the next segment can be read.
	**/
	public void clear()
	{
		bytesRead = 0L;
	}

	/**
	 * Report whether the stream has read as all of the allowed bytes.
	 * Note: This does not guarantee that there are more bytes to read.
	**/
	public boolean exhausted()
	{
		return (bytesRead == segmentSize);
	}

	public int read() throws IOException
	{
		// first, figure out if we can read enough bytes
		if ( bytesRead >= segmentSize )
		{
			// we're out of bytes
			return -1;
		}

		int c = super.read();
		if ( c != -1 ) { bytesRead++; }
		return c;
	}
	public int read( byte[] b, int off, int len ) throws IOException
	{
		// first, figure out if we can read enough bytes
		if ( bytesRead >= segmentSize )
		{
			// we're out of bytes
			return -1;
		}

		// limit the number of bytes to read to prevent overrun
		int req = Math.min(b.length - off, len);
		long bytesLeft = segmentSize - bytesRead;
		if ( bytesLeft < (long)req )
		{
			req = (int)bytesLeft;
		}

		// perform read and return number of bytes read
		int read = super.read( b, off, req );
		bytesRead += read;
		return read;
	}
	public long skip( long n ) throws IOException
	{
		// limit number of bytes to skip to prevent overrun
		long bytesLeft = segmentSize - bytesRead;
		long toSkip = Math.min( bytesLeft, n );

		// skip and return number of bytes skipped
		long skipped = super.skip(toSkip);
		bytesRead += skipped;
		return skipped;
	}
}
