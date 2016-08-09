package edu.ucsd.library.dams.file;

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

/**
 * Utility class to create derivatives with damsrepo camel.
 * @author lsitu@ucsd.edu
**/
public class ImageMagickCamel extends CamelEndpoint
{
    private String command = null;
    private long timeout = 0;

    public ImageMagickCamel( String command, Endpoint endpoint, long timeout ) {
        super (endpoint);
        if ( !(new File(command)).exists() )
        {
            throw new IllegalArgumentException(
                "Can't find magick convert command: " + command
            );
        }
        this.command = command;
        this.timeout = timeout;
    }
    
    /**
      * create derivative
     * @param fs FileStore to retrieve source image from, and store generated
     *   derivative image in.
     * @param oid Object identifier.
     * @param cid component identifier.
     * @param fid identifier for master image.
     * @param derid identifier for generated derivative.
     * @param width Width, in pixels of derivative image.
     * @param height Height, in pixels of derivative image.
     * @throws Exception 
    **/
    public boolean makeDerivative( FileStore fs, String oid, String cid,
        String fid, String derid, int width, int height)
        throws Exception
    {
        return makeDerivative( fs, oid, cid, fid, derid,  width, height, 0 );
    }

    /**
      * create derivative
     * @param fs FileStore to retrive source image from, and store generated
     *   derivative image in.
     * @param objectID Object identifier.
     * @param masterID File identifier for master image.
     * @param derivID File identifier for generated derivative.
     * @param width Width, in pixels of derivative image.
     * @param height Height, in pixels of derivative image.
     * @throws Exception 
    **/
    public boolean makeDerivative( FileStore fs, String oid, String cid, String fid, String derid,
            int width, int height, int frameNo ) throws Exception {

        String sourceFile = fs.getPath(oid, cid, fid);
        String derFile = fs.getPath(oid, cid, derid);
        String size = width + "x" + height;
        System.out.println("Command: " + command + "; sourceFile: " + sourceFile + "; destFile: " + derFile + "; size: " + size + "; frame no: " + frameNo);

        // create the exchange used for the communication
        // we use the in out pattern for a synchronized exchange where we expect a response
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        // set the input message
        org.apache.camel.Message message = exchange.getIn();
        message.setHeader(HeaderConstants.CAMEL_JMS_TIMEOUT, timeout);
        message.setHeader(HeaderConstants.COMMAND, command);
        message.setHeader(HeaderConstants.SOURCE_FILE, sourceFile);
        message.setHeader(HeaderConstants.DEST_FILE, derFile);
        message.setHeader(HeaderConstants.SZIE, size);
        message.setHeader(HeaderConstants.FRAME, "" + frameNo);

        return invoke (exchange);
    }
}
