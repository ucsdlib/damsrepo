package edu.ucsd.library.dams.triple;

/**
 * Exception class to wrap implementation-specific exceptions.
 * @author escowles
**/
public class TripleStoreException extends Exception
{
	/**
	 * Create an exception with no detail message.
	**/
	public TripleStoreException()
	{
		super();
	}

	/**
	 * Create an exception with a detail message.
	**/
	public TripleStoreException( String message )
	{
		super( message );
	}

	/**
	 * Create an exception with a detail message and cause.
	**/
	public TripleStoreException( String message, Throwable cause )
	{
		super( message, cause );
	}

	/**
	 * Create an exception wrapping another exception.
	**/
	public TripleStoreException( Throwable cause )
	{
		super( cause.getMessage(), cause );
	}
}
