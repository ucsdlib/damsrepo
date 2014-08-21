package edu.ucsd.library.dams.api;

// java core api
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

// servlet api
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// httpclient
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;

// logging
import org.apache.log4j.Logger;

// dams
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreUtil;


/**
 * Proxy servlet for debugging REST API usage.
 * @author escowles@ucsd.edu
 * @since 2014-07-22
**/
public class ProxyServlet extends HttpServlet
{
	private static Logger log = Logger.getLogger(ProxyServlet.class);
	private String baseURL = null;
	private boolean logRequests;
	private boolean logHeaders;
	private boolean logContent;
	private HttpClient client = null;

	// initialize servlet parameters
	public void init( ServletConfig config ) throws ServletException
	{
		baseURL = config.getInitParameter("baseURL");
		logRequests = booleanParam( config, "logRequests" );
		logHeaders = booleanParam( config, "logHeaders" );
		logContent = booleanParam( config, "logContent" );
        ClientConnectionManager pool = new PoolingClientConnectionManager();
		client = new DefaultHttpClient( pool );
	}
	private static boolean booleanParam( ServletConfig config, String name )
	{
		String val = config.getInitParameter(name);
		return val != null && val.equals("true");
	}


	//========================================================================
	// REST API methods
	//========================================================================

	public void doHead( HttpServletRequest req, HttpServletResponse res )
	{
		try
		{
			HttpHead head = new HttpHead( proxyURL(req) );
			copyRequestHeaders( req, head );
			proxy( head, res );
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing HEAD request", ex );
		}
	}
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		try
		{
			HttpGet get = new HttpGet( proxyURL(req) );
			copyRequestHeaders( req, get );
			proxy( get, res );
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing GET request", ex );
		}
	}
	public void doDelete( HttpServletRequest req, HttpServletResponse res )
	{
		try
		{
			HttpDelete del = new HttpDelete( proxyURL(req) );
			copyRequestHeaders( req, del );
			proxy( del, res );
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing DELETE request", ex );
		}
	}

	public void doPost( HttpServletRequest req, HttpServletResponse res )
	{
		try
		{
			HttpPost post = new HttpPost( proxyURL(req) );
			copyRequestHeaders( req, post );
			copyRequestEntity( req, post );
			proxy( post, res );
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing POST request", ex );
		}
	}
	public void doPut( HttpServletRequest req, HttpServletResponse res )
	{
		try
		{
			HttpPut put = new HttpPut( proxyURL(req) );
			copyRequestHeaders( req, put );
			copyRequestEntity( req, put );
			proxy( put, res );
		}
		catch ( Exception ex )
		{
			log.warn( "Error processing PUT request", ex );
		}
	}


	//========================================================================
	// Proxy implementation
	//========================================================================

	/**
	 * Return URL mapped to baseURL, with query string params
	**/
	private String proxyURL( HttpServletRequest req )
	{
		String relURL = req.getPathInfo();
		if ( req.getQueryString() != null )
		{
			relURL += "?" + req.getQueryString();
		}
		return baseURL + relURL;
	}

	/**
	 * Copy request headers to proxy request
	**/
	private void copyRequestHeaders( HttpServletRequest orig,
		HttpRequest proxy )
	{
		for ( Enumeration e = orig.getHeaderNames(); e.hasMoreElements(); )
		{
			String name = (String)e.nextElement();
			String value = orig.getHeader( name );
			if ( name.equalsIgnoreCase("Content-Length") )
			{
				log.info("supressing header: " + name + ": " + value);
			}
			else
			{
				proxy.setHeader( name, value );
				if ( logHeaders )
				{
					log.info( "    " + name + ": " + value );
				}
			}
		}
	}

	/**
	 * Copy the request entity to proxy request
	**/
	private void copyRequestEntity( HttpServletRequest orig,
		HttpEntityEnclosingRequest proxy ) throws IOException
	{
		long len = orig.getContentLength();
		if ( len > 0 )
		{
			InputStream in = orig.getInputStream();
			String inputType = orig.getContentType();
			log.info("request contentType:" + inputType);
			if ( logContent && inputType != null &&
				(inputType.startsWith("text/") || inputType.endsWith("/xml")) )
			{
				try
				{
					String content = FileStoreUtil.read(in);
					log.info("request entity: " + content);
					proxy.setEntity( new StringEntity(content) );
				}
				catch ( Exception ex ) { ex.printStackTrace(); }
			}
			else
			{
				proxy.setEntity( new InputStreamEntity(in, len) );
			}
		}
	}

	/**
	 * Perform request and send output to response
	**/
	private void proxy( HttpUriRequest req, HttpServletResponse res )
		throws FileStoreException, IOException
	{
		HttpResponse response = client.execute(req);
		int status = response.getStatusLine().getStatusCode();
		res.setStatus(status);
		if ( logRequests )
		{
			String uri = req.getURI().toString().substring( baseURL.length() );
			log.info(
				req.getMethod() + " " + uri + " " + status + " " +
				response.getStatusLine().getReasonPhrase()
			);
		}

		// headers
		Header[] headers = response.getAllHeaders();
		for ( int i = 0; i < headers.length; i++ )
		{
			Header h = headers[i];
			res.setHeader( h.getName(), h.getValue() );
			if ( logHeaders )
			{
				log.info( "    " + h.getName() + ": " + h.getValue() );
			}
		}

		// content
		HttpEntity entity = response.getEntity();
		if ( entity != null )
		{
			boolean textOutput = false;
			Header mimeTypeHeader = response.getFirstHeader("Content-Type");
			if ( mimeTypeHeader != null && mimeTypeHeader.getValue() != null )
			{
                String mimeType = mimeTypeHeader.getValue();
				res.setContentType( mimeType );
				if ( mimeType.startsWith("text/") || mimeType.endsWith("/xml") )
				{
					textOutput = true;
				}
			}
			OutputStream out = res.getOutputStream();
			if ( logContent && textOutput )
			{
				String content = FileStoreUtil.read(entity.getContent());
				log.info("response entity: " + content);
				FileStoreUtil.copy(
					new ByteArrayInputStream(content.getBytes()), out
				);
			}
			else
			{
				FileStoreUtil.copy( entity.getContent(), out );
			}
			out.close();
		}
	}

}
