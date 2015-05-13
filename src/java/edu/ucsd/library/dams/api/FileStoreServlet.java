/*
 * net/balusc/webapp/FileServlet.java
 *
 * Copyright (C) 2009 BalusC
 *
 * This program is free software: you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public
 * License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see <http://www.gnu.org/licenses/>.
 */

package edu.ucsd.library.dams.api;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;
import java.text.SimpleDateFormat;

import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * A file servlet supporting client-side caching and GZIP of text content.
 * This servlet can also be used for images, client-side caching would become
 * more efficient. This servlet can also be used for text files, GZIP would
 * decrease network bandwidth.
 *
 * @author BalusC
 * @link http://balusc.blogspot.com/2009/02/fileservlet-supporting-resume-and.html
 *
 * Retrieved 2011-02-04, modified with filename/path, authorization logic, etc.
 * Ported to exclusive FileStore usage 2012-05-23.
 * @author escowles@ucsd.edu
 * @author lsitu@ucsd.edu
 */
public class FileStoreServlet extends HttpServlet
{
	/* begin ucsd changes */
	private static Logger log = Logger.getLogger( FileStoreServlet.class );
	/* end ucsd changes */

	// Constants ---------------------------------------------------------
	private static final int DEFAULT_BUFFER_SIZE = 10240; // ..bytes = 10KB.
	private static final long DEFAULT_EXPIRE_TIME = 604800000L; // ..ms = 1 week

	// Properties --------------------------------------------------------
	private String fsDefault;
	private Properties props;
	private SimpleDateFormat df;

	// Actions -----------------------------------------------------------

	/**
	 * Initialize the servlet.
	 * @see HttpServlet#init().
	 */
	public void init() throws ServletException
	{
		/* begin ucsd changes */
		try
		{
            InitialContext ctx = new InitialContext();
            String damsHome = null;
			try
			{
				damsHome = (String)ctx.lookup("java:comp/env/dams/home");
			}
			catch ( Exception ex )
			{
				damsHome = "dams";
			}
            File f = new File( damsHome, "dams.properties" );
            props = new Properties();
            props.load( new FileInputStream(f) );
			fsDefault = props.getProperty("fs.default");
			df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
			// RFC 822, Wed, 23 May 2012 11:54:18 GMT
		}
		catch ( Exception ex )
		{
			log.warn("Unable to lookup default filestore", ex );
			throw new ServletException("Unable to lookup default filestore");
		}
		/* end ucsd changes */
	}

	/**
	 * Process HEAD request. This returns the same headers as GET request, but
	 * without content.
	 * @see HttpServlet#doHead(HttpServletRequest, HttpServletResponse).
	 */
	protected void doHead( HttpServletRequest request,
		HttpServletResponse response ) throws ServletException, IOException
	{
		// Process request without content.
		processRequest(request, response, false);
	}

	/**
	 * Process GET request.
	 * @see HttpServlet#doGet(HttpServletRequest, HttpServletResponse).
	 */
	protected void doGet( HttpServletRequest request,
		HttpServletResponse response ) throws ServletException, IOException
	{
		// Process request with content.
		processRequest(request, response, true);
	}

