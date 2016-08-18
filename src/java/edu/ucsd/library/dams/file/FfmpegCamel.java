package edu.ucsd.library.dams.file;

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

/**
 * Utility class to create derivatives with damsrepo camel.
 * @author lsitu
 * 
**/
public class FfmpegCamel extends CamelEndpoint {
    private String command = null;
    private long timeout = 0;

    public FfmpegCamel( String command, Endpoint endpoint, long timeout ) {
    	super (endpoint);
        this.command = command;
        if ( !(new File(command)).exists() ) {
            throw new IllegalArgumentException(
                "Can't find ffmpeg command: " + this.command
            );
        }
        this.timeout = timeout;
    }

    /**
     * create thumbnail, mp3 and mp4 derivatives
     * @param fs
     * @param oid
     * @param cid
     * @param fid - source file id
     * @param derid - thumbnail files id
     * @param codecParams - codec and other params like scale 150:-1, 450:-1, 768:-1 etc.
     * @param offset - start position in seconds or 00:00:10.xxx format
     * @return
     * @throws Exception 
     */
    public boolean createDerivative (FileStore fs, String oid, String cid, String fid, String derid,
            String codecParams, String offset) throws Exception {

        String sourceFile = fs.getPath(oid, cid, fid);
        String derFile = fs.getPath(oid, cid, derid);

        System.out.println("Command: " + command + "; sourceFile: " + sourceFile + "; destFile: " + derFile);

        // create the exchange used for the communication
        // we use the in out pattern for a synchronized exchange where we expect a response
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        // set the input message
        org.apache.camel.Message message = exchange.getIn();
        message.setHeader(HeaderConstants.CAMEL_JMS_TIMEOUT, timeout);
        message.setHeader(HeaderConstants.COMMAND, command);
        message.setHeader(HeaderConstants.SOURCE_FILE, sourceFile);
        message.setHeader(HeaderConstants.DEST_FILE, derFile);
        message.setHeader(HeaderConstants.PARAMS, codecParams);
        message.setHeader(HeaderConstants.METADATA, "");

        return invoke (exchange);
    }
}
