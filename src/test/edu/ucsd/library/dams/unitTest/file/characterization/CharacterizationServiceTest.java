package edu.ucsd.library.dams.unitTest.file.characterization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.ucsd.library.dams.file.characterization.CharacterizationService;
import edu.ucsd.library.dams.file.characterization.model.TechnicalMetadata;
import edu.ucsd.library.dams.file.characterization.processors.ChecksumProcessor;
import edu.ucsd.library.dams.file.characterization.processors.FfmpegProcessor;
import edu.ucsd.library.dams.file.characterization.processors.FitsProcessor;
import edu.ucsd.library.dams.file.characterization.processors.Processor;

/**
 * Test methods for CharacterizationService class
 * @author lsitu
 *
 */
public class CharacterizationServiceTest {

    private static SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ssXXX");

    private static List<Processor> PROCESSORS = null;

    private static String RESOURCE_DIR = null;

    private static String FITS_COMMAND = null;

    private static String FFMPEG_COMMAND = null;

    @BeforeClass
    public static void init() throws IOException {

        // Initiate command for FITS
        FITS_COMMAND = resolveSymbolicLink("/usr/local/bin/fits");

        // Initiate command for FFMPEG
        FFMPEG_COMMAND = resolveSymbolicLink("/usr/local/bin/ffmpeg");

        // Initiate processors
        Processor[] processorArr = {
            new FitsProcessor(FITS_COMMAND),
            new FfmpegProcessor(FFMPEG_COMMAND),
            new ChecksumProcessor() };
        PROCESSORS = Arrays.asList(processorArr);

        // Initiate basic path for test files
        RESOURCE_DIR = System.getProperty("dams.samples");
        if (StringUtils.isBlank(RESOURCE_DIR)) {
            String baseDir = RESOURCE_DIR = new File(CharacterizationServiceTest.class.getClassLoader().getResource("").getPath()).getParent();
            RESOURCE_DIR = baseDir + "/src/sample/";
        }
    }

    private static String resolveSymbolicLink(String command) throws IOException {
        if (Files.isSymbolicLink(Paths.get(command))) {
            Path path = Files.readSymbolicLink(Paths.get(command));
            if (!path.isAbsolute()) {
                path = Paths.get(command).resolveSibling(path);
            }
            return path.toString();
        }
        return command;
    }

