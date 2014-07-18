package edu.ucsd.library.dams.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.file.FileStore;
import edu.ucsd.library.dams.file.FileStoreException;
import edu.ucsd.library.dams.file.FileStoreUtil;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

/**
 * Export all files and metadata for an object.
 * @author escowles@ucsd.edu
**/
public class ObjectExport
{
	public static void main(String[] args) throws Exception
	{
		Properties props = new Properties();
		props.load(new FileInputStream(args[0]));
		String tsName = args[1];
		String esName = args[2];

		// setup TripleStores
		TripleStore ts = TripleStoreUtil.getTripleStore(props, tsName);
		TripleStore es = TripleStoreUtil.getTripleStore(props, esName);

		// setup FileStores
		File baseDir = new File(".");
		Map<String,FileStore> filestores = new HashMap<String,FileStore>();

		// predicates
		Map<String,String> nsmap = TripleStoreUtil.namespaceMap(props);
		String prNS = nsmap.get("dams");
		Property hasFile = null;
		Property filestore = null;

		for (int i = 3; i < args.length; i++)
		{
			String ark = args[i];
			System.out.print(ark + ": ");
			File objDir = new File(baseDir, ark);
			if (!objDir.exists()) { objDir.mkdirs(); }
			try
			{
				DAMSObject obj = new DAMSObject(ts, es, ark, nsmap);
				Model model = obj.asModel(true);

				// export metadata
				FileWriter fw = new FileWriter(new File(objDir, "rdf.xml"));
				obj.outputRDF(model, fw, "RDF/XML-ABBREV");
				System.out.println("RDF/XML");

				// export files
				if (hasFile == null)
				{
					hasFile = model.createProperty(prNS + "hasFile");
					filestore = model.createProperty(prNS + "filestore");
				}
				StmtIterator fit = model.listStatements(
					null, hasFile, (RDFNode)null
				);
				while (fit.hasNext())
				{
					Statement fileStmt = fit.next();
					RDFNode fileNode = fileStmt.getObject();

					// figure out which filestore contains this file
					String filestoreName = "localStore";
					if (model.contains(fileNode.asResource(), filestore))
					{
						Statement filestoreStmt = model.getProperty(
							fileNode.asResource(), filestore
						);
						filestoreName = filestoreStmt.getString();
					}
					FileStore fs = filestores.get(filestoreName);
					if (fs == null)
					{
						// instantiate and cache for later
						fs = FileStoreUtil.getFileStore(props, filestoreName);
						filestores.put(filestoreName, fs);
					}

					// export file
// http: / / library.ucsd.edu / ark: / 20775 / bb01717090 / 1 / 5.jpg
// 0      1  2                  3      4       5            6   7
					String[] parts = fileNode.toString().split("/");
					if (parts.length == 8)
					{
						exportFile(objDir, fs, ark, parts[6], parts[7]);
					}
					else if (parts.length == 7)
					{
						exportFile(objDir, fs, ark, null, parts[6]);
					}
				}
			}
			catch (Exception ex)
			{
				System.out.println("error: " + ex.toString());
				ex.printStackTrace();
			}
		}

		// close the model and database connection
		ts.close();
		es.close();
	}
	private static void exportFile(File objDir, FileStore fs, String objid,
		String cmpid, String fileid) throws FileStoreException, IOException
	{
		String filename = "20775-" + objid + "-";
		filename += (cmpid != null) ? cmpid + "-" + fileid : "0-" + fileid;
		System.out.print("  " + filename + ": ");
		File f = new File(objDir, filename);
		FileOutputStream out = new FileOutputStream(f);
		InputStream in = fs.getInputStream(objid,cmpid,fileid);
		long bytesCopied = FileStoreUtil.copy(in, out);
		System.out.println(bytesCopied);
	}
}
