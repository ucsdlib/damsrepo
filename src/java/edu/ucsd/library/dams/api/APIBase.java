package edu.ucsd.library.dams.api;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// post/put file attachments
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * Base class containing utility methods for API endpoints.
 * @author escowles@ucsd.edu
**/
public class APIBase
{
	/* XXX: automatically map POST/X-HTTP-Method-Override to DEL/PUT? */
	private static final long maxUploadSize = 524288000; // 500MB XXX config

	/*************************************************************************/
	/* Core Java API                                                         */
	/*************************************************************************/
	public void createFile( String id, String file, Map<String,String> params,
		InputStream in ) { }
	public void createIdentifier( Map<String,String> params ) { }
	public void createObject( String id, Map<String,String> params,
		InputStream in ) { }
	public void createRelationship( String id, Map<String,String> params ) { }
	public void deleteFile( String id, String file,
		Map<String,String> params ) { }
	public void deleteObject( String id, Map<String,String> params ) { }
	public void deleteRelationship( String id, Map<String,String> params ) { }
	public void describeRepository( Map<String,String> params ) { }
	public void error( String msg ) { }
	public void fileHistory( String id, String file,
		Map<String,String> params ) { }
	public void fileMetadata( String id, String file,
		Map<String,String> params ) { }
	public void getFile( String id, String file, Map<String,String> params ) { }
	public void listFiles( String id, Map<String,String> params ) { }
	public void listRelationships( String id, Map<String,String> params ) { }
	public void listVersions( String id, Map<String,String> params ) { }
	public void objectMetadata( String id, boolean export,
		Map<String,String> params ) { }
	public void objectProfile( String id, Map<String,String> params ) { }
	public void searchRepository( Map<String,String> params ) { }
	public void updateFile( String id, String file, Map<String,String> params,
		InputStream in ) { }
	public void updateObject( String id, Map<String,String> params,
		InputStream in ) { }
	public void validateObject( String id, Map<String,String> params ) { }


	/*************************************************************************/
	/* Utility methods                                                       */
	/*************************************************************************/
	protected static String[] path( HttpServletRequest req )
	{
		String pathstr = req.getPathInfo();
		if ( pathstr == null ) { return new String[]{}; }
		else { return pathstr.split("/"); }
	}
	protected static Map<String,String> parameters( HttpServletRequest req )
	{
		Map<String,String> params = new HashMap<String,String>();
		Enumeration<String> e = req.getParameterNames();
		while ( e.hasMoreElements() )
		{
			String key = e.nextElement();
			params.put( key, req.getParameter(key) );
		}
		return params;
	}
	protected static void input( HttpServletRequest req,
		Map<String,String> params, InputStream in )
	{
		if ( params == null ) { params = new HashMap<String,String>(); }

		if ( ! ServletFileUpload.isMultipartContent(req) )
		{
			Enumeration<String> e = req.getParameterNames();
			while ( e.hasMoreElements() )
			{
				String key = e.nextElement();
				params.put( key, req.getParameter(key) );
			}
			in = null;
			return;
		}

		// process parts
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload( factory );
		upload.setSizeMax( maxUploadSize );
		List items = upload.parseRequest( req );
		for ( int i = 0; i < items.size(); i++ )
		{
			FileItem item = (FileItem)items.get(i);
			// form fields go in parameter map
			if ( item.isFormField() )
			{
				params.put( item.getFieldName(), item.getString() );
			}
			// file gets opened as an input stream
			else
			{
				in = item.getInputStream();
			}
		}
	}
}
