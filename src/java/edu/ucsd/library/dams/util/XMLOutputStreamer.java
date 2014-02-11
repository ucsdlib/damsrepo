package edu.ucsd.library.dams.util;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

// xml streaming
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

/**
 * OutputStreamer implementationf or XML.
 * @author escowles
**/
public class XMLOutputStreamer implements OutputStreamer
{
    private XMLStreamWriter stream;
    public XMLOutputStreamer( HttpServletResponse res )
		throws Exception
    {
		// setup output stream
		res.setContentType("application/xml");
		PrintWriter out = new PrintWriter(
			new OutputStreamWriter(res.getOutputStream(), "UTF-8")
		);
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        this.stream = factory.createXMLStreamWriter(out);
    }
    public void start( String groupName ) throws Exception
    {
        // start document
        stream.writeStartDocument();
        stream.writeStartElement("response");
       	stream.writeStartElement(groupName);
    }
    public void output( Map<String,String> record ) throws Exception
    {
        stream.writeStartElement("value");
        inner( record );
        stream.writeEndElement();
    }
    private void inner( Map<String,String> record ) throws Exception
    {
        for ( Iterator<String> it = record.keySet().iterator(); it.hasNext(); )
        {
            String key = it.next();
            String val = record.get(key);
            stream.writeStartElement(key);
            stream.writeCharacters( val );
            stream.writeEndElement();
        }
    }
    public void finish( Map<String,String> info ) throws Exception
    {
        // finish records
        stream.writeEndElement();

        // write request metadata
        inner( info );

        // finish response
        stream.writeEndElement();

        // cleanup
        stream.writeEndDocument();
        stream.flush();
        stream.close();
    }
}
