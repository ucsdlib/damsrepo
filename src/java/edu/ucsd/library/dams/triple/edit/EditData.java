package edu.ucsd.library.dams.triple.edit;

import java.io.Serializable;

import org.json.simple.JSONArray;

/**
 * Bean for storing updates in JSON format.
 * @author mcritchlow@ucsd.edu
**/
public class EditData implements Serializable
{
	private JSONArray adds = null;
	private JSONArray deletes = null;
	private JSONArray updates = null;
	private String ark = null;
	private String tsName = null;
	
	public JSONArray getAdds()
	{
		return adds;
	}

	public void setAdds(JSONArray adds)
	{
		this.adds = adds;
	}

	public JSONArray getDeletes()
	{
		return deletes;
	}

	public void setDeletes(JSONArray deletes)
	{
		this.deletes = deletes;
	}

	public JSONArray getUpdates()
	{
		return updates;
	}

	public void setUpdates(JSONArray updates)
	{
		this.updates = updates;
	}

	public String getArk()
	{
		return ark;
	}

	public void setArk(String ark)
	{
		this.ark = ark;
	}

	public String getTsName()
	{
		return tsName;
	}

	public void setTsName( String tsName )
	{
		this.tsName = tsName;
	}

	public EditData( JSONArray adds, JSONArray updates, JSONArray deletes,
		String ark, String tsName )
	{
		this.adds = adds;
		this.updates = updates;
		this.deletes = deletes;
		this.ark = ark;
		this.tsName = tsName;
	}
}
