package edu.ucsd.library.dams.file.characterization.processors;

import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.DURATION;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.QUALITY;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Extract technical metadata from media files with ffmpeg
 * 
**/
public class FfmpegProcessor extends Processor {
    private static Logger log = Logger.getLogger(FfmpegProcessor.class);

    public FfmpegProcessor() {

        this("ffmpeg");
    }

    public FfmpegProcessor(String command) {
        super(command);
    }

    @Override
    public Map<String, Object> extractMetadata(String sourceFile) throws Exception {
        List<String> cmdParams = new ArrayList<>();
        cmdParams.add(command);
        cmdParams.add("-i");
        cmdParams.add(sourceFile);

        String rawOutput = exec(cmdParams);

        log.debug("FFMEPG command: " + concat(cmdParams, " ") + ":\n" + rawOutput);
        return processData(rawOutput);
    }

    /* Extract duration and quality metadata
     * @param data
     * @return Map
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    private Map<String, Object> processData(String data) throws UnsupportedEncodingException, IOException {

        Map<String, Object> metadata = new HashMap<>();
        Map<String,String> fieldMap = new HashMap<String,String>();
        String line = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data.getBytes("utf-8"))))) {

            // Parse the error stream
            while( (line=reader.readLine()) != null ) {
                int idx = line.indexOf( "Duration: " );
                if( idx != -1 ) {
                    //Handle duration: Duration: 00:42:53.59, bitrate: 1136 kb/s
                    int idxEnd = line.indexOf(", ", idx);
                    if(idxEnd <= idx) { idxEnd = line.length(); }
                    String duration = line.substring( idx + 10, idxEnd);
                    if(duration != null && duration.indexOf('.') > 0) {
                        duration = formatDuration(duration);
                    }
                    fieldMap.put( DURATION, duration );

                    String bitrate = line.substring(
                        line.indexOf( "bitrate: " ) + 9
                    );
                    fieldMap.put( "bitrate", bitrate );
                } else if ( line.indexOf( "Stream #") != -1 && line.indexOf("Video:") != -1 ) {
                    // video parameters
                    // Stream #0:0(eng): Video: mpeg4 (Simple Profile) (mp4v / 0x7634706D), yuv420p, 640x480 [SAR 1:1 DAR 4:3], 261 kb/s, 10 fps, 10 tbr, 3k tbn, 25 tbc

                    idx = line.indexOf("DAR ");
                    if (idx >= 0) {
                        // extract aspect ratio
                        int endIdx = line.indexOf(']', idx) > 0 ? line.indexOf(']', idx) : line.indexOf(',', idx);
                        if (endIdx > 0) {
                            String dar = line.substring(idx + 4, endIdx).trim();
                            fieldMap.put( "dar", dar );
                        }
                    }

                    idx = line.indexOf("SAR ");
                    if (idx >= 0) {
                        // extract aspect ratio
                        int endIdx = line.indexOf(' ', idx) > 0 ? line.indexOf(' ', idx + 4) : line.indexOf(',', idx);
                        if (endIdx > 0) {
                            String dar = line.substring(idx + 4, endIdx).trim();
                            fieldMap.put( "sar", dar );
                        }
                    }

                    String s = line.replaceAll("\\[.+?\\]","");
                    s = s.replaceAll("\\(\\w+? / \\w+?\\)","");
                    s = s.replaceAll(".*Video:","");
                    String[] tokens = s.split(",");
                    String qual = "";
                    for ( int i = 0; i < tokens.length; i++ ) {
                        if ( i == 0 || tokens[i].trim().matches("\\d+x\\d+")
                                    || tokens[i].indexOf("kb/s") != -1
                                    || tokens[i].indexOf("fps") != -1 ) {
                            if ( qual.length() > 0 ) { qual += ", "; }
                            qual += tokens[i].trim();
                        }

                        // frame size
                        if (tokens[i].trim().matches("\\d+x\\d+"))
                            fieldMap.put( "size", tokens[i].trim() );
                    }

                    fieldMap.put( "video", qual );
                } else if ( line.indexOf( "Stream #") != -1  && line.indexOf("Audio:") != -1 ) {
                    // audio parameters
                    // Stream #0:1(eng): Audio: aac (mp4a / 0x6134706D), 32000 Hz, mono, s16, 43 kb/s
                    String s = line.replaceAll(" \\(\\w+? / \\w+?\\)","");
                    s = s.replaceAll(".*Audio: ","");
                    fieldMap.put( "audio", s );
                }
            }
        }

        metadata.put(DURATION, fieldMap.get(DURATION));
        metadata.put(QUALITY, qualityValue(fieldMap));
        return metadata;
    }

    /* Format quality value for medias
     * @param dataMap
     * @return
     */
    private String qualityValue(Map<String,String> dataMap) {
        String quantity = null;
        String audioFormat = dataMap.get("audio");
        String videoFormat = dataMap.get("video");
        if ( audioFormat != null && videoFormat != null ) {
            quantity = "video: " + videoFormat + "; audio: " + audioFormat;
        } else if ( audioFormat != null ) {
            quantity = audioFormat;
        } else if ( videoFormat != null && videoFormat.startsWith("png, ") ) {
            quantity = videoFormat.substring(5);
        } else if ( videoFormat != null ) {
            quantity = videoFormat;
        }
        return quantity;
    }

    /*
     * Override for the output from ffmpeg
     */
    @Override
    protected String exec(List<String> cmd) throws Exception {
        String rawData = null;
        try {
            rawData = super.exec(cmd);
        } catch (Exception ex) {
            ex.printStackTrace();
            rawData = ex.getMessage();
        }
        return rawData;
    }
}
