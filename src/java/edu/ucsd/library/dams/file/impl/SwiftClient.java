package edu.ucsd.library.dams.file.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// for content type detection
import javax.activation.FileDataSource;
import javax.security.auth.login.LoginException;

// ssl
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

// http client 4.x
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.ContentType;

// local
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreAuthException;
import edu.ucsd.library.dams.file.FileStoreUtil;
import edu.ucsd.library.dams.util.HttpUtil;

/**
 * OpenStack Storage Client
 * @see http://docs.rackspace.com/files/api/v1/cf-devguide/content/index.html
 * @author escowles@ucsd.edu
**/
public class SwiftClient
{
	private Properties props  = null; // properties file holding account info
	private String authToken  = null; // authentication token
	private String storageURL = null; // default storage URL
	private DefaultHttpClient client = null; // shared httpclient instance
	private PrintStream out   = null; // message/debug output
	private NumberFormat nf   = null; // format segment names

	// segmented input stream params
	private static long SEGMENT_SIZE = 1073741824L; // 1 GB
	private String manifestContainer = null;
	private String manifestObject = null;
	private int manifestCount = 0;

	/**
	 * Create SwiftClient object and authenticate using the auth and account
	 *   information provided.
	 * @param props Properties object containing authUser, authToken and
	 *   authURL parameters.
	 * @param out PrintStream to send logging and/or error messages to.
	**/
	public SwiftClient( Properties props, PrintStream out )
		throws FileStoreException, IOException
	{
		this.props = props;
		this.out = out;


		// accept all SSL certs
        ClientConnectionManager ccm = new PoolingClientConnectionManager();
		try
		{
        	X509TrustManager tm = new X509TrustManager() {
            	public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException { }
            	public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException { }
            	public X509Certificate[] getAcceptedIssuers() { return null; }
        	};

			SSLContext ctx = SSLContext.getInstance("TLS");
        	ctx.init(null, new TrustManager[]{tm}, null);

        	SSLSocketFactory ssf = new SSLSocketFactory(ctx,SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        	SchemeRegistry sr = ccm.getSchemeRegistry();
        	sr.register(new Scheme("https", 443, ssf));

			//ctx.init(new KeyManager[0], new TrustManager[] {tm}, new SecureRandom());
			SSLContext.setDefault(ctx);
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}

		// disable timeouts
		BasicHttpParams params = new BasicHttpParams();
		params.setParameter( "http.socket.timeout",     new Integer(0) );
		params.setParameter( "http.connection.timeout", new Integer(0) );
      
		client = new DefaultHttpClient( ccm, params );

		// disable retries
		DefaultHttpRequestRetryHandler x = new DefaultHttpRequestRetryHandler(
			0, false
		);
		client.setHttpRequestRetryHandler( x );

		String user = props.getProperty("username");
		String pass = props.getProperty("password");
		String authURL = props.getProperty("authURL");

		HttpGet get = new HttpGet( authURL );
		get.addHeader( "X-Auth-User", user );
		get.addHeader( "X-Auth-Key", pass );

		// exec
		try
		{
			HttpUtil http = new HttpUtil( client, get );
			int status = http.exec();

			// check status
			if ( status == 200 )
			{
				authToken = http.responseHeaderValue("X-Auth-Token");
				storageURL = http.responseHeaderValue("X-Storage-Url");
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			get.reset();
		}

		// init
		nf = NumberFormat.getIntegerInstance();
		nf.setMinimumIntegerDigits(4);
		nf.setGroupingUsed(false);

		//message("auth: OK");
		//message("  Auth Token.: " + authToken );
		//message("  Storage URL: " + storageURL);
	}

	/**
	 * Create a SwiftClient object using the provided authentication token and
	 *   storage URL.
	 * @param authToken Authentication token obtained from Swift login.
	 * @param storageURL URL for Swift REST API.
	 * @param out PrintStream to send logging and/or error messages to.
	**/
	public SwiftClient( String authToken, String storageURL, PrintStream out )
	{
		this.authToken = authToken;
		this.storageURL = storageURL;
		this.out = out;

		// init
		nf = NumberFormat.getIntegerInstance();
		nf.setMinimumIntegerDigits(4);
		nf.setGroupingUsed(false);
	}

	/**
	 * Output a message.
	**/
	private void message( String s ) throws IOException
	{
		if ( out != null )
		{
			out.println(s);
			out.flush();
		}
	}

	/**
	 * Test whether a container or object exists.
	**/
	public boolean exists( String container, String object )
		throws FileStoreException, IOException
	{
		boolean exists = false;

		String url = storageURL;
		if ( container != null && !container.trim().equals("") )
		{
			url += "/" + container;
			if ( object != null )
			{
				url += "/" + object;
			}
		}
		else
		{
			throw new FileStoreException(
				"container parameter must be specified"
			);
		}

		HttpHead head = new HttpHead(url);
		head.addHeader( "X-Auth-Token", authToken );

		int status = -1;
		try
		{
			HttpUtil http = new HttpUtil( client, head );
			status = http.exec();
			if ( status == 200 || status == 204 )
			{
				exists = true;
			}
			else if ( status == 404 )
			{
				exists = false;
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			head.reset();
		}
		return exists;
	}

	/**
	 * Get a list of containers.
	 * @param user If not null, list containers owned by the specified user.
	**/
	public List<String> listContainers( String user )
		throws FileStoreException, IOException
	{
		String url = storageURL;
		if ( user != null )
		{
			// if the user param is specified, list for another user
			url = url.substring(0, url.indexOf("_")) + "_" + user;
		}
		return get( url );
	}

	/**
	 * Get a list of objects in a container.
	 * @param user If not null, list container owned by the specified user.
	**/
	public List<String> listObjects( String user, String container,
		String path ) throws FileStoreException, IOException
	{
		return listObjects( user, container, path, null );
	}
	/**
	 * Get a list of objects in a container.
	 * @param user If not null, list container owned by the specified user.
	**/
	public List<String> listObjects( String user, String container,
		String path, String marker ) throws FileStoreException, IOException
	{
		String url = storageURL;
		if ( user != null )
		{
			// if the user param is specified, list for another user
			url = url.substring(0, url.indexOf("_")) + "_" + user;
		}
		url += "/" + container;
		if ( path != null ) { url += "?prefix=" + path + "/&delimiter=/"; }
		if ( marker != null )
		{
			if ( path == null ) { url += "?"; } else { url += "&"; }
			url += "marker=" + URLEncoder.encode(marker,"UTF-8");
		}
		return get( url );
	}

	/**
	 * Retrieve a URL and output the results.
	**/
	private List<String> get( String url )
		throws FileStoreException, IOException
	{
		message("url: " + url);
		HttpGet get = new HttpGet(url);
		get.addHeader( "X-Auth-Token", authToken );
		int status = -1;
		List<String> list = null;
		try
		{
			HttpUtil http = new HttpUtil( client, get );
			status = http.exec();
			if ( status == 200 )
			{
				list = http.toList();
			}
			else if ( status == 204 )
			{
				list = new ArrayList<String>();
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			get.reset();
		}
		return list;
	}

	/**
	 * Retrieve a file and save it to disk.
	**/
	public void download( String user, String container, String object )
		throws FileStoreException, IOException
	{
		InputStream in = read( user, container, object );
		String fn = container + "-" + object;
		BufferedOutputStream out = new BufferedOutputStream(
			new FileOutputStream(fn)
		);
		long bytesRead = -1L;
		try
		{
			bytesRead = FileStoreUtil.copy( in, out );
		}
		catch ( Exception ex )
		{
			IOException ioex = null;
			if ( ex.getCause() instanceof IOException )
			{
				ioex = (IOException)ex.getCause();
			}
			else
			{
				ioex = new IOException( ex.toString() );
				ioex.initCause( ex );
			}
			throw ioex;
		}
		in.close();
		message( bytesRead + " bytes written to " + fn );
	}
	public InputStream read( String user, String container, String object )
		throws FileStoreException, IOException
	{
		String url = storageURL;
		if ( user != null )
		{
			// if the user param is specified, list for another user
			url = url.substring(0, url.indexOf("_")) + "_" + user;
		}
		url += "/" + container + "/" + object;
		String fn = object.replaceAll("/","-");

		message("url: " + url);
		HttpGet get = new HttpGet(url);
		get.addHeader( "X-Auth-Token", authToken );
		int status = -1;
		HttpUtil http = new HttpUtil( client, get );
		status = http.exec();
		InputStream in = null;
		if ( status == 200 )
		{
			in = new HttpInputStream(http.getResponseBodyAsStream(), get);
		}
		else
		{
			try { http.debug(out); }
			catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		}
		return in;
	}

	public int uploadSegmented( String container, String object,
		InputStream in, long len ) throws IOException
	{
		try
		{
			SegmentedInputStream sis = new SegmentedInputStream(
				in, SEGMENT_SIZE
			);
			String contentType = new FileDataSource(object).getContentType();
			Map<String,String> metadata = new HashMap<String,String>();
			for ( int i = 0; sis != null; i++ )
			{
				long thisLen = Math.min( SEGMENT_SIZE, len - (i*SEGMENT_SIZE) );
				String segName = object + "/" + nf.format(i);
				if ( exists(container, segName) )
				{
					// skip existing segments
					message(segName + ": already exists, skipping");
					long start = System.currentTimeMillis();
					sis.skip( thisLen );
					long dur = System.currentTimeMillis() - start;
					message( "### skip time: " + dur );
				}
				else
				{
					message(segName + ": " + thisLen);
					long start = System.currentTimeMillis();
					upload( container, segName, sis, thisLen );
					long dur = System.currentTimeMillis() - start;
					message( "### upload time: " + dur );
				}

				// check whether we've reached the end of the stream
				if ( ! sis.exhausted() || (i+1)*SEGMENT_SIZE == len )
				{
					sis.close(true); sis = null;
				}
				else
				{
					sis.clear();
				}
			}
			
	
			// create manifest joining the segments into virtual file
			return manifest( container, object );
		}
		catch ( Exception ex )
		{
			throw new IOException(ex);
		}
	}

	/**
	 * Upload a file.
	**/
	public int upload( String container, String object, InputStream in,
		long len ) throws FileStoreException, IOException
	{
		// make sure the container exists
		createContainer( container );

		String url = storageURL + "/" + container + "/" + object;
		HttpPut put = new HttpPut( url );
		put.addHeader( "X-Auth-Token", authToken );
		String contentType = new FileDataSource(object).getContentType();
		InputStreamEntity ent = new InputStreamEntity(
			in, len, ContentType.create(contentType)
		);
		put.setEntity(ent);
		int status = -1;
		try
		{
			//HttpUtil http = new HttpUtil( client, put );
			//status = http.exec();
			HttpClient client = new DefaultHttpClient();
			HttpResponse response = client.execute(put);
			status = response.getStatusLine().getStatusCode();
			if ( status == 201 )
			{
				message( "uploaded " + container + "/" + object );
			}
			else if ( status == 401 || status == 403 )
			{
				throw new FileStoreAuthException(
					new LoginException("HTTP Status: " + status)
				);
			}
			else
			{
				//http.debug(out);
				out.println("Error uploading file");
			}
		}
		finally
		{
			put.reset();
		}
		return status;
	}

	/**
	 * Server-side copy.
	**/
	public int copy( String srcContainer, String srcObject, String dstContainer,
		String dstObject ) throws FileStoreException, IOException
	{
		// get source file size
		Map<String,String> md = stat(srcContainer,srcObject);
		long len = Long.parseLong( md.get("Content-Length") );

		// create manifest if a complete multi-segment upload is done
		manifestAfterSegments( dstContainer, dstObject );

		int status = -1;
		if ( len > SEGMENT_SIZE )
		{
			// don't copy, just need to create manifest, but after segments
			manifestObject = dstObject;
			manifestContainer = dstContainer;
			message("multipart object, waiting for segments: " + dstContainer + "/" +dstObject);
			status = 202; // Accepted == will try to complete later...
		}
		else
		{
			String url = storageURL + "/" + dstContainer + "/" + dstObject;
			HttpPut put = new HttpPut( url );
			put.addHeader( "X-Auth-Token", authToken );
			put.addHeader( "X-Copy-From", "/" + srcContainer + "/" + srcObject);
			put.addHeader( "Content-Length" , "0" );
			try
			{
				HttpUtil http = new HttpUtil( client, put );
				status = http.exec();
				if ( status == 201 )
				{
					message(
						"copied " + srcContainer + "/" + srcObject + " to "
						  		+ dstContainer + "/" + dstObject
					);
				}
				else
				{
					http.debug(out);
				}
			}
			catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
			finally
			{
				put.reset();
			}
		}
		return status;
	}

	/**
	 * Create the manifest for any multi-part uploads that are completed.
	**/
	public void manifestAfterSegments( String dstContainer, String dstObject )
		throws FileStoreException, IOException
	{
		if ( manifestContainer != null && manifestObject != null )
		{
			if ( dstContainer != null && dstObject != null
					&& dstContainer.equals(manifestContainer)
					&& dstObject.startsWith(manifestObject) )
			{
				manifestCount++;
			}
			else
			{
				int status = manifest( manifestContainer, manifestObject );
				if ( status == 201 )
				{
					message(
						"manifest created for " + manifestCount + " segments"
					);
				}
				else
				{
					message( "manifest creation failed" );
				}
				manifestContainer = null;
				manifestObject = null;
				manifestCount = 0;
			}
		}
	}

	/**
	 * Multi-part manifest.
	**/
	public int manifest( String container, String object )
		throws FileStoreException, IOException
	{
		String url = storageURL + "/" + container + "/" + object;
		HttpPut put = new HttpPut( url );
		put.addHeader( "X-Auth-Token", authToken );
		put.addHeader( "X-Object-Manifest", container + "/" + object + "/");
		//XXX: rethink pattern to move segments into dedicated container???
		int status = -1;
		try
		{
			HttpUtil http = new HttpUtil( client, put );
			status = http.exec();
			if ( status == 201 )
			{
				message( "created manifest " + container + "/" + object + "/" );
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			put.reset();
		}
		return status;
	}

	/**
	 * Create a container.
	**/
	public int createContainer( String container )
		throws FileStoreException, IOException
	{
		String url = storageURL + "/" + container;
		HttpPut put = new HttpPut( url );
		put.addHeader( "X-Auth-Token", authToken );
		int status = -1;
		try
		{
			HttpUtil http = new HttpUtil( client, put );
			status = http.exec();
			if ( status == 201 )
			{
				message( "created: " + container );
			}
			else if ( status == 202 )
			{
				message( "already existed: " + container );
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			put.reset();
		}
		return status;
	}

	/**
	 * Delete a container.
	**/
	public int deleteContainer( String container )
		throws FileStoreException, IOException
	{
		String url = storageURL + "/" + container;
		HttpDelete del = new HttpDelete( url );
		del.addHeader( "X-Auth-Token", authToken );
		int status = -1;
		try
		{
			HttpUtil http = new HttpUtil( client, del );
			status = http.exec();
			if ( status == 204 )
			{
				message( "deleted: " + container );
			}
			else if ( status == 404 )
			{
				message( "did not exist: " + container );
			}
			else if ( status == 409 )
			{
				message( "not empty: " + container );
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			del.reset();
		}
		return status;
	}

	/**
	 * Delete a container or an object.
	 * @param object If not null, delete this object.
	**/
	public int delete( String container, String object )
		throws FileStoreException, IOException
	{
		String url = storageURL + "/" + container;
		if ( object != null ) { url += "/" + object; }
		HttpDelete del = new HttpDelete( url );
		del.addHeader( "X-Auth-Token", authToken );
		int status = -1;
		try
		{
			HttpUtil http = new HttpUtil( client, del );
			status = http.exec();
			if ( status == 204 )
			{
				message("deleted " + container + "/" + object);
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			del.reset();
		}
		return status;
	}

	/**
	 * Get metadata for an account, container or object.  If container and
	 *   object are both specified, get info for an object.  If only container
	 *   is specified, get info for the container.  If both are null, get info
	 *   for the account.
	**/
	public Map<String,String> stat( String container, String object )
		throws FileStoreException, IOException
	{
		Map<String,String> values = null;
		String url = storageURL;
		if ( container != null )
		{
			url += "/" + container;
			if ( object != null )
			{
				url += "/" + object;
			}
		}

		HttpHead head = new HttpHead(url);
		head.addHeader( "X-Auth-Token", authToken );
		try
		{
			HttpUtil http = new HttpUtil( client, head );
			int status = http.exec();
			if ( status == 200 || status == 204 )
			{
				if ( container != null && object != null )
				{
					// object metadata
					values = http.getHeaders( null );
				}
				else if ( container != null )
				{
					// container metadata
					values = http.getHeaders( "X-Container-" );
				}
				else
				{
					// just account metadata
					values = http.getHeaders( "X-Account-" );
				}
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			head.reset();
		}
		return values;
	}

	/**
	 * Set or update metadata for a container or object.  If object is not null,
	 *   update metadata for the specified object.  Otherwise, update metadata
	 *   for the specified container.
	**/
	public int meta( String container, String object, Map<String,String> meta )
		throws FileStoreException, IOException
	{
		String url = storageURL;
		String type = "Account";
		if ( container != null && !container.trim().equals("") && !container.equals("-") )
		{
			url += "/" + container;
			if ( object != null && !object.trim().equals("") && !object.equals("-") )
			{
				url += "/" + object;
				type = "Object";
			}
			else
			{
				type = "Container";
			}
		}
		HttpPost post = new HttpPost(url);
		post.addHeader( "X-Auth-Token", authToken );
		for ( Iterator<String> it = meta.keySet().iterator(); it.hasNext(); )
		{
			String key = it.next();
			String val = meta.get( key );
			post.addHeader( "X-" + type + "-Meta-" + key, val );
		}
		int status = -1;
		try
		{
			HttpUtil http = new HttpUtil( client, post );
			status = http.exec();
			if ( status == 202 )
			{
				message("updated: " + container + "/" + object);
			}
			else if ( status == 404 )
			{
				message("does not exist: " + container + "/" + object);
			}
			else
			{
				http.debug(out);
			}
		}
		catch ( LoginException ex ) { throw new FileStoreAuthException(ex); }
		finally
		{
			post.reset();
		}
		return status;
	}

	/* command-line operation */
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		SwiftClient swift = new SwiftClient( props, System.out );

		String op = args[1];
		if ( op.equals("stat") )
		{
			String container = null;
			String object = null;
			if ( args.length > 2 ) { container = args[2]; }
			if ( args.length > 3 ) { object = args[3]; }
			swift.output( swift.stat( container, object ) );
		}
		else if ( op.equals("listContainers") )
		{
			String user = null;
			if ( args.length > 2 ) { user = args[2]; }
			swift.output( swift.listContainers(user) );
		}
		else if ( op.equals("createContainer") )
		{
			String container = args[2];
			swift.output( swift.createContainer(container) );
		}
		else if ( op.equals("deleteContainer") )
		{
			String container = args[2];
			swift.output( swift.deleteContainer(container) );
		}
		else if ( op.equals("listObjects") )
		{
			String user = null;
			String container = null;
			String path = null;
			if ( args.length > 4 )
			{
				user = args[2];
				container = args[3];
				path = args[4];
			}
			else if ( args.length > 3 )
			{
				user = args[2];
				container = args[3];
			}
			else if ( args.length > 2 )
			{
				container = args[2];
			}
			else
			{
				System.err.println("Not enough arguments: must be 1-3");
				System.exit(1);
			}
			String mrk = swift.output(swift.listObjects(user,container,path));
			while ( mrk != null )
			{
				mrk = swift.output(swift.listObjects(user,container,path,mrk));
			}
		}
		else if ( op.equals("download") )
		{
			String user = null;
			String container = null;
			String object = null;
			if ( args.length > 4 )
			{
				user = args[2];
				container = args[3];
				object = args[4];
			}
			else if ( args.length > 3 )
			{
				container = args[2];
				object = args[3];
			}
			else
			{
				System.err.println("Not enough arguments: must be 2 or 3");
				System.exit(1);
			}
			swift.download( user, container, object );
		}
		else if ( op.equals("copy") )
		{
			String srcContainer = null;
			String srcObject    = null;
			String dstContainer = null;
			String dstObject    = null;
			if ( args.length > 5 )
			{
				srcContainer = args[2];
				srcObject    = args[3];
				dstContainer = args[4];
				dstObject    = args[5];
				swift.output(
					swift.copy(srcContainer, srcObject, dstContainer, dstObject)
				);
			}
			else
			{
				System.err.println("Not enough arguments: must be 4");
			}
		}
		else if ( op.equals("move") )
		{
			String srcContainer = null;
			String srcObject    = null;
			String dstContainer = null;
			String dstObject    = null;
			if ( args.length > 5 )
			{
				srcContainer = args[2];
				srcObject    = args[3];
				dstContainer = args[4];
				dstObject    = args[5];
				swift.output(
					swift.copy(srcContainer, srcObject, dstContainer, dstObject)
				);
				swift.output(swift.delete( srcContainer, srcObject ) );
			}
			else
			{
				System.err.println("Not enough arguments: must be 4");
			}
		}
		else if ( op.equals("upload") )
		{
			String container = null;
			String object    = null;
			long len         = -1L;
			InputStream in   = null;
			if ( args.length > 3 )
			{
				container = args[2];
				object    = args[3];
				if ( args.length > 4 )
				{
					in = new FileInputStream(args[4]);
					len = new File(args[4]).length();
				}
				else
				{
					in = new FileInputStream(args[3]);
					len = new File(args[3]).length();
				}
				if ( len > SEGMENT_SIZE )
				{
					swift.output( swift.uploadSegmented(container,object,in,len) );
				}
				else
				{
					swift.output( swift.upload(container,object,in,len) );
				}
			}
			else
			{
				System.err.println("Not enough arguments: must be 2 or 3");
			}
		}
		else if ( op.equals("delete") )
		{
			String container = null;
			String object = null;
			if ( args.length > 3 ) { object = args[3]; }
			if ( args.length > 2 )
			{
				container = args[2];
				swift.output(swift.delete( container, object ) );
			}
			else
			{
				System.err.println("Not enough arguments: must be 1 or 2");
			}
		}
		else if ( op.equals("meta") )
		{
			String container = null;
			String object = null;
			Map<String,String> meta = new HashMap<String,String>();
			if ( args.length > 5 )
			{
				container = args[2];
				object    = args[3];
				for ( int i = 4; (i+1) < args.length; i += 2 )
				{
					meta.put( args[i], args[i+1] );
				}
				swift.output(swift.meta( container, object, meta ) );
			}
			else
			{
				System.err.println("Not enough arguments: must be at least 4");
			}
		}
		else
		{
			System.err.println("unknown operation: " + op);
		}
	}
	private void output( int i ) throws IOException
	{
		message("status: " + i);
	}
	private String output( List<String> messages ) throws IOException
	{
		String last = null;
		for ( int i = 0; i < messages.size(); i++ )
		{
			message( messages.get(i) );
			if ( (i+1) == messages.size() ) { last = messages.get(i); }
		}
		return last;
	}
	private void output( Map<String,String> messages ) throws IOException
	{
		Iterator<String> keys = messages.keySet().iterator();
		while ( keys.hasNext() )
		{
			String key = keys.next();
			message( key + ": " + messages.get( key ) );
		}
	}
}
