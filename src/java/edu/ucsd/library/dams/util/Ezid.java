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

// dom4j
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;


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

    /**
     * Validate that DAMS XML contains the required information to mint a DOI.
     * @param damsXML DAMS4 XML content
     * @throws EzidException if the validation fails
    **/
    public static void validate( String damsXML ) throws DocumentException, EzidException {
        // pre-validate
        Document d = DocumentHelper.parseText(damsXML);

        String existing = d.valueOf("/rdf:RDF/*/dams:note/dams:Note[(dams:type = 'preferred citation' or dams:type = 'identifier') and contains(rdf:value, 'http://dx.doi.org/')]");
        if ( existing != null && !existing.trim().equals("") )
        {
            throw new EzidException("Record already has a DOI assigned");
        }

        String issue = d.valueOf("/rdf:RDF/*/dams:date/dams:Date[dams:type='issued']/rdf:value");
        if ( issue == null || issue.equals("") )
        {
            throw new EzidException("Record does not contain Date Issued");
        }

        String citation = d.valueOf(
            "/rdf:RDF/*/dams:note/dams:Note[dams:type='preferred citation']/rdf:value"
        );
        if ( citation == null || citation.indexOf(" (") == -1
            || citation.substring(0, citation.indexOf(" (")).trim().equals("") )
        {
            throw new EzidException("Record does not contain Preferred Citation");
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
