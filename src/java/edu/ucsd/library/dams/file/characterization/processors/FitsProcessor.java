package edu.ucsd.library.dams.file.characterization.processors;

import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.CAPTURE_DEVICE;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.CHECKSUM_MD5;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.DATE_CREATED;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.DATE_MODIFIED;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.DURATION;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.EXIF_VERSION;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.FILE_NAME;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.FILE_PATH;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.FITS_VERSION;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.FORMAT;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.IMAGE_PRODUCER;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.MIME_TYPE;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.QUALITY;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.SCANNING_SOFTWARE;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.SIZE;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.STATUS;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.VALID;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.VERSION;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.WELL_FORMED;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.dom4j.io.SAXReader;
import org.jaxen.SimpleNamespaceContext;

/**
 * Extract technical metadata with FITS command line tool.
 * @author lsitu@ucsd.edu
**/
public class FitsProcessor extends Processor {

    private static Logger log = Logger.getLogger(FitsProcessor.class);

    public FitsProcessor() {
        this("fits");
    }

    public FitsProcessor(String command) {
        super(command);
    }

    @Override
    public Map<String, Object> extractMetadata(String sourceFile) throws Exception {
        List<String> cmdParams = new ArrayList<>();
        cmdParams.add(command);
        cmdParams.add("-i");
        cmdParams.add(sourceFile);
 
        String rawOutput = exec(cmdParams);

        log.debug("FITS command: " + concat(cmdParams, " ") + "\n" + rawOutput);
        return processData(rawOutput);
    }

    private String getValue(Node node, String path) {
        return valueOf(node, path, "fits", "http://hul.harvard.edu/ois/xml/ns/fits/fits_output");
    }

    private String valueOf(Node node, String path, String nsPrefix, String nsURI) {
        XPath xpath = DocumentHelper.createXPath(path);
        SimpleNamespaceContext ns = new SimpleNamespaceContext();

        ns.addNamespace( nsPrefix, nsURI );  
        xpath.setNamespaceContext(ns);
        return xpath.valueOf(node);
    }

    @Override
    protected String cleanRawData(String rawData) {
        // cleanup for xml parsing 
        return rawData.substring(rawData.indexOf("<?xml ")).trim();
    }

