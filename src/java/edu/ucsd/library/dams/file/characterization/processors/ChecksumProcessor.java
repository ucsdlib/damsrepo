package edu.ucsd.library.dams.file.characterization.processors;

import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.CHECKSUM_CRC32;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.CHECKSUM_MD5;
import static edu.ucsd.library.dams.file.characterization.model.MetadataConstants.CHECKSUM_SHA;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import edu.ucsd.library.dams.file.Checksum;

/**
 * Calculate checksums of a file.
 * @author lsitu@ucsd.edu
**/
public class ChecksumProcessor extends Processor {

    public ChecksumProcessor() {
        super(null);
    }

    @Override
    public Map<String, Object> extractMetadata(String sourceFile) throws Exception {
        return process(sourceFile);
    }

    /*
     * Calculate the checksums with list of digest algorithms by piping it through a DigestInputStream
     * @param sourceFile the path to the file
     * @param algorithms the digest algorithms
     * @return TechnicalMetadata
     * @throws 
     */
    private Map<String, Object> process(String sourceFile)
            throws FileNotFoundException, IOException, NoSuchAlgorithmException {

        Map<String, Object> metadata = new HashMap<>();

        try (InputStream in = new FileInputStream(sourceFile)) {

            boolean crc32Checksum = true;
            boolean md5Checksum = true;
            boolean sha1Checksum = true;
            Map<String, String> checksums = Checksum.checksums(in, null, crc32Checksum, md5Checksum, sha1Checksum, false, false);
            metadata.put(CHECKSUM_CRC32, checksums.get("crc32"));
            metadata.put(CHECKSUM_MD5, checksums.get("md5"));
            metadata.put(CHECKSUM_SHA, checksums.get("sha1"));
            return metadata;
        }
    }
}