    @Test
    public void testExtractTechnicalMetadataForPlainTextFile() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/data.txt").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for text file!", "text/plain", metadata.getMimeType());
        assertEquals("Wrong format for text file!", "Plain text", metadata.getFormat());
        assertEquals("Wrong filename for text file!", "data.txt", metadata.getFileName());
        assertEquals("Wrong size for text file!", 83, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for text file!", "b35231dd9f99414e5000b09c6f77c9bd", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for text file!", "35c9e343", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for text file!", "d34a6e6f7c0fa5ba59b49aa53b828b5059874631", metadata.getChecksumSHA());
        assertEquals("Wrong well-formed value for text file!", true, metadata.getWellFormed());
        assertEquals("Wrong valid value for text file!", true, metadata.getValid());
    }

    @Test
    public void testExtractTechnicalMetadataForExcelXslxDocument() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/damsComplexObject2-1.xslx").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for Excel XSLX document!", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", metadata.getMimeType());
        assertEquals("Wrong format for Excel XSLX document!", "XLSX", metadata.getFormat());
        assertEquals("Wrong filename for Excel XSLX document!", "damsComplexObject2-1.xslx", metadata.getFileName());
        assertEquals("Wrong size for Excel XSLX document!", 34789, metadata.getSize());
        assertEquals("Wrong date created for Excel XSLX document!", "2013:02:19 10:08:45-08:00", dataFormat.format(metadata.getDateCreated()));
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for Excel XSLX document!", "6ba97519ecbb1619e312c1b481f04069", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for Excel XSLX document!", "21412a6f", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for Excel XSLX document!", "105968bd951b35e801944248be38aea7967bddd9", metadata.getChecksumSHA());
    }

    @Test
    public void testExtractTechnicalMetadataForPdfDocument() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/document.pdf").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for PDF document!", "application/pdf", metadata.getMimeType());
        assertEquals("Wrong format for PDF document!", "Portable Document Format", metadata.getFormat());
        assertEquals("Wrong version for PDF document!", "1.3", metadata.getVersion());
        assertEquals("Wrong filename for PDF document!", "document.pdf", metadata.getFileName());
        assertEquals("Wrong size for PDF document!", 34095, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for PDF document!", "4f235d635504caa802a18d5f0f76dfd1", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for PDF document!", "0372e6c5", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for PDF document!", "b077f16ff520044d07704bd07eb96e17de085256", metadata.getChecksumSHA());
        assertEquals("Wrong well-formed value for PDF document!", true, metadata.getWellFormed());
        assertEquals("Wrong valid value for PDF document!", true, metadata.getValid());
    }

    @Test
    public void testExtractTechnicalMetadataForTiffImage() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/image.tif").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for TIFF image!", "image/tiff", metadata.getMimeType());
        assertEquals("Wrong format for TIFF image!", "TIFF EXIF", metadata.getFormat());
        assertEquals("Wrong filename for TIFF image!", "image.tif", metadata.getFileName());
        assertEquals("Wrong size for TIFF image!", 94356, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for TIFF image!", "eaf4bc7bc0bc4e576c75a7186a853aa2", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for TIFF image!", "9aef9696", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for TIFF image!", "f5fabb91b36dc30c936c82dd1a4e86a65c83f833", metadata.getChecksumSHA());
        assertEquals("Wrong quality for TIFF image!", "1274x1466", metadata.getQuality());
    }

    @Test
    public void testExtractTechnicalMetadataForJpegImageFile() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/image.jpg").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for JEPG image!", "image/jpeg", metadata.getMimeType());
        assertEquals("Wrong format for JEPG image!", "JPEG File Interchange Format", metadata.getFormat());
        assertEquals("Wrong filename for JEPG image!", "image.jpg", metadata.getFileName());
        assertEquals("Wrong size for JEPG image!", 25627, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for JEPG image!", "302fcca8b3aaa6499035db8825a527f0", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for JEPG image!", "590970ad", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for JEPG image!", "c9751e26be5f620fab0c37fd85356bd88c4124c6", metadata.getChecksumSHA());
        assertEquals("Wrong quality for JEPG image!", "667x768", metadata.getQuality());
        assertEquals("Wrong well-formed value for JEPG image!", true, metadata.getWellFormed());
        assertEquals("Wrong valid value for JEPG image!", true, metadata.getValid());
    }

    @Test
    public void testExtractTechnicalMetadataForPngImageFile() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/damsComplexObject2-1.png").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for PNG image!", "image/png", metadata.getMimeType());
        assertEquals("Wrong format for PNG image!", "Portable Network Graphics", metadata.getFormat());
        assertEquals("Wrong filename for PNG image!", "damsComplexObject2-1.png", metadata.getFileName());
        assertEquals("Wrong size for PNG image!", 125234, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for PNG image!", "3dac9bdce1d6a0672c6db91d2bc2611d", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for PNG image!", "e4a907ac", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for PNG image!", "bd20db2c215b24258b5e826909c4778dd4dba8c5", metadata.getChecksumSHA());
        assertEquals("Wrong quality for PNG image!", "1247x958", metadata.getQuality());
    }

    @Test
    public void testExtractTechnicalMetadataForMovVideo() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/video.mov").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for MOV video!", "video/quicktime", metadata.getMimeType());
        assertEquals("Wrong format for MOV video!", "Quicktime", metadata.getFormat());
        assertEquals("Wrong filename for MOV video!", "video.mov", metadata.getFileName());
        assertEquals("Wrong size for MOV video!", 976666, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for MOV video!", "5c07e8eb9e7348f6c99d72b0e0fef0e7", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for MOV video!", "9a10b60b", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for MOV video!", "a7231423f4d6a597f65b11f166e98d34b4cadc6c", metadata.getChecksumSHA());
        assertEquals("Wrong quality for MOV video!", "video: h264 (High), 720x480, 1423 kb/s, 29.97 fps; audio: aac (LC), 48000 Hz, stereo, fltp, 127 kb/s (default)", metadata.getQuality());
        assertEquals("Wrong duration for MOV video!", "00:00:05", metadata.getDuration());
    }

    @Test
    public void testExtractTechnicalMetadataForMp4Video() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/video.mp4").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for MP4 video!", "video/mp4", metadata.getMimeType());
        assertEquals("Wrong format for MP4 video!", "ISO Media, MPEG v4 system, version 1", metadata.getFormat());
        assertEquals("Wrong filename for MP4 video!", "video.mp4", metadata.getFileName());
        assertEquals("Wrong size for MP4 video!", 787326, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for MP4 video!", "9693740310f4ce323c1c0c7cf6c84e92", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for MP4 video!", "c17acfb3", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for MP4 video!", "36622c9cf11f2acb0637d103fb3a5d74cca56f68", metadata.getChecksumSHA());
        assertEquals("Wrong quality for MP4 video!", "video: h264 (High), 720x480, 1114 kb/s, 29.97 fps; audio: aac (LC), 48000 Hz, stereo, fltp, 127 kb/s (default)", metadata.getQuality());
        assertEquals("Wrong duration for MP4 video!", "00:00:05", metadata.getDuration());
    }

    @Test
    public void testExtractTechnicalMetadataForWavAudio() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/audio.wav").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for WAV audio!", "audio/x-wave", metadata.getMimeType());
        assertEquals("Wrong format for WAV audio!", "Waveform Audio", metadata.getFormat());
        assertEquals("Wrong filename for WAV audio!", "audio.wav", metadata.getFileName());
        assertEquals("Wrong size for WAV audio!", 156942, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for WAV audio!", "00e6122bf9cda9a30acffb5a562944ad", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for WAV audio!", "5a2c2c86", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for WAV audio!", "4a6985fc0ce3721aad50dbe19b39e644feeae048", metadata.getChecksumSHA());
        assertEquals("Wrong quality for WAV audio!", "16-bit, 44100 Hz, Single channel (Mono)", metadata.getQuality());
        assertEquals("Wrong duration for WAV audio!", "00:00:02", metadata.getDuration());
        assertEquals("Wrong well-formed value for WAV audio!", true, metadata.getWellFormed());
        assertEquals("Wrong valid value for WAV audio!", true, metadata.getValid());
    }

    @Test
    public void testExtractTechnicalMetadataForMp3Audio() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/audio.mp3").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for MP3 audio!", "audio/mpeg", metadata.getMimeType());
        assertEquals("Wrong format for MP3 audio!", "MPEG 1/2 Audio Layer 3", metadata.getFormat());
        assertEquals("Wrong filename for MP3 audio!", "audio.mp3", metadata.getFileName());
        assertEquals("Wrong size for MP3 audio!", 58136, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for MP3 audio!", "d177e8aae39a3887408b429f0e1ef4cd", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for MP3 audio!", "5d0eca93", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for MP3 audio!", "66f09a157208fa71abe05475f62dfc57fa7b832e", metadata.getChecksumSHA());
        assertEquals("Wrong quality for MP3 audio!", "44100 Hz, Single channel (Mono)", metadata.getQuality());
        assertEquals("Wrong duration for MP3 audio!", "00:00:02", metadata.getDuration());
    }

    @Test
    public void testExtractTechnicalMetadataForGzipFile() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/data.tar.gz").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for GZIP file!", "application/x-gzip", metadata.getMimeType());
        assertEquals("Wrong format for GZIP file!", "GZIP Format", metadata.getFormat());
        assertEquals("Wrong filename for GZIP file!", "data.tar.gz", metadata.getFileName());
        assertEquals("Wrong size for GZIP file!", 49848, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for GZIP file!", "8368675044299958e86f8d91389e57c0", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for GZIP file!", "634fbe48", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for GZIP file!", "d695e5602c7edb9f78387b9762e7cbfc12aae06e", metadata.getChecksumSHA());
   }

    @Test
    public void testExtractTechnicalMetadataForZipFile() throws Exception {
        String testFile = new File(RESOURCE_DIR, "files/data.zip").getAbsolutePath();
        TechnicalMetadata metadata = CharacterizationService.extractMetadata(testFile, PROCESSORS);

        assertEquals("Wrong mimetype for ZIP file!", "application/zip", metadata.getMimeType());
        assertEquals("Wrong format for ZIP file!", "ZIP Format", metadata.getFormat());
        assertEquals("Wrong filename for ZIP file!", "data.zip", metadata.getFileName());
        assertEquals("Wrong size for ZIP file!", 7793, metadata.getSize());
        assertNotNull("Date modified doesn't exists for text file!", metadata.getDateModified());
        assertEquals("Wrong MD5 checksum for ZIP file!", "dc9fb3fac2ba2ef227228fb6597a0110", metadata.getChecksumMD5());
        assertEquals("Wrong CRC32 checksum for ZIP file!", "cb469b72", metadata.getChecksumCRC32());
        assertEquals("Wrong SHA-1 checksum for ZIP file!", "980d4c663b87bf95f5372e94cc845d524abf275d", metadata.getChecksumSHA());
   }
}
