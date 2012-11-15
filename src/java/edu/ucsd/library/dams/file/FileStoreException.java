package edu.ucsd.library.dams.file;

/**
 * Exception class to wrap implementation-specific exceptions.
 * @author escowles@ucsd.edu
**/
public class FileStoreException extends Exception
{
	private int errorCode = -1;

	/**
	 * Create an exception with no detail message.
	**/
	public FileStoreException()
	{
		super();
	}

	/**
	 * Create an exception with a detail message.
	**/
	public FileStoreException( String message )
	{
		super( message );
	}

	/**
	 * Create an exception with a detail message and cause.
	**/
	public FileStoreException( String message, Throwable cause )
	{
		super( message, cause );
	}

	/**
	 * Create an exception wrapping another exception.
	**/
	public FileStoreException( Throwable cause )
	{
		super( cause.getMessage(), cause );
	}

	/**
	 * Create an exception with an error code and detail message.
	**/
	public FileStoreException( int errorCode, String message )
	{
		super( message );
		this.errorCode = errorCode;
	}

	/**
	 * Return error code.
	**/
	public int errorCode() { return errorCode; }
}
