package edu.ucsd.library.dams.solr;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

// servlet
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// logging
import org.apache.log4j.Logger;

// http utility
import edu.ucsd.library.dams.util.HttpUtil;


/**
 * Servlet to proxy Lucene/Solr searches for client-side Javascript.
 * @author escowles 
**/
public class SolrProxy extends HttpServlet
{
	private static Logger log = Logger.getLogger( SolrProxy.class );
	private static final String defaultEncoding = "UTF-8";

	private String defaultDS = null;
	private String solrBase = null;
	private String baseDir = null;

	public void init( ServletConfig config ) throws ServletException
	{
		try
		{
			InitialContext ctx = new InitialContext();
			String prefix = "java:comp/env/xdre/";
			defaultDS = (String)ctx.lookup( prefix + "defaultDataSource" );
			solrBase = (String)ctx.lookup( prefix + "solrBase" );
			if (solrBase.endsWith("/")) { solrBase = solrBase.substring(0,solrBase.length()-1); }
			baseDir = (String)ctx.lookup("java:comp/env/clusterSharedPath");
		}
		catch ( Exception ex )
		{
			log.error( "Error looking up Solr base from JDNI", ex );
		}
		super.init( config );
	}
	public void doGet( HttpServletRequest req, HttpServletResponse res )
	{
		long start = System.currentTimeMillis();

		// make sure char encoding is unicode by default
		if ( req.getCharacterEncoding() == null )
		{
			try
			{
				req.setCharacterEncoding( defaultEncoding );
				log.debug("Setting character encoding: " + defaultEncoding );
			}
			catch ( UnsupportedEncodingException ex )
			{
				log.warn("Unable to set chararacter encoding");
			}
		}
		else
		{
			log.debug("Browser specified character encoding: " + req.getCharacterEncoding() );
		}
		
		// load profile
		String profileFilter = null;
		String profile = req.getParameter("profile");
		if ( profile != null )
		{
			profileFilter = getServletConfig().getInitParameter(
				"profile-" + profile
			);
		}

		// velocity
		String name = "select";
		String contentType = null;
		String v_template = req.getParameter("v.template");
		if ( (v_template != null && !v_template.equals("")) || (profileFilter != null && profileFilter.indexOf("v.template") != -1) )
		{
			name = "velo";
			contentType = "text/html";
		}

		// datasource param
		String ds = req.getParameter("ds");
		if ( ds == null || ds.equals("") )
		{
			ds = defaultDS;
		}
		ds = ds.replaceAll(".*\\/","");

		// JSON reformatting
		String format = req.getParameter("format");

		// XSL formatting
		String xsl = req.getParameter("xsl");

		// check ip and username
		String statusFilter = null;
		String username = req.getRemoteUser();
		if ( username == null || username.equals("") )
		{
			// not logged in, check ip addr status
			String status = (String)req.getAttribute("X-DAMS-Role");
			statusFilter = getServletConfig().getInitParameter(
				"filter" + status
			); // XXX: convert to JNDI
		}
		// build URL
		String url = solrBase + "/" + ds + "/" + name
			+ "?" + req.getQueryString();
		if ( xsl != null && !xsl.equals("") )
		{
			url += "&wt=xml";
		}
		if ( statusFilter != null && !statusFilter.equals("") )
		{
			url += "&fq=" + statusFilter;
		}
		if ( profileFilter != null && !profileFilter.equals("") )
		{
			url += "&fq=" + profileFilter;
		}
		log.info("url: " + url);

		// perform search
		try
		{
			// IOException here with message like "Http error connecting to ... 400 Bad Request" when malformed search (like single quote)
			String output = null;
			HttpUtil http = new HttpUtil(url);
			try
			{
				http.exec();
				if ( http.status() == 200 )
				{
					output = http.contentBodyAsString();
				}
				if ( contentType == null )
				{
					contentType = "application/json";
				}
			}
			catch ( IOException ex )
			{
				log.warn( "Error performing Solr search, url: " + url, ex );
				String err = ex.getMessage();
//XXX: look at error message/status code from http response...
				if ( err.endsWith("400 Bad Request") )
				{
					output = "{\"error\":\"Parsing error, please revise your query and try again.\"}";
					contentType = "application/json";
				}
			}
			finally
			{
				if ( http != null )
				{
					http.releaseConnection(); // ZZZ: release connection
				}
			}

			// check for null xml
			if ( output == null )
			{
				output = "{\"error\":\"Processing error, please revise your query and try again\"}";
				contentType = "application/json";
			}
			else if ( xsl != null && !xsl.equals("") )
			{
				String casGroupTest = null;
				if(xsl.indexOf("piclens_rss.xsl") >=0){
					if(req.getParameter("casTest") != null)
						casGroupTest = "casTest";
					else
						casGroupTest = "";
				}
				xsl = baseDir + xsl;
				xsl = xsl.replaceAll("\\.\\.",""); // prevent snooping
				output = SolrFormat.xslt(
					output, xsl, req.getParameterMap(), req.getQueryString(), casGroupTest
				);
				contentType = "text/xml";
			}
			else if ( format != null && format.equals("curator") )
			{
				// reformat json
				String userQuery = req.getParameter("q");
				output = SolrFormat.jsonFormatText( output, ds, null, userQuery );
			}
			else if ( format != null && format.equals("grid") )
			{
				// reformat json
				int PAGE_SIZE = 20;
				//int skip = (req.getParameter("start") != null ) ? Integer.parseInt(req.getParameter("start")) + 1 : 0;
				int rows = (req.getParameter("rows") != null) ? Integer.parseInt(req.getParameter("rows")) : PAGE_SIZE;
				//int page = (skip/rows) + 1;
				int page = -1;
				try
				{
					page = Integer.parseInt(req.getParameter("page"));
				}
				catch ( Exception ex ) { page = -1; }
			
				if(page == -1)
					page = 1;
				
				output = SolrFormat.jsonGridFormat( output, page, rows );
			}

			if ( contentType.indexOf("; charset=") == -1 )
			{
				contentType += "; charset=" + defaultEncoding;
			}

			// override content type
			if ( req.getParameter("contentType") != null )
			{
				contentType= req.getParameter("contentType");
			}

			// send output to client
			res.setContentType( contentType );
			PrintWriter out = res.getWriter();
			out.println( output );
			out.flush();
			out.close();
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			try
			{
				res.sendError( res.SC_INTERNAL_SERVER_ERROR, ex.toString() );
			}
			catch ( Exception e2 )
			{
			}
		}
		long dur = System.currentTimeMillis() - start;
		log.info("SolrProxy dur: " + dur + ", params: " + req.getQueryString());
	}
}
