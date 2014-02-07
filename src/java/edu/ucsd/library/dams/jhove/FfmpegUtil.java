package edu.ucsd.library.dams.jhove;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Methods for extracting technical metadata from media files by parsing ffmpeg
 * command-line output.
 * @author lsitu@ucsd.edu
 * @author escowles@ucsd.edu
**/
public class FfmpegUtil
{
	private static Logger log = Logger.getLogger(FfmpegUtil.class);

	/**
	 * Executes ffmpeg against the specified file, parses the
	 * results, and returns a map that contains key metrics
	 *
	 * @param filename	  
	 * @return		 
	**/
	public static Map<String,String> executeInquiry( String filename,
		String ffmpegCommand )
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
			log.debug( "Command line: " + sb );
			Process p = Runtime.getRuntime().exec( sb.toString() );

			// Read the response
			BufferedReader input = new BufferedReader(
				new InputStreamReader( p.getInputStream() )
			);
			BufferedReader error = new BufferedReader(
				new InputStreamReader( p.getErrorStream() )
			);

			// Parse the input stream
			String line = input.readLine();
			while( line != null )
			{
				line = input.readLine();
			}

			// Parse the error stream
			while( (line=error.readLine()) != null )
			{
				int idx = line.indexOf( "Duration: " );
				if( idx != -1 )
				{
					//Handle duration: Duration: 00:42:53.59, bitrate: 1136 kb/s
					int idxEnd = line.indexOf(", ", idx);
					if(idxEnd <= idx) { idxEnd = line.length(); }
					String duration = line.substring( idx + 10, idxEnd);
					if(duration != null && duration.indexOf('.') > 0)
					{
						duration = formatDuration(duration);
					}
					fieldMap.put( "duration", duration );

					String bitrate = line.substring(
						line.indexOf( "bitrate: " ) + 9
					);
					fieldMap.put( "bitrate", bitrate );
				}
				else if ( line.indexOf( "Stream #") != -1
					&& line.indexOf("Video:") != -1 )
				{
					// video parameters
					// Stream #0:0(eng): Video: mpeg4 (Simple Profile) (mp4v / 0x7634706D), yuv420p, 640x480 [SAR 1:1 DAR 4:3], 261 kb/s, 10 fps, 10 tbr, 3k tbn, 25 tbc
					String s = line.replaceAll("\\[.+?\\]","");
					s = s.replaceAll("\\(\\w+? / \\w+?\\)","");
					s = s.replaceAll(".*Video:","");
					String[] tokens = s.split(",");
					String qual = "";
					for ( int i = 0; i < tokens.length; i++ )
					{
						if ( i == 0 || tokens[i].trim().matches("\\d+x\\d+")
									|| tokens[i].indexOf("kb/s") != -1
									|| tokens[i].indexOf("fps") != -1 )
						{
							if ( qual.length() > 0 ) { qual += ", "; }
							qual += tokens[i].trim();
						}
					}
					fieldMap.put( "video", qual );
				}
				else if ( line.indexOf( "Stream #") != -1
					&& line.indexOf("Audio:") != -1 )
				{
					// audio parameters
					// Stream #0:1(eng): Audio: aac (mp4a / 0x6134706D), 32000 Hz, mono, s16, 43 kb/s
					String s = line.replaceAll(" \\(\\w+? / \\w+?\\)","");
					s = s.replaceAll(".*Audio: ","");
					fieldMap.put( "audio", s );
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
	 * Format the duration in hh:MM:ss format
	 * @param duration
	 * @return
	 */
	public static String formatDuration(String duration)
	{
		String[] parts = duration.split(":");
		if(parts.length == 3)
		{
			int totalSeconds = Math.round(Float.parseFloat(parts[2]))  // secs
				+ (Integer.parseInt(parts[1])*60)                      // mins
				+ (Integer.parseInt(parts[0])*60*60);                  // hrs
			int hour = totalSeconds/3600;
			int minute = totalSeconds%3600/60;
			int second = totalSeconds%3600%60;
			return (hour<10?"0"+hour:hour) + ":" + (minute<10?"0"+minute:minute)
				+ ":" + (second<10?"0"+second:second);
		}
		else
		{
			return duration;
		}
   }
}
