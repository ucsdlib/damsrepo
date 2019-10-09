package edu.ucsd.library.dams.unitTest.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for Unit tests
 * @author lsitu
 *
 */
public abstract class UnitTestBasic {
    protected File getResourceFile(String fileName) throws IOException {
        File resourceFile = new File(fileName);
        resourceFile.deleteOnExit();

        byte[] buf = new byte[4096];
        try(InputStream in = getClass().getResourceAsStream("/resources/" + fileName);
                FileOutputStream out = new FileOutputStream(resourceFile)) {

            int bytesRead = 0;
            while ((bytesRead = in.read(buf)) > 0) {
                out.write(buf, 0, bytesRead);
            }
        }
        return resourceFile;
    }
}
