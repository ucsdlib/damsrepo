package edu.ucsd.library.dams.solr;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.InitialContext;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import edu.ucsd.library.dams.util.HttpUtil;

/**
 * Utility class for interfacing with multi-core Lucene/Solr search engines.
 * @author escowles
**/
public class SolrHelper
{
	private static Logger log = Logger.getLogger( SolrHelper.class );

	private String multiURL = null;

    /**
     * Solr response string indicating a successful operation.
    **/
    public static String responseOK = "<result status=\"0\"></result>";

    /**
     * Solr response string indicating a successful operation (multi-core mode).
    **/
    public static String responseOKMulti = "<int name=\"status\">0</int>";

    /**
     * Number of records to retrieve in a single page.
    **/
    public static int LIST_BATCH = 10000;

	/**
	 * Default constructor (looks up Solr base URL from JNDI).
	**/
	public SolrHelper()
	{
		try
		{
			InitialContext ctx = new InitialContext();
			multiURL = (String)ctx.lookup("java:comp/env/solr-multi-url");
			log.info( "multiURL: " + multiURL );
		}
		catch ( Exception ex )
		{
			log.error( "Unable to lookup Solr base URL", ex );
		}
	}
	/**
	 * Constructor with Solr base specified.
	 * @param multiBase Solr base URL.
	**/
	public SolrHelper( String multiBase )
	{
		multiURL = multiBase;
	}

	//### Public Methods ######################################################

	/**
	 * Add an object to solr (or update existing object).
	 * @param core Solr core to add document to.
	 * @param xml Document to add to Solr.
	 * @throws IOException On error interacting with Solr.
	**/
	public boolean add( String core, String xml ) throws IOException
	{
		return update( multiURL + "/" + core, xml );
	}

	/**
	 * Remove an object from solr.
	 * @param core Solr core to remove the document from.
	 * @param ds Datasource to delete from.
	 * @param id The id field of the object to remove.
	 * @throws IOException On error interacting with Solr.
	**/
	public boolean delete( String core, String ds, String id )
		throws IOException
	{
		return update(
			multiURL + "/" + core,
			"<delete><id>" + ds + "_" + id + "</id></delete>"
		);
	}

	/**
	 * Tell Solr to save added/updated records and to remove delted records.
	 * @param Solr core to commit.
	 * @throws IOException On error interacting with Solr.
	**/
	public boolean commit( String core ) throws IOException
	{
		return update( multiURL + "/" + core, "<commit/>" );
	}

	/**
	 * Tell Solr to optimize the index for faster searching.
	 * @param core Solr core to optimize.
	 * @throws IOException On error interacting with Solr.
	**/
	public boolean optimize( String core ) throws IOException
	{
		return update( multiURL + "/" + core, "<optimize/>" );
	}

	/**
	 * List cores available in a Solr server.
	**/
	public List<String> cores() throws IOException
	{
		List<String> cores = new ArrayList<String>();
		try
		{
			String url = multiURL + "/admin/cores";
			String solrXml = HttpUtil.get( url );
			Document doc = DocumentHelper.parseText( solrXml );
			List nodes = doc.selectNodes( "/response/lst[@name='status']/lst" );
			for ( int i = 0; i < nodes.size(); i++ )
			{
				Element e = (Element)nodes.get(i);
				String name = e.attributeValue("name");
				String realName = e.valueOf("str[@name='name']");
				if ( name.equals( realName ) )
				{
					cores.add( e.attributeValue("name") );
				}
			}
		}
		catch ( DocumentException ex )
		{
			throw new IOException(ex.getMessage());
		}
		return cores;
	}