	/**
	 * Process the actual request.
	 * @param request The request to be processed.
	 * @param response The response to be created.
	 * @param content Whether the request body should be written (GET) or not
	 *  (HEAD).
	 * @throws IOException If something fails at I/O level.
	 */
	private void processRequest ( HttpServletRequest request,
		HttpServletResponse response, boolean content) throws IOException
	{
		// Validate the requested file -------------------------------------

		// Get requested file by path info.
		/* start ucsd changes */

		// get object and file ids from path
		String objid = null;
		String cmpid = null;
		String fileid = null;
		try
		{
			// /bb1234567x/1.tif
			// /bb1234567x/1/2.tif
			String[] path = request.getPathInfo().split("/");
			if ( path.length == 3 )
			{
				objid = path[1];
				fileid = path[2];
			}
			else if ( path.length == 4 )
			{
				objid = path[1];
				cmpid = path[2];
				fileid = path[3];
			}
		}
		catch (Exception e)
		{
			log.info(
				"Error parsing request pathInfo: " + request.getPathInfo()
			);
		}

		// make sure required parameters are populated
		if ( objid == null || objid.trim().length() == 0
			|| fileid == null || fileid.trim().length() == 0 )
		{
			try
			{
				response.setContentType("text/plain");
				response.sendError( response.SC_BAD_REQUEST,
					"Subject and file must be specified in the request URI" );
				return;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		String fullFilename = objid + "-" + fileid;

		// first load the FileStore (no point if this doesn't work)
		FileStore fs = null;
		long fsTime = 0;
		try
		{
			long start = System.currentTimeMillis();
			fs = FileStoreUtil.getFileStore( props, fsDefault );
			fsTime = System.currentTimeMillis() - start;
		}
		catch ( Exception ex )
		{
			response.setContentType("text/plain");
			response.sendError(
				response.SC_INTERNAL_SERVER_ERROR,
				"Error initializing FileStore"
			);
			ex.printStackTrace();
			return;
		}

		// check authorization attribute
		String restricted = null;
		String authorized = (String) request.getAttribute(
			"edu.ucsd.library.dams.api.DAMSAPIServlet.authorized"
		);
		if(authorized == null || !authorized.equals("true"))
		{
			log.info("Illegal Access from IP " + request.getRemoteAddr()
				+ " for file " + fullFilename);
			response.setContentType("text/plain");
			response.sendError( HttpServletResponse.SC_FORBIDDEN,
				"Access without authorization.");
			return;
		}
		else
		{
			log.info( "DAMS Access authorized for IP " + request.getRemoteAddr()
					+ " for file " + fullFilename);
			restricted = (String)request.getAttribute("pas.restricted");
			//Disable browser caching for restricted objects.
			if(restricted != null && restricted.equals("1"))
			{
				String browser = request.getHeader("User-Agent");
				if(browser != null && browser.indexOf("MSIE") != -1)
				{
					response.addHeader("Cache-Control",
						"post-check=0, pre-check=0");
				}
				else
				{
					response.setHeader("Cache-Control",
						"no-store, no-cache, must-revalidate");
				}
				response.setHeader("Pragma", "no-cache");
				response.setHeader("Expires", "0");
			}
		}
		/* end ucsd changes */
		
		// load file metadata
		Map<String,String> meta = null;
		long metaTime = 0;
		try
		{
			long start = System.currentTimeMillis();
			meta = fs.meta( objid, cmpid, fileid );
			metaTime = System.currentTimeMillis() - start;
		}
		catch ( Exception ex )
		{
			log.info("File " + fullFilename + " doesn't exist.");
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		// Prepare some variables. The ETag is an unique identifier of the file
		String length = meta.get("Content-Length");
		String lastModStr = meta.get("Last-Modified");
		long lastModified = 0L;
		try
		{
			lastModified = df.parse( lastModStr ).getTime();
		}
		catch ( Exception ex )
		{
			// error parsing lastmod date... set to now
			lastModified = System.currentTimeMillis();
		}
		String eTag = meta.get("ETag");
		if ( eTag == null )
		{
			eTag = fullFilename + "_" + length + "_" + lastModified;
		}


		// Validate request headers for caching -----------------------------

		// If-None-Match header should contain "*" or ETag. If so, return 304.
		String ifNoneMatch = request.getHeader("If-None-Match");
		if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
			response.setHeader("ETag", eTag); // Required in 304.
			response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}

		// If-Modified-Since header should be greater than LastModified. If so,
		// then return 304.
		// This header is ignored if any If-None-Match header is specified.
		long ifModifiedSince = request.getDateHeader("If-Modified-Since");
		if (ifNoneMatch == null && ifModifiedSince != -1
			&& ifModifiedSince + 1000 > lastModified) {
			response.setHeader("ETag", eTag); // Required in 304.
			response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}


		// Validate request headers for resume ------------------------------

		// If-Match header should contain "*" or ETag. If not, then return 412.
		String ifMatch = request.getHeader("If-Match");
		if (ifMatch != null && !matches(ifMatch, eTag)) {
			response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		// If-Unmodified-Since header should be greater than LastModified.
		// If not, then return 412.
		long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
		if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified)
		{
			response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return;
		}

		// Prepare and initialize response ----------------------------------

		// Get content type by file name and set default GZIP support and
		// content disposition.
		String contentType = getServletContext().getMimeType(fullFilename);
		boolean acceptsGzip = false;
		String disposition = "inline";

		// If content type is unknown, then set the default value.  For all
		// content types, see: http://www.w3schools.com/media/media_mimeref.asp
		// To add new content types, add new mime-mapping entry in web.xml.
		if (contentType == null) {
			contentType = "application/octet-stream";
		}
		
		//If UCSD download
		boolean download = request.getParameter("download") != null;
		if( download ){
			disposition = "attachment";
			contentType = "application/x-download";
		}
		// Else if content type is text, then determine whether GZIP content
		// encoding is supported by the browser and expand content type with
		// the one and right character encoding.
		else if (contentType.startsWith("text")) {
			//String acceptEncoding = request.getHeader("Accept-Encoding");
			//acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
			contentType += ";charset=UTF-8";
		} 

		// Else, expect for images, determine content disposition. If content
		// type is supported by the browser, then set to inline, else
		// attachment which will pop a 'save as' dialogue.
		else if (!contentType.startsWith("image")) {
			String accept = request.getHeader("Accept");
			disposition = accept != null && accepts(accept, contentType) ? "inline" : "attachment";
		}

		String sFileName = request.getParameter("name");
		if(sFileName == null || (sFileName=sFileName.trim()).length()==0)
			sFileName = fullFilename;
		
		// Initialize response.
		response.reset();
		response.setBufferSize(DEFAULT_BUFFER_SIZE);
		response.setHeader("Content-Disposition",
			disposition + ";filename=\"" + sFileName + "\"");
		response.setHeader("ETag", eTag);
		response.setDateHeader("Last-Modified", lastModified);
		/* begin ucsd changes */
		if( restricted == null || !restricted.equals("1") )
		{
			response.setDateHeader("Expires",
				System.currentTimeMillis() + DEFAULT_EXPIRE_TIME);
		}
		/* end ucsd changes */

		// Send requested file to client ------------------------------------

		// Prepare streams.
		InputStream input = null;
		OutputStream output = null;
		long fileTime = 0;
		if (content)
		{
			try
			{
				long start = System.currentTimeMillis();
				// Open streams.
				input = fs.getInputStream(objid,cmpid,fileid);
				output = response.getOutputStream();
				response.setContentType(contentType);
				if (acceptsGzip)
				{
					// The browser accepts GZIP, so GZIP the content.
					response.setHeader("Content-Encoding", "gzip");
					output = new GZIPOutputStream(output, DEFAULT_BUFFER_SIZE);
				}
				else
				{
					// Content length is not directly predictable in case of
					// GZIP. So only add it if there is no means of GZIP, else
					// browser will hang.
					response.setHeader("Content-Length", length);
				}

				// Copy full range.
				/* begin ucsd changes */
				FileStoreUtil.copy(input, output);
				fileTime = System.currentTimeMillis() - start;
				/* begin ucsd changes */
			}
			catch ( Exception ex )
			{
				log.info("Error reading " + fullFilename, ex );
			}
			finally
			{
				/* begin ucsd changes */
				log.info("Time in miliseconds to retrival file " + fullFilename + "(" + length + " bytes)" + ": Total " + (fsTime + metaTime + fileTime) + "[FileStore initiation: " + fsTime +  "; Metadata query: " + metaTime + "; File download: " + fileTime + "]");
				/* begin ucsd changes */
				// Gently close streams.
				close(output);
				close(input);
			}
		}
	}

	// Helpers (can be refactored to public utility class) --------------------

	/**
	 * Returns true if the given accept header accepts the given value.
	 * @param acceptHeader The accept header.
	 * @param toAccept The value to be accepted.
	 * @return True if the given accept header accepts the given value.
	 */
	private static boolean accepts(String acceptHeader, String toAccept) {
		String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
		Arrays.sort(acceptValues);
		return Arrays.binarySearch(acceptValues, toAccept) > -1
			|| Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
			|| Arrays.binarySearch(acceptValues, "*/*") > -1;
	}

	/**
	 * Returns true if the given match header matches the given value.
	 * @param matchHeader The match header.
	 * @param toMatch The value to be matched.
	 * @return True if the given match header matches the given value.
	 */
	private static boolean matches(String matchHeader, String toMatch) {
		String[] matchValues = matchHeader.split("\\s*,\\s*");
		Arrays.sort(matchValues);
		return Arrays.binarySearch(matchValues, toMatch) > -1
			|| Arrays.binarySearch(matchValues, "*") > -1;
	}

	/**
	 * Close the given resource.
	 * @param resource The resource to be closed.
	 */
	private static void close(Closeable resource) {
		if (resource != null) {
			try {
				resource.close();
			} catch (IOException ignore) {
				// Ignore IOException. If you want to handle this anyway, it
				// might be useful to know that this will generally only be
				// thrown when the client aborted the request.
			}
		}
	}
}
