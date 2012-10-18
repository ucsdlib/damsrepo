package edu.ucsd.library.dams.file.impl;

import java.io.IOException;
import java.io.InputStream;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * Wrapper class for InputStream objects obtained from HttpClient, which 
 * releases the associated HTTP connection when the stream is closed.
 * @author escowles
**/
public class HttpInputStream extends InputStream
{
	private InputStream in = null;
	private HttpRequestBase reqBase = null;

	public HttpInputStream( InputStream in, HttpRequestBase reqBase )
	{
		this.in = in;
		this.reqBase = reqBase;
	}

	/***** updated close() method ********************************************/
	public void close() throws IOException
	{
		try
		{
			// close underlying buffer
			in.close();
		}
		finally
		{
			// release httpclient connection
			reqBase.releaseConnection();
		}
	}

	/***** InputStream impl. *************************************************/
	public int available() throws IOException { return in.available(); }
	public void mark( int readlimit ) { in.mark(readlimit); }
	public boolean markSupported() { return in.markSupported(); }
	public int read() throws IOException { return in.read(); }
	public int read( byte[] b ) throws IOException { return in.read(b); }
	public int read( byte[] b, int off, int len ) throws IOException { return in.read(b,off,len); }
	public void reset() throws IOException { in.reset(); }
	public long skip( long n ) throws IOException { return in.skip(n); }
}
