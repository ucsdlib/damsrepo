package edu.ucsd.library.dams.triple;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;

import org.openrdf.model.Resource;
import org.openrdf.model.Value;
import org.openrdf.model.URI;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.BNodeImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.ntriples.NTriplesWriter;
import org.openrdf.rio.rdfxml.util.RDFXMLPrettyWriter;

import org.apache.log4j.Logger;

import edu.ucsd.library.dams.solr.SolrHelper;


/**
 * Utility methods for working with triplestore objects.
 * @author escowles
**/
public class TripleStoreUtil
{
	private static Logger log = Logger.getLogger( TripleStoreUtil.class );
	private static String ns = "http://libraries.ucsd.edu/ark:/20775/";

	/**
	 * Get an instance of a triplestore class.
	 * @param props Properties object holding parameters to initialize the 
	 *  triplestore.  Must contain at least the "className" property to
	 *  determine which triplestore class should be created.  Other parameters
	 *  required will depend on the triplestore implementation.
	**/
	public static TripleStore getTripleStore( Properties props )
		throws ClassNotFoundException, IllegalAccessException,
			InstantiationException, InvocationTargetException,
			NoSuchMethodException
	{
		String className = props.getProperty("className");
		Class c = Class.forName( className );
		Constructor constructor = c.getConstructor(new Properties().getClass());
		return (TripleStore)constructor.newInstance( props );
	}

	/**
	 * Set RDF output verbose reporting.
	**/
	public static void setDebug( boolean b ) { debug = b; }
	private static boolean debug = false;
	private static void outputRDF( StatementIterator iter, RDFWriter writer )
		throws TripleStoreException
	{
		try
		{
			writer.startRDF();
			for ( int i = 0; iter.hasNext(); i++ )
			{
				Statement stmt = iter.nextStatement();
				writer.handleStatement( sesameStatement( stmt ) );
				if ( (i+1) % 1000 == 0 && debug )
				{
					System.err.println( "outputRDF: " + (i+1) );
				}
			}
			writer.endRDF();
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException( ex );
		}
	}
	/**
	 * Output a set of Statements as RDF/XML.
	**/
	public static void outputRDFXML( StatementIterator iter, Writer writer )
		throws TripleStoreException
	{
		outputRDF( iter, new RDFXMLPrettyWriter( writer ) );
	}
	/**
	 * Output a set of Statements as NTriples.
	**/
	public static void outputNTriples( StatementIterator iter, Writer writer )
		throws TripleStoreException
	{
		outputRDF( iter, new NTriplesWriter( writer ) );
	}

	private static org.openrdf.model.Statement sesameStatement( Statement stmt )
	{
		Identifier subject = stmt.getSubject();
		Resource res = null;
		if ( subject.isBlankNode() )
		{
			res = new BNodeImpl( subject.getId() );
		}
		else
		{
			res = new URIImpl( subject.getId() );
		}
		URI pre = new URIImpl( stmt.getPredicate().getId() );
		Value obj = null;
		if ( stmt.hasLiteralObject() )
		{
			obj = new LiteralImpl( stmt.getLiteral() );
		}
		else if ( stmt.getObject().isBlankNode() )
		{
			obj = new BNodeImpl( stmt.getObject().getId() );
		}
		else
		{
			obj = new URIImpl( stmt.getObject().getId() );
		}
		return new org.openrdf.model.impl.StatementImpl( res, pre, obj );
	}

	/**
	 * Copy an object from one triplestore to another.
	 * @param src Source triplestore.
	 * @param dst Destination triplestore.
	 * @param id Identifier of the object to copy.
	 * @throws TripleStoreException On error reading or writing objects in the
	 *   triplestores.
	**/
	public static int copy( TripleStore src, TripleStore dst, Identifier id )
		throws TripleStoreException
	{
		int triples = 0;
		String q = "DESCRIBE <" + id.getId() + ">";
		StatementIterator it = src.sparqlDescribe( q );
		Map<String,Identifier> bnodes = new HashMap<String,Identifier>();
		while ( it.hasNext() )
		{
			Statement stmt = it.nextStatement();
			Identifier pre = stmt.getPredicate();

			// map subject bnode ids
			Identifier sub = stmt.getSubject();
			if ( sub.isBlankNode() )
			{
				Identifier newObj = bnodes.get(sub.getId());
				if ( newObj == null )
				{
					newObj = dst.blankNode();
					bnodes.put( sub.getId(), newObj );
				}
				sub = newObj;
			}

			if ( stmt.hasLiteralObject() )
			{
				// add statement
				dst.addLiteralStatement( sub, pre, stmt.getLiteral(), id );
			}
			else
			{
				// map object bnode ids
				Identifier obj = stmt.getObject();
				if ( obj.isBlankNode() )
				{
					Identifier newObj = bnodes.get(obj.getId());
					if ( newObj == null )
					{
						newObj = dst.blankNode();
						bnodes.put( obj.getId(), newObj );
					}
					obj = newObj;
				}

				// add statement
				dst.addStatement( sub, pre, obj, id );
			}

			triples++;
		}
		it.close();
		return triples;
	}

