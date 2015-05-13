package edu.ucsd.library.dams.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


public class D4IntegrationTest extends AbstractIntegrationTest
{
	private static String unitID;
	private static String collID;
	private static String obj1ID;
	private static String repoBase;
	private static String idNS = "http://library.ucsd.edu/ark:/20775/";

	@BeforeClass
	public static void setup()
	{
    	repoPort = Integer.parseInt( System.getProperty("test.port", "8080"));
		repoHost = System.getProperty("test.host", "localhost");
		repoUser = System.getProperty("test.user", "dams");
		repoPass = System.getProperty("test.pass", "dams");
		repoBase = "http://" + repoHost + ":" + repoPort + "/dams/api";

		// mint 3 arks (unit, collection, 2 simple objects, complex object)
		String mintBody = execAndGetBody( new HttpPost(repoBase + "/next_id?format=json&count=5") );
		JSONObject mintJson = (JSONObject)JSONValue.parse( mintBody );
		JSONArray ids = (JSONArray)mintJson.get("ids");
		unitID = (String)ids.get(0);
		collID = (String)ids.get(1);
		obj1ID = (String)ids.get(2);
		assertNotNull(unitID);
		assertNotNull(collID);
		assertNotNull(obj1ID);
	}

	@After
    public void cleanup()
	{
		execAndCleanup( new HttpDelete( repoBase + "/objects/" + obj1ID ) );
		execAndCleanup( new HttpDelete( repoBase + "/objects/" + collID ) );
		execAndCleanup( new HttpDelete( repoBase + "/objects/" + unitID ) );
	}

	@Test
	public void testCreate()
	{
		// create a unit
		List unitData = new ArrayList();
		addTriple( unitData, unitID, "dams:unitName", "'Test Unit'" );
		addTriple( unitData, unitID, "dams:unitURI", "http://test.com" );
		addTriple( unitData, unitID, "rdf:type", "http://library.ucsd.edu/ontology/dams#Unit" );
		String unitJSON = JSONValue.toJSONString(unitData);
		String unitBody = execAndGetBody(
			new HttpPost(repoBase + "/objects/" + unitID + "?adds=" + urlencode(unitJSON)) );
		assertTrue( "Status: " + unitBody, unitBody.indexOf("<status>OK</status>") != -1);

		// create a collection
		List collData = new ArrayList();
		addTriple( collData, collID, "dams:title", "node1" );
		addTriple( collData, "node1", "mads:authoritativeLabel", "'Test Collection'" );
		addTriple( collData, "node1", "rdf:type", "http://www.loc.gov/mads/rdf/v1#Title" );
		addTriple( collData, collID, "rdf:type", "http://library.ucsd.edu/ontology/dams#AssembledCollection");
		String collJSON = JSONValue.toJSONString(collData);
		String collBody = execAndGetBody(
			new HttpPost(repoBase + "/objects/" + collID + "?adds=" + urlencode(collJSON)));
		assertTrue( "Status: " + collBody, collBody.indexOf("<status>OK</status>") != -1);

		// create object
		List obj1Data = new ArrayList();
		addTriple( obj1Data, obj1ID, "dams:title", "node1" );
		addTriple( obj1Data, "node1", "mads:authoritativeLabel", "'Test Object 1'" );
		addTriple( obj1Data, "node1", "rdf:type", "http://www.loc.gov/mads/rdf/v1#Title" );
		addTriple( obj1Data, obj1ID, "rdf:type", "http://library.ucsd.edu/ontology/dams#Object" );
		addTriple( obj1Data, obj1ID, "dams:unit", idNS + unitID );
		addTriple( obj1Data, obj1ID, "dams:assembledCollection", idNS + collID );
		String obj1JSON = JSONValue.toJSONString(obj1Data);
		String obj1Body = execAndGetBody(
			new HttpPost(repoBase + "/objects/" + obj1ID + "?adds=" + urlencode(obj1JSON)));
		assertTrue( "Status: " + obj1Body, obj1Body.indexOf("<status>OK</status>") != -1);
	}

	public void addTriple(List data, String sub, String pre, String obj)
	{
		Map m = new HashMap();
		m.put( "subject",   sub );
		m.put( "predicate", pre );
		m.put( "object",    obj );
		data.add( m );
	}
}