	/**
	 * List records in a Solr core.
	 * @param core Core to list.
	 * @param datasource If not null, limit to this datasource.
	 * @param category If not null, limit to this category.
	**/
	public List<String> list( String core, String datasource,
		String category ) throws IOException
	{
		List<String> ids = new ArrayList<String>();

		try
		{
			String q = "";
			if ( datasource != null ) { q = "ds:" + datasource; }
			if ( category != null )
			{
				if ( q.length() > 0 ) { q += " AND "; }
				q += "category:" + category;
			}
			int rows = 1000;
			listBatch( ids, core, q, 0, rows );

			if ( ids.size() == rows )
			{
				for ( int batch = 1; ids.size() == (batch*rows) && batch < 300; batch++ )
				{
					listBatch( ids, core, q, batch*rows, rows );
				}
			}
		}
		catch ( DocumentException ex )
		{
			throw new IOException(ex.getMessage());
		}
		return ids;
	}
	private void listBatch( List<String> ids, String core, String query,
		int offset, int rows ) throws DocumentException, IOException
	{
		log.debug("listBatch: " + offset);
		Set fields = new HashSet();
		fields.add("subject");
		String solrXml = search(core, query, offset, rows, null, fields, null);
		Document doc = DocumentHelper.parseText( solrXml );

		// was field[@name='subject'] -- probably incompat with solr 1.2
		List nodes = doc.selectNodes(
			"/response/result/doc/str[@name='subject']"
		);
		for ( int i = 0; i < nodes.size(); i++ )
		{
			Element e = (Element)nodes.get(i);
			ids.add( e.getText() );
		}
	}

	/**
	 * Perform a query and count the number of records that match.
	 * @param core Core to search.
	 * @param query Query to perform.
	**/
	public int count( String core, String query )
		throws DocumentException, IOException
	{
		Set fields = new HashSet();
		fields.add("subject");
		String solrXml = search(core, query, 0, 0, null, fields, null);
		Document doc = DocumentHelper.parseText( solrXml );

		// was field[@name='subject'] -- probably incompat with solr 1.2
		int count = -1;
		Number n = doc.numberValueOf( "/response/result/@numFound" );
		if ( n != null ) { count = n.intValue(); }
		return count;
	}

	/**
	 * Search solr.
	 * @param core Datasource to query
	 * @param q Solr search string
	 * @param start First record to retrieve, or -1 for the default (0).
	 * @param rows Number of records to retrieve, or -1 for the default (20).
	 * @param sort Field to sort by, or null for the default (score).
	 * @throws IOException On error interacting with Solr.
	**/
	public String search( String core, String q, int start, int rows,
		String sort, Set fields, Set facetFields ) throws IOException
	{
		StringBuffer url = new StringBuffer();
		url.append( multiURL + "/" + core + "/select?" );

		// unicode-capable query processing, but make sure + are not escaped
		String qs = StringEscapeUtils.unescapeHtml4(q); // &uuml;
		qs = URLEncoder.encode(qs,"UTF-8");
		if ( qs.indexOf("%2B") != -1 )
		{
			qs = qs.replaceAll("%2B","+");
		}
		url.append( "q=" + qs );

		// sorting
		if ( sort != null && !sort.equals("") ) { url.append( "; " + sort ); }

		// paging
		if ( start > -1 ) { url.append( "&start=" + start ); }
		if ( rows  > -1 )
		{
			url.append( "&rows="  + rows  );
		}
		else
		{
			url.append( "&rows=20" );
		}

		// facets
		if ( facetFields != null )
		{
			// remove fields in the query from the facet list
			HashSet localFacetFields = new HashSet();
			for ( Iterator it = facetFields.iterator(); it.hasNext(); )
			{
				String f = (String)it.next();
				if ( q.indexOf(f+":") == -1 )
				{
					localFacetFields.add(f);
				}
			}
			if ( localFacetFields.size() > 0 )
			{
				url.append( "&facet=true&facet.sort=true&facet.zeros=false");
				url.append( "&facet.limit=10" );
				for ( Iterator it = localFacetFields.iterator(); it.hasNext(); )
				{
					String f = (String)it.next();
					url.append("&facet.field=" + f);
				}
			}
		}

		String urlText = url.toString();
		log.info( "url: " + urlText );
		urlText = urlText.replaceAll(" ","%20");
		return HttpUtil.get( urlText );
	}

