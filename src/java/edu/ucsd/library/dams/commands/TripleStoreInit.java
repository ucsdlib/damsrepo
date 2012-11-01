package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Initialize a new triplestore.
 * @author escowles@ucsd.edu
**/
public class TripleStoreInit
{
    public static void main( String[] args ) throws Exception
    {
        System.out.println("loading configuration");
        Properties props = new Properties();
        props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

        // get TripleStore instance
        System.out.println("creating triplestore object");
        TripleStore ts = TripleStoreUtil.getTripleStore( props, tsName );

        // initialize ts
        System.out.println("initializing...");
        ts.init();

        // check model size as a sanity check
        System.out.println( "model size: " + ts.size() );

        // close the model and database connection
        System.out.println("closing connection");
        ts.close();
    }
}
