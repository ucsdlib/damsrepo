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
	public void clientAuthorize( Map<String,String> params ) { }
	public void clientInfo( Map<String,String> params ) { }
	public void collectionCharacterize( String colid,
		Map<String,String> params ) { }
	public void collectionCount( String colid, Map<String,String> params ) { }
	public void collectionEmbargo( String colid, Map<String,String> params ) { }
	public void collectionDerivatives( String colid,
		Map<String,String> params ) { }
	public void collectionFixity( String colid, Map<String,String> params ) { }
	public void collectionIndexDelete( String colid,
		Map<String,String> params ) { }
	public void collectionIndexUpdate( String colid,
		Map<String,String> params ) { }
	public void collectionListAll( Map<String,String> params ) { }
	public void collectionListObjects( String colid,
		Map<String,String> params ) { }
	public void collectionTransform( String colid,
		Map<String,String> params ) { }
	public void collectionValidate( String colid,
		Map<String,String> params ) { }
	public void fileCharacterize( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileCreate( String objid, String fileid,
		Map<String,String> params, InputStream in ) { }
	public void fileDelete( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileDerivatives( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileFixity( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileShow( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileUpdate( String objid, String fileid,
		Map<String,String> params, InputStream in ) { }
	public void identifierCreate( Map<String,String> params ) { }
	public void indexSearch( Map<String,String> params ) { }
	public void objectCreate( String objid, Map<String,String> params,
		InputStream in ) { }
	public void objectDelete( String objid, Map<String,String> params ) { }
	public void objectIndexDelete( String objid, Map<String,String> params ) { }
	public void objectIndexUpdate( String objid, Map<String,String> params ) { }
	public void objectShow( String objid, boolean export,
		Map<String,String> params ) { }
	public void objectTransform( String objid, Map<String,String> params ) { }
	public void objectUpdate( String objid, Map<String,String> params,
		InputStream in ) { }
	public void objectValidate( String objid, Map<String,String> params ) { }
	public void predicateList( Map<String,String> params ) { }

	public void error( String msg ) { }

	/*************************************************************************/
	/* Fedora API                                                            */
	/*************************************************************************/
	public void relationshipCreate( String objid,
		Map<String,String> params ) { }
	public void relationshipDelete( String objid,
		Map<String,String> params ) { }
	public void describeRepository( Map<String,String> params ) { }
	public void fileHistory( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileProfile( String objid, String fileid,
		Map<String,String> params ) { }
	public void fileList( String objid, Map<String,String> params ) { }
	public void relationshipShow( String objid, Map<String,String> params ) { }
	public void objectVersions( String objid, Map<String,String> params ) { }
	public void objectProfile( String objid, Map<String,String> params ) { }


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
