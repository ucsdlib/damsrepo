package edu.ucsd.library.dams.commands;

import java.io.FileInputStream;
import java.util.Properties;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.Statement;

import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

/**
 * Shutdown a triplestore.
 * @author escowles@ucsd.edu
**/
public class TripleStoreShutdown
{
	public static void main( String[] args ) throws Exception
	{
		Properties props = new Properties();
		props.load( new FileInputStream(args[0]) );
		String tsName = args[1];

        String dsURL   = props.getProperty( "ts." + tsName + ".dataSourceURL");
        String dsUser  = props.getProperty( "ts." + tsName + ".dataSourceUser");
        String dsPass  = props.getProperty( "ts." + tsName + ".dataSourcePass");
		String dsClass = props.getProperty( "ts." + tsName + ".driverClass");
		Class c = Class.forName( dsClass );
		Driver driver = (Driver)c.newInstance();
		DriverManager.registerDriver( driver );
		Connection con = DriverManager.getConnection( dsURL, dsUser, dsPass );

		Statement stmt = con.createStatement();
		System.out.println("Shutdown: " + tsName);
		stmt.execute("shutdown");
		stmt.close();
		con.close();
	}
}
