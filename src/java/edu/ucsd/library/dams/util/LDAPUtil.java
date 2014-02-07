package edu.ucsd.library.dams.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.NamingException;
import javax.naming.NameNotFoundException;
import javax.naming.PartialResultException;

/**
 * Utility to lookup properties from LDAP.
 * @author escowles
**/
public class LDAPUtil
{
	private Properties props;
	private String ldapURL;
	private String ldapPrincipal;
	private String ldapPass;
	private String queryPrefix;
	private String querySuffix;
	private String groupAttribute;
	private String nameAttribute;
	private String[] defaultAttributes;

	public LDAPUtil( Properties props )
	{
		// ldap authorization
		this.props= props;

		ldapURL = props.getProperty( "ldap.url" );
		ldapPrincipal = props.getProperty( "ldap.principal" );
		ldapPass = props.getProperty( "ldap.pass" );
		queryPrefix = props.getProperty( "ldap.queryPrefix" );
		querySuffix = props.getProperty( "ldap.querySuffix" );

		String tmp = props.getProperty("ldap.defaultAttributes");
		if ( tmp != null )
		{
			defaultAttributes = tmp.split(",");
		}
	}

	public Map<String,List<String>> lookup( String user, String[] attribs )
		throws NamingException
	{
		Map<String,List<String>> info = new HashMap<String,List<String>>();

		// check config and parameter
		if ( user == null || user.trim().equals("") || ldapURL == null
			|| ldapPrincipal == null )
		{
			return info;
		}

		if ( attribs == null ) { attribs = defaultAttributes; }

		String ldapQuery = queryPrefix + user + querySuffix;

		Hashtable<String,String> env = new Hashtable<String,String>();
		env.put(
			Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory"
		);
		env.put(Context.PROVIDER_URL, ldapURL);
		env.put(Context.SECURITY_PRINCIPAL, ldapPrincipal);
		env.put(Context.SECURITY_CREDENTIALS, ldapPass);
		if( ldapURL.startsWith("ldaps") )
		{
			env.put(Context.SECURITY_PROTOCOL, "ssl");
		}

		DirContext ctx = null;
		try
		{
			ctx = new InitialDirContext(env);
		}
		catch ( PartialResultException prex ) {}

		// get properties
		Attributes results = ctx.getAttributes( ldapQuery, attribs );
		for ( int i = 0; i < attribs.length; i++ )
		{
			List<String> list = new ArrayList<String>();
			Attribute values = results.get( attribs[i] );
			String m  = props.getProperty("ldap." + attribs[i] + ".match");
			String t1 = props.getProperty("ldap." + attribs[i] + ".trimstart");
			String t2 = props.getProperty("ldap." + attribs[i] + ".trimend");
			for ( int j = 0; values != null && j < values.size(); j++ )
			{
				String val = (String)values.get(j);
				if ( m == null || val.indexOf(m) != -1 )
				{
					if ( t1 != null && val.indexOf(t1) != -1 )
					{
						val = val.substring( val.indexOf(t1) + t1.length() );
					}
					if ( t2 != null && val.indexOf(t2) > 0 )
					{
						val = val.substring( 0, val.indexOf(t2) );
					}
					list.add(val);
				}
			}
			info.put( attribs[i], list );
		}
		ctx.close();
		return info;
	}
}
