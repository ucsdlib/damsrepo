package edu.ucsd.library.dams.model;

import java.util.List;
import edu.ucsd.library.dams.triple.TripleStore;

public class DAMSObject
{
	public DAMSObject( TripleStore ts, String tsName, String id )
	{
		// XXX
	}
	public List<String> getPDFIndexFiles()
	{
		// XXX: rename to listFiles() (list all master files, let indexer figure out which ones it can use)
		return null;
	}
	public String getRDF(boolean recurse, boolean translated)
	{
		// XXX
		return null;
	}
	public String getSolrCollectionData()
	{
		// XXX
		return null;
	}
	public String getSolrJsonData()
	{
		// XXX
		return null;
	}
	public String getSolrNamespaceMap()
	{
		// XXX
		return null;
	}
	public void setTsTagTriplestore( TripleStore ts, String tsName )
	{
		// XXX: are we using this at all?
	}
}
