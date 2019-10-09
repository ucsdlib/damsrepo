package edu.ucsd.library.dams.unitTest.api;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.junit.Before;
import org.junit.Test;

import edu.ucsd.library.dams.api.FedoraAPIServlet;

public class FedoraAPIServletTest extends UnitTestBasic
{
    private static String roleAdmin = "dams-curator";
    private static String roleLocal = "local";
    private static String roleDefault = "public";
    private static String localCopyright = "UC Regents";

    private String clrId = "";
    private Document clrDoc = null;

    @Before
    public void init() throws DocumentException, IOException {
        SAXReader saxReader = new SAXReader();
        clrDoc = saxReader.read(getResourceFile("clr-suppress-discovery.xml"));
        String arkUrl = clrDoc.valueOf("/rdf:RDF/*/@rdf:about");
        clrId = arkUrl.substring(arkUrl.lastIndexOf("/") + 1 );
    }

    @Test
    public void testCLRSuppressDiscoveryAccessGroupDiscover() throws Exception {
        // CLR with visibility should be hidden for public but searchable by curator.
        boolean discover = true;
        String actualGroup = FedoraAPIServlet.accessGroup(clrDoc, discover, clrId,
                roleAdmin, roleLocal, roleDefault, localCopyright);
        assertEquals("Dicover access group should NOT be searchable!", roleAdmin, actualGroup);
    }

    @Test
    public void testCLRSuppressDiscoveryAccessGroupRead() throws Exception {
        // CLR with visibility suppressDiscovery should be public accessible through collection URL.
        boolean discover = false;
        String actualGroup = FedoraAPIServlet.accessGroup(clrDoc, discover, clrId,
                roleAdmin, roleLocal, roleDefault, localCopyright);
        assertEquals("Read access group should be publc!", roleDefault, actualGroup);
    }
}
