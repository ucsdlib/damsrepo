package edu.ucsd.library.dams.file.characterization.processors;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * Interface to run commands to extract technical metadata.
 * @author lsitu
 * 
**/
public abstract class Processor {
    protected SimpleDateFormat[] dateFormats = { new SimpleDateFormat("yyyy:MM:dd HH:mm:ssXXX"),
            new SimpleDateFormat("yyyy:MM:dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss") };

    protected String command = null;

    public Processor( String command)
    {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Extract technical metadata from source file
     * @param sourceFile
     * @throws Exception 
     */
    public abstract Map<String, Object> extractMetadata(String sourceFile) throws Exception;

    /**
     * Execute command
     * @param cmd
     * @return String
     * @throws Exception
     */
    protected String exec(List<String> cmd) throws Exception {
        Reader reader = null;
        InputStream in = null;
        BufferedReader buf = null;
        StringBuffer log = null;
        Process proc = null;
        try {
            log = new StringBuffer();
            // Execute the command
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            proc = pb.start();

            in = proc.getInputStream();
            reader = new InputStreamReader(in);
            buf = new BufferedReader(reader);
            for ( String line = null; (line=buf.readLine()) != null; ) {
                log.append( line + "\n" );
            }

            // Wait for the process to exit
            int status = proc.waitFor();
            if ( status == 0 ) {
                return cleanRawData(log.toString());
            } else {
                // Output error messages
                in = proc.getErrorStream();
                reader = new InputStreamReader(in);
                buf = new BufferedReader(reader);
                for ( String line = null; (line=buf.readLine()) != null; ) {
                    log.append( line + "\n" );
                }
                throw new Exception( log.toString() );
            }

        } catch ( Exception ex ) {
            throw new Exception( log.toString(), ex );
        } finally {
            close(in);
            close(reader);
            close(buf);
            if(proc != null){
                proc.destroy();
                proc = null;
            }
        }
    }

    /**
     * Cleanup raw data
     * @param rawData
     * @return
     */
    protected String cleanRawData(String rawData) {
        return rawData.trim();
    }

    public void close ( Closeable closeable ) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Format the duration in hh:MM:ss format
     * @param duration
     * @return String
     */
    public static String formatDuration(String duration) {
        String[] parts = duration.split(":");
        if(parts.length == 3 || parts.length == 4) {
            // format: 0:2:11.123
            String secPart = parts[2];
            if (parts.length == 4) {
                secPart += "." + parts[3];
            }

            int totalSeconds = Math.round(Float.parseFloat(secPart))       // secs
                    + (Integer.parseInt(parts[1])*60)                      // mins
                    + (Integer.parseInt(parts[0])*60*60);                  // hrs
            return formatDuration(totalSeconds);
        } else if (duration.indexOf("s") > 0 && duration.indexOf(" ") > 0) {
            // format: 1.73 s
            try {
                int totalSeconds = Math.round(Float.parseFloat(duration.substring(0, duration.indexOf("s")).trim()));
                return formatDuration(totalSeconds);
            } catch (NumberFormatException ex) { }
        }

        return duration;
    }

    private static String formatDuration(int totalSeconds) {
        int hour = totalSeconds/3600;
        int minute = totalSeconds%3600/60;
        int second = totalSeconds%3600%60;
        return (hour<10?"0"+hour:hour) + ":" + (minute<10?"0"+minute:minute)
            + ":" + (second<10?"0"+second:second);
    }

    /**
     * Concatenate list items to string delimited by delimiter
     * @param params
     * @param delimiter
     * @return String
     */
    public String concat(List<String> params, String delimiter) {
        String result = "";
        for (String param : params) {
            result += param + delimiter;
        }

        if (result.length() > 0) {
            result = result.substring(0, result.length() - delimiter.length());
        }
        return result;
    }

}
