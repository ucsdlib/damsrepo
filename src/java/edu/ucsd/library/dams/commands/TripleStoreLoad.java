package edu.ucsd.library.dams.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Command-line data loading app.
 * @author escowles@ucsd.edu
**/
public class TripleStoreLoad
{
	private static long startTime = 0L;
	private static long startTriples = 0L;
	private static int records = 0;
	private static TripleStore ts = null;
	private static final int BATCH_SIZE = 1000;

	private static Set<String> validClasses = null;
	private static Set<String> validProperties = null;
 
	/**
	 * Command-line operation.
	 * @param args The first argument must be a properties file for triplestore
	 *  connection.  Other arguments can be files in RDF XML or N-Triples format
	 *  or directories containing the files.
	**/
	public static void main( String[] args ) throws Exception
	{
		try
		{
			startTime = System.currentTimeMillis();

			// get TripleStore instance
			Properties props = new Properties();
			props.load( new FileInputStream(args[0]) );
			String tsName = args[1];
			ts = TripleStoreUtil.getTripleStore( props, tsName );

			// parse class/property lists for validation
			validClasses = loadSet(args[2]);
			validProperties = loadSet(args[3]);

			// model size
			startTriples = ts.size();
			System.out.println( "size: " + startTriples );

			// add bulk Statements
			for ( int i = 4; i < args.length; i++ )
			{
				File f = new File( args[i] );
				if ( f.isDirectory() )
				{
					processDirectory( f );
				}
				else if ( f.isFile() )
				{
					processFile( f );
				}
			}
			ts.optimize();

			// output progress info
			outputStatus( -1, true );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
		}
		finally
		{
			// close the model and database connection
			System.out.println("closing connection");
			if ( ts != null ) { ts.close(); }
		}
	}

	public static void outputStatus( int i, boolean verbose )
	{
		try
		{
			// model size
			long loadingTime = System.currentTimeMillis() - startTime;
			long triplesLoaded = ts.size() - startTriples;
			float loadingRate = (float)triplesLoaded/((float)loadingTime/1000);
			if ( verbose )
			{
				System.out.println( "total triples.....: " + ts.size() );
				System.out.println( "triples loaded....: " + triplesLoaded );
				System.out.println( "loading time (ms).: " + loadingTime );
				System.out.println( "loading rate (t/s): " + loadingRate );
			}
			else
			{
				if ( i != -1 ) { System.out.print( i + ": " ); }
				System.out.println(
					triplesLoaded + " / " + loadingTime + "ms ("
						+ loadingRate+ " t/s)"
				);
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace();
			//System.exit(1);
		}
	}

	public static void processDirectory( File f )
	{
		String name = null;
		try
		{
			name = f.getName().toLowerCase();
			System.err.println("dir: " + f.getAbsolutePath() );
			File[] children = f.listFiles();
			for ( int i = 0; i < children.length; i++ )
			{
				if ( children[i].isFile() )
				{
					processFile( children[i] );
				}
				else if ( children[i].isDirectory() )
				{
					processDirectory( children[i] );
				}
			}
		}
		catch ( Exception ex )
		{
			System.err.println(
				"Error processing " + name + ": " + ex.toString()
			);
			ex.printStackTrace();
		}
	}
	public static void processFile( File f )
	{
		String name = null;
		try
		{
			name = f.getName().toLowerCase();
			System.out.println( f.getName() );
			if ( name.endsWith(".rdf") || name.endsWith(".xml") )
			{
				ts.loadRDFXML( f.getAbsolutePath(), validClasses, validProperties );
				records++;
			}
			else if ( name.endsWith(".nt") || name.endsWith(".ntriples") )
			{
				ts.loadNTriples( f.getAbsolutePath(), validClasses, validProperties );
				records++;
			}
			else
			{
				System.err.println("Unknown format: " + f.getAbsolutePath());
			}
			if ( records > 0 && records % BATCH_SIZE == 0 )
			{
				outputStatus( records, false );
			}
		}
		catch ( Exception ex )
		{
			System.err.println(
				"Error processing " + name + ": " + ex.toString()
			);
			ex.printStackTrace();
			System.exit(1);
		}
	}
	private static Set<String> loadSet( String filename ) throws IOException
	{
		if ( filename.trim().equals("") )
		{
			return null;
		}
		BufferedReader buf = new BufferedReader( new FileReader(filename) );
		try
		{
			Set<String> set = new HashSet<>();
			for ( String line = null; (line = buf.readLine()) != null; )
			{
				set.add( line );
			}
			System.err.println("Loaded " + set.size() + " values from " + filename);
			return set;
		}
		finally
		{
			buf.close();
		}
	}

}
