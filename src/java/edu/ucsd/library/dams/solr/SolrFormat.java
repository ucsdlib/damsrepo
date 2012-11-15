package edu.ucsd.library.dams.solr;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// dom4j
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

// json simple
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

// commons-lang
import org.apache.commons.lang3.StringEscapeUtils;

// logging
import org.apache.log4j.Logger;


/**
 * Convert Solr results to JSON.
 *
 * @author escowles@ucsd.edu
 * @author mcritchlow@ucsd.edu
**/
public class SolrFormat
{
	private static Logger log = Logger.getLogger( SolrFormat.class );

	/**
	 * Convert Solr XML to older verbose JSON format, parsing each JSON
	 * fragment individually using the JSON Simple API.
	**/
	@SuppressWarnings("unchecked")
	public static String jsonFormat( String solrXML, String ds, String scope,
		String userQuery ) throws DocumentException
	{
		/** top level json object which has 2 objects in it
		 * 1: "ResultSet" - Solr index data
		 * 2: "facets" - Solr facet data
		 */
		JSONObject parentObject = new JSONObject();
		Document indexDoc = DocumentHelper.parseText( solrXML );

		// resultset md
		JSONObject stmt = new JSONObject();
		stmt.put( "generator", "jsonFormat" );
		stmt.put(
			"totalResultsAvailable",
			indexDoc.valueOf("/response/result/@numFound")
		);
		stmt.put(
			"totalResultsReturned",
			indexDoc.valueOf("count(/response/result/doc)")
		);
		stmt.put(
			"firstResultPosition",
			indexDoc.valueOf("/response/result/@start")
		);
		stmt.put("error", indexDoc.valueOf("/error") );
		stmt.put("query", userQuery );
		if ( scope != null )
		{
			stmt.put( "scope", scope );
		}
		stmt.put("ds", ds );

		// results
		JSONArray results = new JSONArray();
		List resultElems = indexDoc.selectNodes("/response/result/doc");
		for ( int i = 0; i < resultElems.size(); i++ )
		{
			// system md
			Element docElem = (Element)resultElems.get(i);
			JSONObject docObj = new JSONObject();
			docObj.put("id", docElem.valueOf("str[@name='id']") );
			docObj.put("ds", ds );
			docObj.put(
				"arkId", "20775/" + docElem.valueOf("str[@name='subject']")
			);

			// attribs
			List attribElems = docElem.selectNodes("arr[@name='attrib']/str");
			for ( int j = 0; j < attribElems.size(); j++ )
			{
				Element attribElem = (Element)attribElems.get(j);
				String s = attribElem.getText();
				String pred = s.substring( 0, s.indexOf("|||") );
				pred = pred.replaceAll( " .*", "" );
				String val = s.substring( s.indexOf("|||") + 3 );
				try
				{
					if ( val.startsWith("b") )
					{
						val = "\"" + val + "\"";
					}
					Object o = JSONValue.parse(val);
					docObj.put( pred, o );
				}
				catch ( Throwable t )
				{
					log.warn("Error parsing: " + val);
				}
			}
			results.add( docObj );
		}
		stmt.put("Result", results );
		parentObject.put("ResultSet", stmt );

		// facets
		JSONArray facetsObj = new JSONArray();
		List facetElems = indexDoc.selectNodes(
			"/response/lst[@name='facet_counts']/lst[@name='facet_fields']"
				+ "/lst[count(int) > 0]"
		);
		for ( int i = 0; i < facetElems.size(); i++ )
		{
			Element facetElem = (Element)facetElems.get(i);
			JSONObject facetVal = new JSONObject();
			String facetName = facetElem.attributeValue("name");

			// facet values
			List facetValues = facetElem.selectNodes("int[@name != '']");
			for ( int j = 0; j < facetValues.size(); j++ )
			{
				Element facetValue = (Element)facetValues.get(j);
				String facetValueName = facetValue.attributeValue("name");
				String facetValueCount = facetValue.getText();
				facetVal.put( facetValueName, facetValueCount );
			}

			JSONObject facetValObj = new JSONObject();
			facetValObj.put( facetName, facetVal );
			if ( facetValObj.size() > 0 )
			{
				facetsObj.add( facetValObj );
			}
		}
		parentObject.put("facets",facetsObj);
		return parentObject.toString();
	}

