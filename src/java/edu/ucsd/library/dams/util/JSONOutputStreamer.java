package edu.ucsd.library.dams.util;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;

/**
 * OutputStreamer implementation for JSON.
 * @author escowles
**/
public class JSONOutputStreamer implements OutputStreamer
{
	PrintWriter out = null;
	boolean firstRecord = true;
    public JSONOutputStreamer( HttpServletResponse res )
		throws Exception
    {
		// setup output stream
		res.setContentType("application/json");
		this.out = new PrintWriter(
			new OutputStreamWriter(res.getOutputStream(), "UTF-8")
		);
    }
    public void start( String groupName ) throws Exception
    {
        // start document
		out.println( "{ \"" + JSONObject.escape(groupName) + "\": [ ");
    }
    public void output( Map<String,String> record ) throws Exception
    {
		if ( !firstRecord )
		{
			out.println(",");
		}
		else
		{
			firstRecord = false;
		}
		out.print("{");
        inner( record );
		out.print("}");
    }
    private void inner( Map<String,String> record ) throws Exception
    {
        for ( Iterator<String> it = record.keySet().iterator(); it.hasNext(); )
        {
			// values
            String key = it.next();
			String val = JSONObject.escape( record.get(key) );
			out.print( "\"" + JSONObject.escape(key) + "\":\"" + val + "\"" );
			if ( it.hasNext() ) { out.print(","); }
        }
    }
    public void finish( Map<String,String> info ) throws Exception
    {
        // finish records
		if ( info.size() > 0 )
		{
			out.println("],");
		}
		else
		{
			out.println("]");
		}

        // write request metadata
        inner( info );

        // finish response
        out.println("}");

        // cleanup
        out.flush();
		out.close();
    }
}
