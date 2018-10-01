package edu.ucsd.library.dams.file.characterization;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import edu.ucsd.library.dams.file.characterization.model.TechnicalMetadata;
import edu.ucsd.library.dams.file.characterization.processors.FfmpegProcessor;
import edu.ucsd.library.dams.file.characterization.processors.Processor;

/**
 * Utility class with API to extract technical metadata.
 * @author lsitu@ucsd.edu
**/
public class CharacterizationService {
    private static Logger log = Logger.getLogger(CharacterizationService.class);

    private static Pattern mediaPattern = Pattern.compile(".*(video|audio|mpeg|png).*");

    /**
     * Extract technical metadata for a file
     * @param filename
     * @return TechnicalMetadata
     * @throws Exception 
    **/
    public static TechnicalMetadata extractMetadata(String sourceFile, List<Processor> processors) throws Exception {
        TechnicalMetadata metadata = new TechnicalMetadata();

        String mimeType = URLConnection.guessContentTypeFromName(sourceFile);
        for (Processor processor : processors) {

            if ((processor instanceof FfmpegProcessor)) {
                Matcher matcher = mediaPattern.matcher(mimeType);
                if (StringUtils.isNotBlank(mimeType) && !matcher.find()) {
                    continue;
                }

                // Skip execute the FfmpegProcessor when duration/quality values already being extracted
                String quality = metadata.getQuality();
                if (StringUtils.isNotBlank(quality) && StringUtils.isNotBlank(metadata.getDuration())
                    || mimeType.contains("png") && StringUtils.isNotBlank(quality)) {
                    continue;
                }

                log.info("FfmpegProcessor for file " + sourceFile + ": " + processor.getCommand());
            }

            Map<String, Object> data = processor.extractMetadata(sourceFile);

            for (String key : data.keySet()) {
                Field field = metadata.getClass().getDeclaredField(key);
                field.setAccessible(true);

                // Assign value extracted
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.set(metadata, data.get(key));
                }
            }

            // Set mimetype for the next process if extracted
            if (StringUtils.isNotBlank(metadata.getMimeType())) {
                mimeType = metadata.getMimeType();
            }
        }

        log.debug("Technical metadata extracted: " + metadata);
        return metadata;
    }
}
