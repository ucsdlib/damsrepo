package edu.ucsd.library.dams.api;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.text.NumberFormat;

import java.util.Properties;

import javax.naming.InitialContext;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.apache.log4j.Logger;

/**
 * Basic minter servlet, configured using DAMS_HOME/minter.properties (this file
 * will be created with default values if it doesn't exist).
 * @author escowles@ucsd.edu
**/
public class SimpleMinterServlet extends HttpServlet
{
	private static Logger log = Logger.getLogger(SimpleMinterServlet.class);
	private static NumberFormat fmt = null;
	private static File damsHome = null;

	/**
	 * Command-line operation.
	 * @param args Command-line arguments: can supply number of ids to mint.
	**/
	public static void main( String[] args ) throws Exception
	{
		int count = 1;
		try { count = Integer.parseInt(args[0]); } catch ( Exception ex ) {}
		mint( 1, new PrintWriter(System.out) );
	}

	/**
	 * Generate one or more identifiers.  The count parameter can be used to
	 * specify the number of identifiers to mint.
	**/
	public void doGet( HttpServletRequest req, HttpServletResponse res )
		throws IOException, ServletException
	{
		// get number of ids to mint
		int count = 1;
		String countStr = null;
		try
		{
			countStr = req.getParameter("count");
			if ( countStr != null && !countStr.equals("") )
			{
				count = Integer.parseInt( countStr );
			}
		}
		catch ( Exception ex )
		{
			log.warn( "Unable to parse count parameter: " + countStr );
		}

		// mint ids
		res.setContentType( "text/plain" );
		PrintWriter out = res.getWriter();
		mint( count, out );
		out.flush();
		out.close();
	}

	/**
	 * Mint identifiers and print them.
	 * @param count The number of identifiers to mint.
	 * @param out PrintWriter to print identifiers to.
	**/
	public static void mint( int count, PrintWriter out )
	{
		// setup dams home directory
		if ( damsHome == null )
		{
			try
			{
				InitialContext ctx = new InitialContext();
				damsHome = new File(
					(String)ctx.lookup("java:comp/env/dams/home")
				);
			}
			catch ( Exception ex )
			{
				damsHome = new File("dams");
				log.warn("Unable to lookup damsHome, using default: "
					+ damsHome.getAbsolutePath() );
			}
		}

		// load config properties
		File propsFile = new File( damsHome, "minter.properties" );
		Properties props = new Properties();
		try
		{
			props.load( new FileInputStream(propsFile) );
		}
		catch ( Exception ex )
		{
			log.warn("Error loading properties, using defaults");
		}

		// set initial state
		String prefix = getString( props, "prefix", "xx" );
		int lastID = getInt( props, "lastid", 0 );
		int digits = getInt( props, "digits", 8 );
		
		// setup number format
		if ( fmt == null )
		{
			fmt = NumberFormat.getIntegerInstance();
			fmt.setMinimumIntegerDigits(digits);
			fmt.setGroupingUsed(false);
		}

		// mint ids
		for ( int i = 0; i < count; i++ )
		{
			lastID++;
			out.println( "id: " + prefix + fmt.format(lastID) );
		}
		out.flush();

		// update properties
		props.setProperty( "lastid", String.valueOf(lastID) );
		try
		{
			FileOutputStream fos = new FileOutputStream(propsFile);
			props.store( fos, "DAMS Repo minter properties" );
			fos.close();
		}
		catch ( Exception ex )
		{
			log.warn("Error writing minter properties", ex );
		}
	}

	// get an int property (and set it to the default if not supplied)
	private static int getInt( Properties props, String key, int defaultVal )
	{
		int value = defaultVal;
		try
		{
			value = Integer.parseInt( props.getProperty(key) );
		}
		catch ( Exception ex )
		{
			value = defaultVal;
			props.setProperty( key, String.valueOf(defaultVal) );
		}
		return value;
	}

	// get a string property (and set it to the default if not supplied)
	private static String getString( Properties props, String key,
		String defaultVal )
	{
		String value = props.getProperty(key);
		if ( value == null )
		{
			value = defaultVal;
			props.setProperty( key, defaultVal );
		}
		return value;
	}
}
