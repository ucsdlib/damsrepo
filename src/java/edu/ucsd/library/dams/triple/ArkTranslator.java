package edu.ucsd.library.dams.triple;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to translate between URIs and ARKs assigned to them.
 * @author escowles@ucsd.edu
**/
public class ArkTranslator
{
	private TripleStore ts = null;
	private String idNS;
	private String owlSameAs;
	private String rdfLabel;
	private Map<String,String> nsmap = null;

	private Map<String,String> uriToArkMap = null;
	private Map<String,String> lblToArkMap = null;
	private Map<String,String> arkToUriMap = null;

	private static Pattern lblPattern = Pattern.compile("<(\\w+:\\w+)>");
	private static Pattern uriPattern = Pattern.compile("<(.+?)>");

	/**
	 * Main constructor.
	 * @param ts TripleStore to load metadata from.
	 * @param id Object identifer (can be full or relative to idNS)
	 * @param nsmap Map from prefixes/names to URIs.
	**/
	public ArkTranslator( TripleStore ts, Map<String,String> nsmap )
	{
		this.ts = ts;
		this.nsmap = nsmap;
		this.idNS = nsmap.get("damsid");
		this.owlSameAs = nsmap.get("owl:sameAs");
		this.rdfLabel = nsmap.get("rdf:label");
	}

	//public Map<String,String> namespaceMap() { return nsmap; }
	//public String getIdentifierNamespace() { return idNS; }
	public Map<String,String> predicateMap() throws TripleStoreException
	{
		loadMap();
		return arkToUriMap;
	}

	private void loadMap() throws TripleStoreException
	{
		if ( uriToArkMap == null && arkToUriMap == null && lblToArkMap == null )
		{
			uriToArkMap = new HashMap<String,String>();
			lblToArkMap = new HashMap<String,String>();
			arkToUriMap = new HashMap<String,String>();
			String sparql = "select ?ark ?pre "
				+ "where { ?ark <" + owlSameAs + "> ?pre }";
			BindingIterator bindings = ts.sparqlSelect(sparql);
			while ( bindings.hasNext() )
			{
				Map<String,String> binding = bindings.nextBinding();
				String ark = binding.get("ark");
				String pre = binding.get("pre");
				arkToUriMap.put( ark, pre );
				uriToArkMap.put( pre, ark );
			}
			bindings.close();

			String lblquery = "select ?ark ?lbl "
				+ "where { ?ark <" + rdfLabel + "> ?lbl }";
			BindingIterator lblBindings = ts.sparqlSelect(lblquery);
			while ( lblBindings.hasNext() )
			{
				Map<String,String> binding = lblBindings.nextBinding();
				String ark = binding.get("ark");
				String lbl = binding.get("lbl");
				try { lbl = lbl.substring(1,lbl.length()-1); }
				catch ( Exception ex ) {}
				lblToArkMap.put( lbl, ark );
			}
			lblBindings.close();
		}
	}
	public Identifier toARK( Identifier uri, boolean strict )
		throws TripleStoreException
	{
		if ( uri == null ) { return null; }
		else if ( uri.isBlankNode() ) { return uri; }
		else { return Identifier.publicURI( toARK(uri.getId(), strict) ); }
	}
	public String toARK( String uri, boolean strict )
		throws TripleStoreException
	{
		loadMap();
		String ark = uriToArkMap.get(uri);

		// if uri not found, look for label
		if ( ark == null ) { ark = lblToArkMap.get(uri); }

		if ( ark != null ) { return ark; }
		else
		{
			if ( strict )
			{
				throw new TripleStoreException("Can't find ARK for " + uri);
			}
			else { return uri; }
		}
	}
	public Identifier toURI( Identifier ark, boolean strict )
		throws TripleStoreException
	{
		if ( ark.isBlankNode() ) { return ark; }
		else { return Identifier.publicURI( toURI(ark.getId(), strict) ); }
	}
	public String toURI( String ark, boolean strict )
		throws TripleStoreException
	{
		loadMap();
		String uri = arkToUriMap.get(ark);
		if ( uri != null ) { return uri; }
		else
		{
			if ( strict )
			{
				throw new TripleStoreException("Can't find URI for " + ark);
			}
			else { return ark; }
		}
	}
	public String translateURIs( String s ) throws TripleStoreException
	{
		loadMap();
		StringBuffer buf = new StringBuffer();
		Matcher m = uriPattern.matcher( s );
		while ( m.find() )
		{
			String uri = s.substring( m.start() + 1, m.end() -1 );
			String ark = toARK(uri,false);
			m.appendReplacement( buf, "<" + ark + ">" );
		}
		m.appendTail( buf );
		return buf.toString();
	}
}
