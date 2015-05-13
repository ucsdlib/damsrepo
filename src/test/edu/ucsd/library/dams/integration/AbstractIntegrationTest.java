package edu.ucsd.library.dams.integration;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;

public abstract class AbstractIntegrationTest
{
	private static HttpClient client;

	protected static String repoHost;
	protected static int repoPort;
	protected static String repoUser;
	protected static String repoPass;

	public static HttpClient getClient()
	{
		DefaultHttpClient defaultClient  = new DefaultHttpClient();

		// setup auth
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
			new AuthScope(repoHost, repoPort),
			new UsernamePasswordCredentials(repoUser, repoPass)
		);
		defaultClient.setCredentialsProvider(credsProvider);
		return defaultClient;
	}
	public static HttpEntity toEntity( Map<String,String> params )
	{
		HttpEntity entity = null;
		try
		{
			// convert map to List<NameValuePair>
			List<NameValuePair> paramList = new ArrayList<NameValuePair>();
			for ( Iterator<String> it = params.keySet().iterator(); it.hasNext(); )
			{
				String key = it.next();
				paramList.add( new BasicNameValuePair(key, params.get(key)) );
			}
			entity = new UrlEncodedFormEntity(paramList);
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		return entity;
	}
	public static HttpResponse exec( HttpRequestBase request )
	{
		HttpResponse response = null;
		try
		{
			if ( client  == null ) { client = getClient(); }
			response = client.execute( request );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		return response;
	}
	public static String execAndGetBody( HttpRequestBase request )
	{
		HttpResponse response = exec(request);
		String body = getBody(response);
		request.releaseConnection();
		return body;
	}
	public static void execAndCleanup( HttpRequestBase request )
	{
		exec(request);
		request.releaseConnection();
	}
	public static String getBody( HttpResponse response )
	{
		String body = null;
		try
		{
			body = EntityUtils.toString( response.getEntity() );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		return body;
	}
	public static String urlencode( String s )
	{
		String enc = null;
		try 
		{
			enc = URLEncoder.encode( s, "UTF-8" );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		return enc;
	}
}
