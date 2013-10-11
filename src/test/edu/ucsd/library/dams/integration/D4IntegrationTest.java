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

import org.junit.Test;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


public class D4IntegrationTest extends AbstractIntegrationTest
{
	private String unitID;
	private String collID;
	private String obj1ID;
	private String obj2ID;
	private String obj3ID;
	private String repoBase;
	private String idNS = "http://library.ucsd.edu/ark:/20775/";

	public D4IntegrationTest()
	{
    	repoPort = Integer.parseInt(
			System.getProperty("test.port", "8080")
		);
		repoHost = System.getProperty("test.host", "localhost");
		repoUser = System.getProperty("test.user", "dams");
		repoPass = System.getProperty("test.pass", "dams");
		repoBase = "http://" + repoHost + ":" + repoPort + "/dams/api";
	}

	@Test
	public void sequence()
	{
		setup();
		testUnit();
		testCollection();
		testObject();
		testEvent();
		testFile();
		testSystem();
		testUpdates();
		cleanup();
	}

	public void setup()
	{
		// mint 5 arks (unit, collection, 2 simple objects, complex object)
		HttpPost mintPost = new HttpPost(
			repoBase + "/next_id?format=json&count=5"
		);
		HttpResponse mintResp = exec( mintPost );
		String mintBody = getBody( mintResp );
		JSONObject mintJson = (JSONObject)JSONValue.parse( mintBody );
		JSONArray ids = (JSONArray)mintJson.get("ids");
		unitID = (String)ids.get(0);
		collID = (String)ids.get(1);
		obj1ID = (String)ids.get(2);
		obj2ID = (String)ids.get(3);
		obj3ID = (String)ids.get(4);
		assertNotNull(unitID);
		assertNotNull(collID);
		assertNotNull(obj1ID);
		assertNotNull(obj2ID);
		assertNotNull(obj3ID);
		System.out.println(
			"arks: c=" + collID + ", u=" + unitID + ", o1=" + obj1ID
			+ ", o2=" + obj1ID + ", o3=" + obj1ID
		);


		// create a unit
		List unitData = new ArrayList();
		addTriple( unitData, unitID, "dams:unitName", "'Test Unit'" );
		addTriple( unitData, unitID, "dams:unitURI", "http://test.com" );
		addTriple( unitData, unitID, "rdf:type", "http://library.ucsd.edu/ontology/dams#Unit" );
		String unitJSON = JSONValue.toJSONString(unitData);
		HttpPost unitPost = new HttpPost(
			repoBase + "/objects/" + unitID + "?adds=" + urlencode(unitJSON)
		);
		HttpResponse unitResp = exec( unitPost );
		String unitBody = getBody( unitResp );
		assertTrue(
			"Status: " + unitBody,
			unitBody.indexOf("<status>OK</status>") != -1
		);

		// create a collection
		List collData = new ArrayList();
		addTriple( collData, collID, "dams:title", "node1" );
		addTriple( collData, "node1", "mads:authoritativeLabel", "'Test Collection'" );
		addTriple( collData, "node1", "rdf:type", "http://www.loc.gov/mads/rdf/v1#Title" );
		addTriple( collData, collID, "rdf:type", "http://library.ucsd.edu/ontology/dams#AssembledCollection" );
		String collJSON = JSONValue.toJSONString(collData);
		HttpPost collPost = new HttpPost(
			repoBase + "/objects/" + collID + "?adds=" + urlencode(collJSON)
		);
		HttpResponse collResp = exec( collPost );
		String collBody = getBody( collResp );
		assertTrue(
			"Status: " + collBody,
			collBody.indexOf("<status>OK</status>") != -1
		);

		// create object 1
		List obj1Data = new ArrayList();
		addTriple( obj1Data, obj1ID, "dams:title", "node1" );
		addTriple( obj1Data, "node1", "mads:authoritativeLabel", "'Test Object 1'" );
		addTriple( obj1Data, "node1", "rdf:type", "http://www.loc.gov/mads/rdf/v1#Title" );
		addTriple( obj1Data, obj1ID, "rdf:type", "http://library.ucsd.edu/ontology/dams#Object" );
		addTriple( obj1Data, obj1ID, "dams:unit", idNS + unitID );
		addTriple( obj1Data, obj1ID, "dams:assembledCollection", idNS + collID );
		String obj1JSON = JSONValue.toJSONString(obj1Data);
		HttpPost obj1Post = new HttpPost(
			repoBase + "/objects/" + obj1ID + "?adds=" + urlencode(obj1JSON)
		);
		HttpResponse obj1Resp = exec( obj1Post );
		String obj1Body = getBody( obj1Resp );
		assertTrue(
			"Status: " + obj1Body,
			obj1Body.indexOf("<status>OK</status>") != -1
		);

		// attach a file to object1 (upload)
    		// curl -f -i -X POST -F sourcePath="$SRCPATH" -F file=@$FILE $URL/api/files/$OBJ_ARK/1/1.tif?fs=$FS

		// create object 2
		// attach a file to object2 (staged)
    		// curl -f -i -X POST -F local=$FILE $URL/api/files/$OBJ_ARK/2/1.jpg?fs=$FS

		// generate derivatives
    		// SIZES="-F size=2 -F size=3 -F size=4 -F size=5"
    		// echo "$URL/api/files/$OBJ_ARK/1/1.tif/derivatives"
    		// curl -f -i -L -X POST $SIZES $URL/api/files/$OBJ_ARK/1/1.tif/derivatives?fs=$FS

		// create object 3
		// attach a file to object3 (upload)
		// attach a file to object3 (staged)
		// generate derivatives
	}

	public void testUnit()
	{
		// list units
		// list objects in a unit
		// count objects in a unit
		// list files in a unit
	}

	public void testCollection()
	{
		// list collections
		// list objects in a collection
		// count objects in a collection
		// list files in a collection
	}

	public void testObject()
	{
		// get top-level metadata
		// get recursive metadata
		// check exists
		// transform object (read-only)
	}

	public void testEvent()
	{
		// find event
		// get event
	}

	public void testFile()
	{
		// find a file
		// get a file
		// check exists
		// fixity check
		// characterization
	}

	public void testSystem()
	{
		// system info
		// system version
		// list filestores
		// list triplestores
		// list predicates
		// user info
	}

	public void testUpdates()
	{
		// find object
		// transform object (saved & check it was saved)
		// update metadata (replace)
		// update metadata (add)
		// update metadata (selective)

		// find file
		// replace file
		// regenerate characterization
	}

	public void cleanup()
	{
		// delete file
		// delete object
		// delete collection
		// delete unit
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