	/**
	 * Sync recently updated objects from one triplestore to another.  Records
	 * to update are found by performing a Solr search.  Each record is then
	 * deleted from the destination triplestore, and copied from the source
	 * triplestore.
	 * @param src Source triplestore with updated objects.
	 * @param dst Destination triplestore where objects are copied to.
	 * @param solrBase Solr base URL
	 * @param solrCore Solr core.
	 * @param category Optional category to limit search.
	 * @param category Optional: limit search to objects in this category.
	 * @param minutes Optional: limit search to objects updated within this many
	 *   minutes.
	 * @throws IOException On error searching Solr or parsing the results.
	 * @throws TripleStoreException On error reading or writing objects in the
	 *   triplestores.
	**/
	public static void sync( TripleStore src, TripleStore dst, String solrBase,
		String solrCore, String category, int minutes )
		throws IOException, TripleStoreException
	{
		Date d = null;
		if ( minutes > -1 )
		{
			//entereddate:[2010-07-28 TO *]
			Calendar now = Calendar.getInstance();
			now.add( Calendar.MINUTE, (-1*minutes) );
			d = now.getTime();
		}
		sync( src, dst, solrBase, solrCore, category, d );
	}
	
	/**
	 * Sync recently updated objects from one triplestore to another.  Records
	 * to update are found by performing a Solr search.  Each record is then
	 * deleted from the destination triplestore, and copied from the source
	 * triplestore.
	 * @param src Source triplestore with updated objects.
	 * @param dst Destination triplestore where objects are copied to.
	 * @param solrBase Solr base URL
	 * @param solrCore Solr core.
	 * @param category Optional: limit search to objects in this category.
	 * @param date Optional: limit search to objects updated after this date.
	 * @throws IOException On error searching Solr or parsing the results.
	 * @throws TripleStoreException On error reading or writing objects in the
	 *   triplestores.
	**/
	public static void sync( TripleStore src, TripleStore dst, String solrBase,
		String solrCore, String category, Date date )
		throws IOException, TripleStoreException
	{
		// build solr query
		String q = "";
		if ( category != null && !category.equals("") )
		{
			q = "category:" + category;
		}
		if ( date != null )
		{
			//entereddate:[2010-07-28 TO *]
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm");
			String d = fmt.format( date );
			if ( !q.equals("") )
			{
				q += " AND ";
			}
			q += "entereddate:[" + d + " TO *]";
		}
		if ( q.equals("") )
		{
			q = "*:*";
		}

		// search solr
		List srcSub = null;
		try
		{
			SolrHelper solr = new SolrHelper( solrBase );
			String solrXml = solr.search(
				solrCore, q, -1, 100, null, null, null
			);
			Document solrDoc = DocumentHelper.parseText(solrXml);
			srcSub = solrDoc.selectNodes(
				"/response/result/doc/str[@name='subject']"
			);
		}
		catch ( DocumentException ex )
		{
			throw new IOException( "Error parsing Solr XML",ex );
		}

		// copy objects
		int objects = 0;
		int triples = 0;
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(2);
		nf.setMinimumFractionDigits(2);
		nf.setGroupingUsed(false);
		long start = System.currentTimeMillis();
		for ( int i = 0; srcSub != null && i < srcSub.size(); i++ )
		{
			Node n = (Node)srcSub.get(i);
			String id = n.getText();
			log.debug("syncing: " + id);
			Identifier subj = Identifier.publicURI(ns+id);

			// remove stale object from dst
			dst.removeObject( subj );

			// copy updated object
			triples += TripleStoreUtil.copy( src, dst, subj );

			// report
			objects++;
			id = id.replaceAll(".*/","");
			float dur = (float)(System.currentTimeMillis() - start)/1000;
			float rate = (float)objects/dur;
			float pct = (float)((i+1)*100)/srcSub.size();
			log.debug(
				id + ": " + objects + "/" + nf.format(dur) + " secs ("
				+ nf.format(pct) + "%, " + nf.format(rate) + "/sec)"
			);
		}
	}
}