	private static void jappend( StringBuilder buf, String att, String val,
		boolean comma )
	{
		buf.append( "\"" + StringEscapeUtils.escapeJava(att) + "\":" );
		buf.append( "\"" + StringEscapeUtils.escapeJava(val) + "\""	);
		if ( comma ) { buf.append(","); }
	}
	/**
	 * Convert Solr XML to older verbose JSON format, without parsing JSON.
	**/
	public static String jsonFormatText( String solrXML, String ds,
		String scope, String userQuery ) throws DocumentException
	{
		/** top level json object which has 2 objects in it
		 * 1: "ResultSet" - Solr index data
		 * 2: "facets" - Solr facet data
		 */
		Document indexDoc = DocumentHelper.parseText( solrXML );

		// resultset md
		StringBuilder stmt = new StringBuilder();
		jappend( stmt, "generator", "jsonFormatText", true );
		jappend(
			stmt, "totalResultsAvailable",
			indexDoc.valueOf("/response/result/@numFound"), true
		);
		jappend(
			stmt, "totalResultsReturned",
			indexDoc.valueOf("count(/response/result/doc)"), true
		);
		jappend(
			stmt, "firstResultPosition",
			indexDoc.valueOf("/response/result/@start"), true
		);
		jappend( stmt, "error", indexDoc.valueOf("/error"), true );
		jappend( stmt, "query", userQuery, true );
		if ( scope != null )
		{
			jappend( stmt, "scope", scope, true );
		}
		jappend( stmt, "ds", ds, true );

		// results
		StringBuilder results = new StringBuilder();
		List resultElems = indexDoc.selectNodes("/response/result/doc");
		for ( int i = 0; i < resultElems.size(); i++ )
		{
			// system md
			Element docElem = (Element)resultElems.get(i);
			StringBuilder docObj = new StringBuilder("{");
			jappend( docObj, "id", docElem.valueOf("str[@name='id']"), true );
			jappend( docObj,"ds", ds, true );
			jappend(
				docObj, "arkId",
				"20775/" + docElem.valueOf("str[@name='subject']"), true
			);

			// attribs
			List attribElems = docElem.selectNodes("arr[@name='attrib']/str");
			for ( int j = 0; j < attribElems.size(); j++ )
			{
				Element attribElem = (Element)attribElems.get(j);
				String s = attribElem.getText();
				String pred = s.substring( 0, s.indexOf("|||") );
				pred = pred.replaceAll( " .*", "" );
				String val = s.substring( s.indexOf("|||") + 3 );
				try
				{
					if ( val.startsWith("b") )
					{
						val = "\"" + val + "\"";
					}
					if ( j > 0 ) { docObj.append(","); }
					docObj.append( "\"" + pred + "\":" + val );
				}
				catch ( Throwable t )
				{
					log.warn("Error parsing: " + val);
				}
			}
			if ( i > 0 ) { results.append(","); }
			results.append( docObj.toString() + "}" );
		}
		stmt.append("\"Result\":[" + results.toString() + "]" );

		// facets
		String facets = facets( indexDoc );

		StringBuilder parentObject = new StringBuilder("{");
		parentObject.append("\"ResultSet\":{" + stmt.toString() + "}" );
		if ( facets != null )
		{
			parentObject.append(",\"facets\":[" + facets.toString() + "]" );
		}
		parentObject.append("}");

		return parentObject.toString();
	}
	private static String facets( Document indexDoc )
	{
		ArrayList<String> facetList = new ArrayList<String>();
		List facetElems = indexDoc.selectNodes(
			"/response/lst[@name='facet_counts']/lst[@name='facet_fields']"
				+ "/lst[count(int) > 0]"
		);
		log.warn("facets = " + facetElems.size() );
		if ( facetElems.size() == 0 )
		{
			return null;
		}
		for ( int i = 0; i < facetElems.size(); i++ )
		{
			Element facetElem = (Element)facetElems.get(i);
			List facetValues = facetElem.selectNodes("int[@name != '']");
			if ( facetValues.size() > 0 )
			{
				String facetName = facetElem.attributeValue("name");
				StringBuilder fac = new StringBuilder();
				fac.append("{\"" + facetName + "\":{");

				// facet values
				for ( int j = 0; j < facetValues.size(); j++ )
				{
					Element facetValue = (Element)facetValues.get(j);
					String facetValueName = facetValue.attributeValue("name");
					String facetValueCount = facetValue.getText();
					boolean next = (j+1) < facetValues.size();
					jappend( fac, facetValueName, facetValueCount, next );
				}
				fac.append("}}");
				facetList.add( fac.toString() );
			}
		}
		StringBuilder facets = new StringBuilder();
		for ( int i = 0; i < facetList.size(); i++ )
		{
			if ( i > 0 ) { facets.append(","); }
			facets.append( facetList.get(i) );
		}

		return facets.toString();
	}

