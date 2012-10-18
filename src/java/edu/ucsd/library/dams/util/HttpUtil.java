package edu.ucsd.library.dams.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginException;

// http client 4.x
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

/**
 * Utility class to obscure HttpClient 4.x API.
 * @author escowles@ucsd.edu
**/
public class HttpUtil
{
	HttpClient client;
	HttpRequestBase request;
	HttpResponse response;

	public HttpUtil( String url )
	{
		this.client = new DefaultHttpClient();
		this.request = new HttpGet( url );
	}
	public HttpUtil( HttpClient client, HttpRequestBase request )
	{
		this.client = client;
		this.request = request;
	}

	public static String post( String url, String content, String mimeType,
		String encoding )
	{
		HttpPost post = new HttpPost( url );
		ContentType contentType = ContentType.create(mimeType,encoding);
		post.setEntity( new StringEntity(content,contentType) );
		return exec( post );
	}
	public static String get( String url )
	{
		HttpGet get = new HttpGet(url);
		return exec( get );
	}
	private static String exec( HttpRequestBase req )
	{
		HttpUtil http = new HttpUtil( new DefaultHttpClient(), req );
		http.exec();
		return http.contentBodyAsString();
	}
	public int exec()
	{
		response = client.execute( request );
		return status();
	}
	public int status()
	{
		return response.getStatusLine().getStatusCode();
	}
	public HttpRequestBase request()
	{
		return request;
	}
	public HttpResponse response()
	{
		return response;
	}
	public void releaseConnection()
	{
		request.releaseConnection();
	}

	/**
	 * Output response information.
	**/
	public void debug(PrintStream out) throws IOException
	{
		int status = response.getStatusLine().getStatusCode();

		if ( out != null )
		{
			out.println( request.getRequestLine() );
			Header[] headers = response.getAllHeaders();
			for ( int i = 0; i < headers.length; i++ )
			{
				out.print( headers[i] );
			}
			out.println( "  status: " + status );
			if ( !request.getMethod().equals("HEAD") )
			{
				List<String> values = toList();
				for ( int i = 0; i < values.size(); i++ )
				{
					out.print( values.get(i) );
				}
			}
			out.flush();
		}

		// signal if status is 401 (auth required) or 403 (forbidden)
		if ( status == 401 || status == 403 )
		{
			throw new LoginException("HTTP Status: " + status);
		}
		else
		{
			throw new IOException("HTTP Status: " + status);
		}
	}

	public InputStream getResponseBodyAsStream() throws IOException
	{
		return response.getEntity().getContent();
	}
	public String contentBodyAsString() throws IOException
	{
		BufferedReader in = null;
		StringBuffer buf = new StringBuffer();
		try
		{
			in = new BufferedReader(
				new InputStreamReader( getResponseBodyAsStream() )
			);;
			for ( String line = null; (line=in.readLine()) != null; )
			{
				buf.append( line );
			}
		}
		finally
		{
			if ( in != null ) { in.close(); }
		}
		return buf.toString();
	}

	/**
	 * Return the response body as a List of Strings.
	**/
	public List<String> toList() throws IOException
	{
		List<String> values = new ArrayList<String>();
		InputStream in = response.getEntity().getContent();
		if ( in != null )
		{
			BufferedReader buf = new BufferedReader(
				new InputStreamReader(in)
			);
			for ( String line = null; (line=buf.readLine()) != null; )
			{
				values.add( line );
			}
			buf.close();
		}
		return values;
	}

	/**
	 * Get the value of a response header.
	**/
	public String responseHeaderValue( String name )
	{
		Header h = response.getFirstHeader( name );
		return h.getValue();
	}

	/**
	 * Get all response headers that match start with a given substring
	 *  (if start is null, output all headers).
	**/
	public Map<String,String> getHeaders( String start )
	{
		Map<String,String> values = new HashMap<String,String>();
		Header[] headers = response.getAllHeaders();
		for ( int i = 0; i < headers.length; i++ )
		{
			String s = headers[i].getName().toLowerCase();
			if ( start == null || s.startsWith(start.toLowerCase()) )
			{
				values.put( headers[i].getName(), headers[i].getValue() );
			}
		}
		return values;
	}

}