	/**
	 * Search Solr using the DisMax request handler.
	 * @param core Datasource to query
	 * @param q Solr search string
	 * @param start First record to retrieve, or -1 for the default (0).
	 * @param rows Number of records to retrieve, or -1 for the default (20).
	 * @param sort Field to sort by, or null for the default (score).
	 * @param qf Comma-separated list of fields to search, or null for the
	 *   default.
	**/
	public String searchDisMax( String core, String q, int start,
		int rows, String sort, String qf ) throws IOException
	{
		StringBuffer url = new StringBuffer();
		url.append( multiURL + "/" + core );
		url.append( "/select?" );

		// unicode-capable query processing, but make sure + are not escaped
		String qs = StringEscapeUtils.unescapeHtml4(q); // &uuml;
		qs = URLEncoder.encode(qs,"UTF-8");
		if ( qs.indexOf("%2B") != -1 )
		{
			qs = qs.replaceAll("%2B","+");
		}
		url.append( "q=" + qs );

		// sorting
		if ( sort != null && !sort.equals("") ) { url.append( "; " + sort ); }

		// paging
		if ( start > -1 ) { url.append( "&start=" + start ); }
		if ( rows  > -1 )
		{
			url.append( "&rows="  + rows  );
		}
		else
		{
			url.append( "&rows=20" );
		}
		if ( qf != null && !qf.equals("") )
		{
			url.append("&qf=" + qf);
		}
		url.append("&qt=dismax&wt=xml");

		String urlText = url.toString();
		log.info( "url: " + urlText );
		urlText = urlText.replaceAll(" ","%20");
		return HttpUtil.get( urlText );
	}
    /**
     * Post an update (add or delete) to the Solr index.
    **/
    public static boolean update( String solrBase, String xml )
        throws IOException
    {
        // post command
        String statusMessage = HttpUtil.post(
            solrBase + "/update", xml, "text/xml", "UTF-8"
        );
        boolean resultOK = checkMessage(statusMessage);
        if ( !resultOK )
        {
            System.err.println("ERROR: " + statusMessage);
        }
        return resultOK;
    }
    private static boolean checkMessage( String msg )
    {
        if ( msg == null ) { return false; }
        else if ( msg.indexOf( responseOK ) > 0 ) { return true; }
        else if ( msg.indexOf( responseOKMulti ) > 0 ) { return true; }
        else { return false; }
    }
    /**
     * Get a list of all entities indexed in a Solr index.
    **/
    public static Set<String> listSolr( String solrBase, String dsParam )
        throws IOException, DocumentException
    {
        HashSet<String> recs = new HashSet<String>();

        // get first page of records
        String url = solrBase + "select?q=ds:" + dsParam + "&fl=id&rows="
            + LIST_BATCH;
        String xml = HttpUtil.get( url) ;
        Document doc = DocumentHelper.parseText( xml );
        int max = doc.numberValueOf("/response/result/@numFound").intValue();
        List nodes = doc.selectNodes("/response/result/doc/str[@name='id']");
        for ( int i = 0; i < nodes.size(); i++ )
        {
            Node n = (Node)nodes.get(i);
            String id = n.getText();
            recs.add( id.replaceAll(dsParam + "_", "") );
        }

        // process records until all pages processed
        for ( int i = 1; (i-1)*LIST_BATCH < max; i++ )
        {
            int start = i * LIST_BATCH;
            System.out.println("List Solr: " + start);
            try
            {
                xml = HttpUtil.get( url + "&start=" + start );
                doc = DocumentHelper.parseText( xml );
                nodes = doc.selectNodes("/response/result/doc/str[@name='id']");
                for ( int j = 0; j < nodes.size(); j++ )
                {
                    Node n = (Node)nodes.get(j);
                    String id = n.getText();
                    recs.add( id.replaceAll(dsParam + "_", "") );
                }
            }
            catch ( Exception ex )
            {
                System.err.println( "Error list records: " + ex.toString() );
            }
        }

        return recs;
    }
}
