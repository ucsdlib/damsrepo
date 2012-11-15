package edu.ucsd.library.dams.file;

/**
 * Exception class to wrap implementation-specific exceptions related to
 * access control, session timeout, etc.
 * @author escowles@ucsd.edu
**/
public class FileStoreAuthException extends FileStoreException
{
    /**
     * Create an exception with no detail message.
    **/
    public FileStoreAuthException()
    {
        super();
    }

    /**
     * Create an exception with a detail message.
    **/
    public FileStoreAuthException( String message )
    {
        super( message );
    }

    /**
     * Create an exception with a detail message and cause.
    **/
    public FileStoreAuthException( String message, Throwable cause )
    {
        super( message, cause );
    }

    /**
     * Create an exception wrapping another exception.
    **/
    public FileStoreAuthException( Throwable cause )
    {
        super( cause.getMessage(), cause );
    }
}
