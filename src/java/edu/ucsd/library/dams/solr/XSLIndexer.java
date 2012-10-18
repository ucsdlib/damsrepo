package edu.ucsd.library.dams.solr;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.dom4j.Document;
import org.dom4j.io.DocumentResult;

/**
 * Class to extract values from RDF using XSL.
 * @author escowles
**/
public class XSLIndexer
{
	List<Transformer> transforms = null;

	/**
	 * Create a XSLIndexer instance.
	 * @param xslDir Directory containing XSL stylesheets that output the values
	 *   to be indexed.
	**/
	public static XSLIndexer fromFiles( List<File> xslFiles )
		throws IOException, TransformerException
	{
		// instantiate transforms
		List<Transformer> transforms = new ArrayList<Transformer>();
		TransformerFactory factory = TransformerFactory.newInstance();
		for ( int i = 0; i < xslFiles.size(); i++ )
		{
			File f = xslFiles.get(i);
			if ( f != null && f.isFile() )
			{
				try
				{
					StreamSource xslSource = new StreamSource(
						new FileReader( f )
					);
					Transformer transformer = factory.newTransformer( xslSource );
					transforms.add( transformer );
				}
				catch ( Exception ex )
				{
					System.err.println("XSLIndexer: couldn't create transform for " + f.getName() + ": " + ex.toString() );
				}
			}
		}
		return new XSLIndexer( transforms );
	}

	/**
	 * Create a XSLIndexer instance.
	 * @param transforms List of Transformer objects that produce Solr XML.
	**/
	public XSLIndexer( List<Transformer> transforms )
		throws IOException, TransformerException
	{
		this.transforms = transforms;
	}

	/**
	 * Extract RDF from an XML document.
	**/
	public List<Document> indexRDF( String xml )
		throws TransformerException, IOException
	{
		List<Document> values = new ArrayList<Document>();
		for ( int i = 0; i < transforms.size(); i++ )
		{
			Transformer transformer = transforms.get(i);

			// xml source
			Source xmlSource = new StreamSource(
				new StringReader(xml)
			);

			// result
			DocumentResult result = new DocumentResult();
			transformer.transform( xmlSource, result );
			values.add( result.getDocument() );
		}

		return values;
	}
}
