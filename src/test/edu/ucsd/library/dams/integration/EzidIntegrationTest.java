package edu.ucsd.library.dams.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


public class EzidIntegrationTest extends AbstractIntegrationTest
{
	private static String collID;
	private static String obj1ID;
	private static String obj2ID;
	private static String repoBase;
	private static File samples;
	private static String idNS = "http://library.ucsd.edu/ark:/20775/";

	@BeforeClass
	public static void setup()
	{
    	repoPort = Integer.parseInt( System.getProperty("test.port", "8080") );
		repoHost = System.getProperty("test.host", "localhost");
		repoUser = System.getProperty("test.user", "dams");
		repoPass = System.getProperty("test.pass", "dams");
		repoBase = "http://" + repoHost + ":" + repoPort + "/dams/api";

		// mint 3 arks (collection, 2 objects)
		HttpPost mintPost = new HttpPost(
			repoBase + "/next_id?format=json&count=3"
		);
		HttpResponse mintResp = exec( mintPost );
		String mintBody = getBody( mintResp );
		JSONObject mintJson = (JSONObject)JSONValue.parse( mintBody );
		JSONArray ids = (JSONArray)mintJson.get("ids");
		collID = (String)ids.get(0);
		obj1ID = (String)ids.get(1);
		obj2ID = (String)ids.get(1);
		assertNotNull(collID);
		assertNotNull(obj1ID);
		assertNotNull(obj2ID);

		samples = new File( System.getProperty("dams.samples") );
	}

	@After
	public void cleanup()
	{
		execAndCleanup( new HttpDelete( repoBase + "/objects/" + collID ) );
		execAndCleanup( new HttpDelete( repoBase + "/objects/" + obj1ID ) );
		execAndCleanup( new HttpDelete( repoBase + "/objects/" + obj2ID ) );
	}

	@Test
	public void testCollection()
	{
		loadAndMint("bb1673671n");
	}

	@Test
	public void testObject1()
	{
        loadAndMint("bb0103691x");
	}

	@Test
	public void testObject2()
	{
		loadAndMint("bb59399209");
	}

	private void loadAndMint( String id )
	{
		HttpPut put = new HttpPut(repoBase + "/objects/" + id);
		put.setEntity( new FileEntity(new File(samples, "object/" + id + ".xml")) );
		String putResponse = execAndGetBody(put);
       	assertTrue( putResponse, putResponse.contains("<statusCode>200</statusCode>") );

		HttpPost post = new HttpPost(repoBase + "/objects/" + id + "/mint_doi");
		String mintResponse = execAndGetBody(put);
       	assertTrue( mintResponse, mintResponse.contains("<statusCode>200</statusCode>") );
	}
}
