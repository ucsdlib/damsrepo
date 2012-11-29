package edu.ucsd.library.dams.jhove;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

	/**
	 * Methods for extract duration for medias with ffmpeg.
	 *
	 */
	public class FfmpegUtil
	{
	    /**
	     * Executes ffmpeg against the specified file, parses the
	     * results, and returns a map that contains key metrics
	     *
	     * @param filename      
	     * @return         
	     */
	    public static Map<String,String> executeInquiry( String filename, String ffmpegCommand )
	    {
	        Map<String,String> fieldMap = new HashMap<String,String>();

	        try
	        {
	            // Build the command line
	            StringBuilder sb = new StringBuilder();
	            sb.append( ffmpegCommand );
	            sb.append( " -i " );
	            sb.append( filename );

	            // Execute the command
	            System.out.println( "Command line: " + sb );
	            Process p = Runtime.getRuntime().exec( sb.toString() );

	            // Read the response
	            BufferedReader input = new BufferedReader( new InputStreamReader( p.getInputStream() ) );
	            BufferedReader error = new BufferedReader( new InputStreamReader( p.getErrorStream() ) );

	            // Parse the input stream
	            String line = input.readLine();
	            while( line != null )
	            {
	                line = input.readLine();
	            }

	            // Parse the error stream
	            line = error.readLine();
	            while( (line=error.readLine()) != null )
	            {
	            	int idx = line.indexOf( "Duration: " );
	            	if( idx != -1 )
	                {
	                    //Handle Duration line: Duration: 00:42:53.59, bitrate: 1136 kb/s
	            		int idxEnd = line.indexOf(", ", idx);
	            		if(idxEnd <= idx)
	            			idxEnd = line.length();
	                    String duration = line.substring( idx + 10, idxEnd);
	                    if(duration != null && duration.indexOf('.') > 0)
	                    	duration = formatDuration(duration);
	                    fieldMap.put( "duration", duration );

	                    String bitrate = line.substring( line.indexOf( "bitrate: " ) + 9 );
	                    fieldMap.put( "bitrate", bitrate );
	                    break;
	                }
	            }
	        }
	        catch( Exception e )
	        {
	            e.printStackTrace();
	        }
	        return fieldMap;
	    }

	    /**
	     * Returns the duration of the specified file
	     *
	     * @param filename
	     * @return
	     */
	    public static String getDuration( String filename, String ffmpegCommand )
	    {
	        Map<String,String> fieldMap = executeInquiry( filename, ffmpegCommand );
	        if( fieldMap.containsKey( "duration" ) )
	        {
	            return fieldMap.get( "duration" );
	        }
	        return "";
	    }
	    
	    
	    /**
	     * Format the duration in hh:MM:ss format
	     * @param duration
	     * @return
	     */
	    public static String formatDuration(String duration){
	    	String[] parts = duration.split(":");
	    	if(parts.length == 3){
	    		int totalSeconds = (Math.round(Float.parseFloat(parts[2]))) + (Integer.parseInt(parts[1])*60 + Integer.parseInt(parts[0])*60*60);
	    		int hour = totalSeconds/3600;
	    		int minute = totalSeconds%3600/60;
	    		int second = totalSeconds%3600%60;
			    return (hour<10?"0"+hour:hour) + ":" + (minute<10?"0"+minute:minute)  + ":" + (second<10?"0"+second:second);
	    	}else{
	    		return duration;
	    	}
	   }
	}