    /*
     * Extract technical metadata from FITS xml output
     * @param rawData
     * @return Map<String, Object>
     * @throws IOException
     * @throws DocumentException
     * @throws ParseException
     */
    private Map<String, Object> processData(String rawData)
            throws IOException, DocumentException, ParseException {
        Map<String, Object> metadata = new HashMap<>();
        SAXReader saxReader = new SAXReader();
        try (InputStream in = new ByteArrayInputStream(rawData.getBytes("utf-8"))) {
            Document doc = saxReader.read(in);

            Node fitsNode = doc.selectSingleNode("/fits");

            // fits version
            metadata.put(FITS_VERSION, getValue(fitsNode, "@version"));

            // exiftool version
            metadata.put(EXIF_VERSION, getValue(fitsNode,
                    "fits:identification/fits:identity/fits:tool[@toolname='Exiftool']/@toolversion"));

            // format
            metadata.put(FORMAT, getValue(fitsNode, "fits:identification/fits:identity/@format"));

            // mimetype: could be a comma-separated string, use the first part
            String[] mimeType = getValue(fitsNode, "fits:identification/fits:identity/@mimetype").split(",");
            metadata.put(MIME_TYPE, mimeType.length > 0 ? mimeType[0].trim() : "");

            // version
            metadata.put(VERSION, getValue(fitsNode, "fits:identification/fits:identity/fits:version"));

            // file size
            metadata.put(SIZE, Long.parseLong(getValue(fitsNode, "fits:fileinfo/fits:size")));

            // date created: some files may not have, use last modified.
            String dateCreated = getValue(fitsNode, "fits:fileinfo/fits:created");
            metadata.put(DATE_CREATED, (StringUtils.isNotBlank(dateCreated) ? parseDate(dateCreated) : null));

            // date modified
            String dateValue = getValue(fitsNode, "fits:fileinfo/fits:lastmodified");
            if (StringUtils.isNotBlank(dateValue)) {
                metadata.put(DATE_MODIFIED, parseDate(getValue(fitsNode, "fits:fileinfo/fits:lastmodified")));
            } else {
                // last modified in OIS File Information (in milliseconds)
                dateValue = getValue(fitsNode, "fits:fileinfo/fits:fslastmodified");
                metadata.put(DATE_MODIFIED, new Date(Long.parseLong(dateValue)));
            }

            // filename
            metadata.put(FILE_NAME, getValue(fitsNode, "fits:fileinfo/fits:filename"));

            // filepath: it's the whole path, need to remove the filename
            String filePath = getValue(fitsNode, "fits:fileinfo/fits:filepath");
            metadata.put(FILE_PATH, filePath.substring(0, filePath.lastIndexOf("/")));

            // original MD5 checksum if extracted
            metadata.put(CHECKSUM_MD5, getValue(fitsNode, "fits:fileinfo/fits:md5checksum"));

            // well-formed
            String wellFormedValue = getValue(fitsNode, "fits:filestatus/fits:well-formed");
            if (StringUtils.isNotBlank(wellFormedValue)) {
                metadata.put(WELL_FORMED, Boolean.parseBoolean(wellFormedValue));
            }

            // valid: file format
            String validValue = getValue(fitsNode, "fits:filestatus/fits:valid");
            if (StringUtils.isNotBlank(validValue)) {
                metadata.put(VALID, Boolean.parseBoolean(validValue));
            }

            // status message
            String statusMessage = getValue(fitsNode, "fits:filestatus/fits:message");
            metadata.put(STATUS, statusMessage);
            if (statusMessage != null && statusMessage.contains("Not well-formed")) {
                metadata.put(WELL_FORMED, false);
                metadata.put(VALID, false);
            }

            // image quality
            String imageWidth = getValue(fitsNode, "fits:metadata/fits:image/fits:imageWidth");
            String imageHeight = getValue(fitsNode, "fits:metadata/fits:image/fits:imageHeight");
            if (StringUtils.isNotBlank(imageWidth) || StringUtils.isNotBlank(imageHeight)) {
                metadata.put(QUALITY, imageWidth + "x" + imageHeight);
            }

            // image producer
            metadata.put(IMAGE_PRODUCER, getValue(fitsNode, "fits:metadata/fits:image/fits:imageProducer"));

            // capture device
            metadata.put(CAPTURE_DEVICE, getValue(fitsNode, "fits:metadata/fits:image/fits:captureDevice"));

            // scanning software
            metadata.put(SCANNING_SOFTWARE, getValue(fitsNode, "fits:metadata/fits:image/fits:scanningSoftwareName"));

            // video/audio duration
            String duration = getValue(fitsNode, "fits:metadata//fits:duration");
            if (StringUtils.isNotBlank(duration)) {
                metadata.put(DURATION, formatDuration(duration));
            }

            if (StringUtils.isBlank((String)metadata.get(QUALITY))) {
                // audio quality
                String bitDepth = getValue(fitsNode, "fits:metadata/fits:audio/fits:bitDepth");
                String sampleRate = getValue(fitsNode, "fits:metadata/fits:audio/fits:sampleRate");
                String channels = getValue(fitsNode, "fits:metadata/fits:audio/fits:channels");
                if (StringUtils.isNotBlank(bitDepth) || StringUtils.isNotBlank(sampleRate) || StringUtils.isNotBlank(channels)) {
                    metadata.put(QUALITY, audioQuality(bitDepth, sampleRate, "Hz", channels)) ;
                }
            }
        }
        return metadata;
    }

    /*
     * Parse the date value that is in different formats
     * @param dateValue
     * @return Date
     */
    private Date parseDate(String dateValue) {
        for (SimpleDateFormat dateFormat : Arrays.asList(dateFormats)) {
            try {
                return dateFormat.parse(dateValue);
            } catch (ParseException e) {
            }
        }
        return null;
    }

    /*
     * Format audio quality
     * @param bits
     * @param freq
     * @param units
     * @param chan
     * @return String
     */
    private static String audioQuality(String bits, String freq, String units, String chan) {
        String qual = "";
        if ( bits != null ) { qual += bits + "-bit"; }
        if ( freq != null ) {
            if ( !qual.equals("") ) { qual += ", "; }
            qual += freq + " " + units;
        }
        if ( chan != null ) {
            if ( !qual.equals("") ) { qual += ", "; }
            if ( chan.equals("1") ) {
                qual += "Single channel (Mono)";
            } else if ( chan.equals("2") ) {
                qual += "Dual channel (Stereo)";
            } else if ( chan.indexOf("channel") == -1 ) {
                qual += chan + " channel";
            } else {
                qual += chan;
            }
        }

        return qual;
    }
}
