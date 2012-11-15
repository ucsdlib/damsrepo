package edu.ucsd.library.dams.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.StringWriter;

import org.apache.pdfbox.exceptions.CryptographyException;
import org.apache.pdfbox.exceptions.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

/**
 * Class to extract text from PDF files.
 * @author escowles@ucsd.edu
**/
public class PDFParser
{
	/**
	 * Extract the text from a PDF file.
	 * @param is InputStream containing the PDF's content.
	 * @param documentLocation Name of the file for debugging output.
	 * @throws IOException On error reading or decoding the input.
	**/
	public static String getContent(InputStream is, String documentLocation)
		throws IOException
	{
		PDDocument pdfDocument = null;

		String contents = null;
		try
		{
			pdfDocument = PDDocument.load( is );

			if( pdfDocument.isEncrypted() )
			{
				//Just try using the default password and move on
				pdfDocument.decrypt( "" );
			}

			//create a writer where to append the text content.
			StringWriter writer = new StringWriter();
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.writeText( pdfDocument, writer );

			// Note: the buffer to string operation is costless;
			// the char array value of the writer buffer and the content string
			// is shared as long as the buffer content is not modified, which will
			// not occur here.
			contents = writer.getBuffer().toString();

		} catch( CryptographyException e )
		{
			throw new IOException( "Error decrypting document(" + documentLocation + "): " + e );
		}
		catch( InvalidPasswordException e )
		{
			//they didn't suppply a password and the default of "" was wrong.
			throw new IOException( "Error: The document(" + documentLocation +
									") is encrypted and will not be indexed." );
		}
		finally
		{
			if( pdfDocument != null )
			{
				pdfDocument.close();
			}
		}
		return contents;
	}
	public static void main( String[] args ) throws Exception
	{
		System.out.println(
			PDFParser.getContent( new FileInputStream(args[0]), args[0] )
		);
	}
}
