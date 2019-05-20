package edu.ucsd.library.dams.unitTest.file;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.ucsd.library.dams.file.FileStoreUtil;

/**
 * Test methods for FileStoreUtils class
 * @author lsitu
 *
 */
public class FileStoreUtilTest {

    @Test
    public void testPairPath() throws Exception {
        assertEquals("Wrong pair path!", "bb/12/34/56/78/", FileStoreUtil.pairPath("bb12345678"));
        assertEquals("Wrong pair path!", "bb/12/34/56/7/", FileStoreUtil.pairPath("bb1234567"));
        assertEquals("Wrong pair path!", "b/", FileStoreUtil.pairPath("b"));
    }
}