	/**
	 * Convert Solr XML to older verbose JSON format, but only including titles,
	 *  without parsing JSON.
	**/
	public static String jsonFormatMinimal( String solrXML, String ds,
		String scope, String userQuery ) throws DocumentException
	{
		/* top level json object which has 2 objects in it
		 * 1: "ResultSet" - Solr index data
		 */
		StringBuilder parentObject = new StringBuilder("{");
		Document indexDoc = DocumentHelper.parseText( solrXML );

		// resultset md
		StringBuilder stmt = new StringBuilder("{");
		jappend( stmt, "generator", "jsonFormatMinimal", true );
		jappend(
			stmt, "totalResultsAvailable",
			indexDoc.valueOf("/response/result/@numFound"), true
		);
		jappend(
			stmt, "totalResultsReturned",
			indexDoc.valueOf("count(/response/result/doc)"), true
		);
		jappend(
			stmt, "firstResultPosition",
			indexDoc.valueOf("/response/result/@start"), true
		);
		jappend( stmt, "error", indexDoc.valueOf("/error"), true );
		jappend( stmt, "query", userQuery, true );
		if ( scope != null )
		{
			jappend( stmt, "scope", scope, true );
		}
		jappend( stmt, "ds", ds, true );

		// results
		StringBuilder results = new StringBuilder();
		List resultElems = indexDoc.selectNodes("/response/result/doc");
		for ( int i = 0; i < resultElems.size(); i++ )
		{
			// system md
			Element docElem = (Element)resultElems.get(i);
			StringBuilder docObj = new StringBuilder("{");
			jappend( docObj, "id", docElem.valueOf("str[@name='id']"), true );
			jappend( docObj,"ds", ds, true );
			jappend(
				docObj,"category",
				docElem.valueOf("arr[@name='category']/str"), true
			);
			jappend(
				docObj, "arkId",
				"20775/" + docElem.valueOf("str[@name='subject']"), true
			);

			// find titles only (make a more extensible version that takes a
			// list of attributes to get???
			List attribElems = docElem.selectNodes("arr[@name='attrib']/str");
			ArrayList<String>titles = new ArrayList<String>();
			for ( int j = 0; j < attribElems.size(); j++ )
			{
				Element attribElem = (Element)attribElems.get(j);
				String s = attribElem.getText();
				if ( s.indexOf("mods:titleInfo") != -1 )
				{
					titles.add( s );
				}
			}

			// add titles
			for ( int j = 0; j < titles.size(); j++ )
			{
				String s = titles.get(j);
			   	String pred = s.substring( 0, s.indexOf("|||") );
			   	pred = pred.replaceAll( " .*", "" );
			   	String val = s.substring( s.indexOf("|||") + 3 );
			   	try
			   	{
				   	if ( val.startsWith("b") )
				   	{
					   	val = "\"" + val + "\"";
				   	}
					if ( j > 0 ) { docObj.append(","); }
				   	docObj.append( "\"" + pred + "\":" + val );
			   	}
			   	catch ( Throwable t )
			   	{
				   	log.warn("Error parsing: " + val);
			   	}
			}
			if ( i > 0 ) { results.append(","); }
		   	results.append( docObj.toString() + "}" );
		}

		// facets
		String facets = facets( indexDoc );

		stmt.append("\"Result\":[" + results.toString() + "]" );
		parentObject.append("\"ResultSet\":" + stmt.toString() + "}" );
		if ( facets != null )
		{
			parentObject.append(",\"facets\":[" + facets.toString() + "]" );
		}
		parentObject.append("}" );

		return parentObject.toString();
	}

	/**
	 * Convert Solr XML to newer grid JSON format.
	**/
	@SuppressWarnings("unchecked")
	public static String jsonGridFormat( String solrXML, int page, int rows )
		throws DocumentException
	{
		Document indexDoc = DocumentHelper.parseText( solrXML );
		JSONObject stmt = new JSONObject();
		if(page == -1){
			page = 1;
		}
		stmt.put("page", Integer.toString(page) );
		int total = Integer.parseInt(
			indexDoc.valueOf("/response/result/@numFound")
		);
		if(total <= 1)
		{
			stmt.put("total", indexDoc.valueOf("/response/result/@numFound") );
		}
		else
		{
			int pageResults = (int)Math.ceil( (double)total / (double)rows );
			stmt.put("total", Integer.toString(pageResults) );
		}
		stmt.put("records", indexDoc.valueOf("/response/result/@numFound") );
		//rows
		JSONArray results = new JSONArray();
		List resultElems = indexDoc.selectNodes("/response/result/doc");
		for ( int i = 0; i < resultElems.size(); i++ )
		{
			// system md
			Element docElem = (Element)resultElems.get(i);
			JSONObject docObj = new JSONObject();
			docObj.put("id", docElem.valueOf("str[@name='subject']") );
			//cell array
			JSONArray cell = new JSONArray();
			cell.add( docElem.valueOf("arr[@name='DAR_TITLE']/str") );
			cell.add( docElem.valueOf("arr[@name='DAR_SOURCE']/str") );
			cell.add( docElem.valueOf("arr[@name='DAR_FILENAME']/str") );
			//can be multiple usage elements
			List usageElems = docElem.selectNodes("arr[@name='DAR_USAGE']/str");
			String usageData = "";
			for ( int j = 0; j < usageElems.size(); j++ ){
				Element usageElem = (Element)usageElems.get(j);
				if(j >= 1){
					usageData += " ; ";
				}
				usageData += usageElem.getText();
			}
			cell.add( usageData ); //DAR_USAGE
			docObj.put("cell",cell);
			results.add( docObj );
		}
		stmt.put("rows", results );
		return stmt.toString();
	}
}
