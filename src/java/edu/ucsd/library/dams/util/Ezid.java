package edu.ucsd.library.dams.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

// http client
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;


/**
 * Utility for minting DOIs using EZID (http://ezid.cdlib.org/).
 * @author escowles
 * @since 2015-02-04
**/
public class Ezid {
	private DefaultHttpClient client;
	private String mintURL;

	/**
	 * Default constructor.
	 * @param host EZID minter base URL, e.g. "https://ezid.cdlib.org"
	 * @param shoulder Base identifier shoulder, e.g. "doi:10.5072/FK2"
	 * @param user EZID username
	 * @param pass EZID password
	**/
	public Ezid( String host, String shoulder, String user, String pass ) {
		mintURL = host + "/shoulder/" + shoulder;

		// setup httpclient with authentication
		client = new DefaultHttpClient();
		final URI uri = URI.create(host);
		CredentialsProvider creds = new BasicCredentialsProvider();
		creds.setCredentials(new AuthScope(uri.getHost(), uri.getPort()),
			new UsernamePasswordCredentials(user, pass));
		client.setCredentialsProvider(creds);
	}

	/**
	 * Mint a DOI identifier.
	 * @param url Object URL
	 * @param dataciteXML DataCite XML content
	 * @returns DOI identifier
	**/
	public String mintDOI( String url, String dataciteXML ) throws IOException, EzidException {
		HttpPost post = new HttpPost(mintURL);
		Map<String,String> params = new HashMap<>();
		params.put("_target", url);
		params.put("_profile", "datacite");
		params.put("datacite", dataciteXML);
		String anvl = toAnvl(params);
		post.setEntity(new StringEntity(anvl));
		post.setHeader("Content-Type", "text/plain");

		// execute request
		HttpResponse response = client.execute(post);
		String body = EntityUtils.toString(response.getEntity());

		// parse doi from response
		if ( response.getStatusLine().getStatusCode() == 201 ) {
        	String[] tokens = body.split(" ");
        	if ( tokens.length > 1 && tokens[1].startsWith("doi:") ) {
            	return tokens[1];
        	} else {
				throw new EzidException(body);
			}
		} else {
			throw new EzidException(body);
		}
	}

	/* See http://ezid.cdlib.org/doc/apidoc.html#java-example */
    private static String encode (String s) {
        return s.replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D").replace(":", "%3A");
    }
    private static String toAnvl (Map<String, String> params) {
        StringBuffer b = new StringBuffer();
        for ( Iterator<Map.Entry<String, String>> i = params.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<String, String> e = i.next();
            b.append(encode(e.getKey()) + ": " + encode(e.getValue()) + "\n");
        }
        return b.toString();
    }

}

/**
 * Embedded exception class.
**/
class EzidException extends Exception {
	public EzidException( String msg ) {
		super(msg);
	}
}
