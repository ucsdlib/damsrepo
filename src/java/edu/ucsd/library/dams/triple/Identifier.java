package edu.ucsd.library.dams.triple;

/**
 * Public URI or blank node.
 * @author escowles
**/
public class Identifier
{
	private String id;
	private boolean blank;
	private Object ref;

	public static Identifier blankNode( String id )
	{
		return new Identifier( id, null, true );
	}
	public static Identifier blankNode( String id, Object ref )
	{
		return new Identifier( id, ref, true );
	}
	public static Identifier publicURI( String id )
	{
		if ( id == null || id.equals("") ) { return null; }
		return new Identifier( id, null, false );
	}
	public static Identifier publicURI( String id, Object ref )
	{
		return new Identifier( id, ref, false );
	}

	/**
	 * Constructor specifying the blank node id and an implementation-specific
	 * blank node object.
	**/
	public Identifier( String id, Object ref, boolean blank )
	{
		this.id = id;
		if ( this.id.startsWith("_:") ) { this.id = id.replaceAll("_:",""); }
		this.ref = ref;
		this.blank = blank;
	}

	public boolean isBlankNode()
	{
		return blank;
	}
	public void setBlankNode( boolean blank )
	{
		this.blank = blank;
	}

	/**
	 * Get the blank node id.
	**/
	public String getId()
	{
		return id;
	}

	/**
	 * Set the blank node id.
	 * @param id The new blank node id.
	**/
	public void setId( String id )
	{
		this.id = id;
	}

	/**
	 * Get the blank node reference.
	**/
	public Object getRef()
	{
		return ref;
	}

	/**
	 * Set the blank node reference.
	 * @param ref The new blank node reference.
	**/
	public void setRef( Object ref )
	{
		this.ref = ref;
	}

	public String toString()
	{
		if ( isBlankNode() ) { return "_:" + id; }
		else { return "<" + id + ">"; }
	}
	public boolean equals( Identifier otherId )
	{
		if ( this.isBlankNode() != otherId.isBlankNode() )
		{
			return false;
		}
		else
		{
			return this.toString().equals( otherId.toString() );
		}
	}
	public int hashCode()
	{
		return toString().hashCode();
	}
	public String debug()
	{
		String s = "id: '" + id + "', ";
		s += "blank: " + blank + ", ";
		s += "ref: '" + ref + "'";
		return s;
	}
}
