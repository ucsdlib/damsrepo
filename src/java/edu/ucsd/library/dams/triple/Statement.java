package edu.ucsd.library.dams.triple;

/**
 * Basic statement/triple object.
 * @author escowles@ucsd.edu
**/
public class Statement
{
	Identifier subject;
	Identifier predicate;
	Identifier object;
	String literal;
	long id = -1L;
	boolean literalObject;

	public Statement()
	{
		subject = null;
		predicate = null;
		object = null;
		literalObject = false;
	}
	public Statement( Identifier subject, Identifier predicate,
		Identifier object )
	{
		this.subject       = subject;
		this.predicate     = predicate;
		this.object        = object;
		this.literalObject = false;
	}
	public Statement( Identifier subject, Identifier predicate,
		String literal )
	{
		this.subject       = subject;
		this.predicate     = predicate;
		this.literal       = literal;
		this.literalObject = true;
	}
	public Statement( String subject, String predicate, String object,
		boolean literalObject )
	{
		this.subject       = Identifier.publicURI(subject);
		this.predicate     = Identifier.publicURI(predicate);
		this.literalObject = literalObject;
		if ( literalObject )
		{
			this.literal   = object;
		}
		else
		{
			this.object    = Identifier.publicURI(object);;
		}
	}

	public Identifier getSubject()    { return subject;       }
	public Identifier getPredicate()  { return predicate;     }
	public Identifier getObject()     { return object;        }
	public String getLiteral()        { return literal;       }
	public long getId()               { return id;            }
	public boolean hasLiteralObject() { return literalObject; }

	public void setSubject( Identifier id )   { subject       = id; }
	public void setPredicate( Identifier id ) { predicate     = id; }
	public void setObject( Identifier id )    { object        = id; }
	public void setLiteral( String s )        { literal       = s;  }
	public void setId( long l )               { id            = l;  }
	public void setLiteralObject( boolean b ) { literalObject = b;  }

	public String toString()
	{
		StringBuffer s = new StringBuffer();
		s.append( subject.toString() + " " + predicate.toString() + " " );
		if ( literalObject )
		{
			// do not double-quote literals if they are already quoted
			if ( literal != null && literal.startsWith("\"") && literal.endsWith("\"") )
			{
				s.append( escapeLiteral(literal) );
			}
			else
			{
				s.append( "\"" + escapeLiteral(literal) + "\"" );
			}
		}
		else
		{
			s.append( object.toString() );
		}
		s.append(" .");
		return s.toString();
	}
	public boolean equals( Object o )
	{
		if ( o instanceof Statement )
		{
			Statement stmt = (Statement)o;
			if ( literalObject && stmt.literalObject )
			{
				return ( stmt.subject.equals( subject )
					  && stmt.predicate.equals( predicate )
					  && stmt.literal.equals( literal ) );
			}
			else if ( ! literalObject && !stmt.literalObject )
			{
				return ( stmt.subject.equals( subject )
					  && stmt.predicate.equals( predicate )
					  && stmt.object.equals( object ) );
			}
			else
			{
				return false;
			}
		}
		else
		{
			return false;
		}
	}
	public int hashCode() { return toString().hashCode(); }
	private static String escapeLiteral( String s )
	{
		if ( s == null ) { return null; }
		String lit = s;

		// escape backslashes (do first so it doesn't munge everything else)
		lit = lit.replaceAll("\\\\", "\\\\\\\\"); // \ -> \\

		// escape quotes and non-printing characters
		lit = lit.replaceAll("\t", "\\t");        // ^I -> \t
		lit = lit.replaceAll("\n", "\\n");        // ^R -> \n
		lit = lit.replaceAll("\r", "\\r");        // ^M -> \r
		lit = lit.replaceAll("\"", "\\\\\"");     // " -> \"
		return lit;
	}
}
