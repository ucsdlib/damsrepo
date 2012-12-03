/**********************************************************************
 * Jhove - JSTOR/Harvard Object Validation Environment
 * Copyright 2005 by the President and Fellows of Harvard College
 **********************************************************************/

package edu.ucsd.library.dams.jhove;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;

import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import edu.harvard.hul.ois.jhove.handler.XmlHandler;

/**
 * The JHOVE engine, providing all base services necessary to build an
 * application.
 * 
 * More than one JhoveBase may be instantiated and process files in
 * concurrent threads.  Any one instance must not be multithreaded.
 */
public class MyJhoveBase extends JhoveBase {
	
	//private MyJhoveBase _jebase = null;
	private static List<MyJhoveBase> myJhoves = new ArrayList<MyJhoveBase>();
	public static boolean shellMode = false;
	private static String jhoveconf = "conf/jhove.conf";
	private static final String jhvname = "ETL-Jhove";
	private static final int [] jhvdate = {2005, 9, 25};
	private static final String jvhrel = "1.0";
	private static final String jvhrights = "Copyright 2004-2005 by JSTOR and " +
	"the President and Fellows of Harvard College. " +
	"Released under the GNU Lesser General Public License.";
	
	private static final String _moduleNames[] = {"PDF-hul","ASCII-hul","GIF-hul","TIFF-hul","WAVE-hul","XML-hul","HTML-hul","BYTESTREAM"};
	private static final String _fileExten[]   = {".pdf",".txt",".gif",".tif",".wav",".xml",".html",".mov"};
	private static HashMap _moduleMap;
	private static String ffmpegCommand = "ffmpeg";
	public static final String MEDIA_FILES = ".wav .mp3 .mov .mp4 .avi";


	private MyJhoveBase() throws Exception {
		super();
		initModuleMap();
	}
	
	public static synchronized MyJhoveBase getMyJhoveBase() throws Exception{
		MyJhoveBase jebase = null;
		if(myJhoves.size() == 0){
			if(jhoveconf == null || !new File(jhoveconf).exists()){
				jhoveconf = System.getProperty("catalina.base") + "/webapps/dams/WEB-INF/classes/jhove.conf";
				if(!new File(jhoveconf).exists())
					throw new Exception("Configuration file was not found: " + jhoveconf);
			}
			String saxClass = MyJhoveBase.getSaxClassFromProperties();
			jebase = new MyJhoveBase();
			jebase.init(MyJhoveBase.jhoveconf, saxClass);
		}else
			jebase = myJhoves.remove(0);
		return jebase;
	}
	
	public static synchronized void returnMyJhoveBase(MyJhoveBase jebase) throws Exception{
		myJhoves.add(jebase);
	}
	//rdias - UCSD: my version of dispatch into order to take control of the 
	//output writer in dispatch
    /** Processes a file or directory, or outputs information.
     *  If <code>dirFileOrUri</code> is null, Does one of the following:
     *  <ul>
     *   <li>If module is non-null, provides information about the module.
     *   <li>Otherwise if <code>aboutHandler</code> is non-null,
     *       provides information about that handler.
     *   <li>If they're both null, provides information about the
     *       application.
     *  </ul>
     *  @param app          The App object for the application
     *  @param module       The module to be used
     *  @param aboutHandler If specified, the handler about which info is requested
     *  @param handler      The handler for processing the output
     *  @param outputFile   Name of the file to which output should go
     *  @param dirFileOrUri One or more file names or URI's to be analyzed
     */
    public void dispatch (App app, Module module, /* String moduleParam, */
				OutputHandler aboutHandler,
				OutputHandler handler, /*String handlerParam,*/
				PrintWriter output,
				String [] dirFileOrUri)
	throws Exception
    {
        super.resetAbort();
    	/* If no handler is specified, use the default TEXT handler. */
    	if (handler == null) {
    	    handler = (OutputHandler) super.getHandlerMap().get ("text");
    	}
    
    	handler.setApp    (app);
    	handler.setBase   (this);
    	handler.setWriter(output);
//    	handler.setWriter (makeWriter (_outputFile, _encoding));
    	//handler.param     (handlerParam);
    
    	handler.showHeader ();                /* Show handler header info. */
    
    	if (dirFileOrUri == null) {
            if (module != null) {             /* Show info about module. */
                //module.param (moduleParam);
                module.applyDefaultParams();
                module.show  (handler);
            }
            else if (aboutHandler != null) {  /* Show info about handler. */
                handler.show  (aboutHandler);
            }
            else {                            /* Show info about application */
                app.show (handler);
            }
    	}
    	else {
            for (int i=0; i<dirFileOrUri.length; i++) {
                if (!process (app, module, /*moduleParam, */ handler, /*handlerParam,*/
    			   dirFileOrUri[i])) {
                        break;
                }
    	    }
    	}
    
    	handler.showFooter ();                /* Show handler footer info. */
    }
    
    public static void removeNS( Element elem )    {
        elem.remove( elem.getNamespace() ); 
        elem.setQName( new QName(elem.getQName().getName(), Namespace.NO_NAMESPACE) );
        // fix children
        List children = elem.elements();
        for ( int i = 0; i < children.size(); i++ )
        {
            Element child = (Element)children.get(i);
            removeNS( child);
        }
    }    
    
    /**
     * Given INP
     * @param kobj
     * @throws DocumentException 
     * @throws ParseException 
     */
    public void parseXml(JhoveInfo kobj, StringWriter swriter) throws DocumentException, ParseException {
    	StringBuffer xmldata = new StringBuffer(swriter.toString());
    	kobj.setMetaxml(xmldata);
       	//Log.console("JHOVE xml:");
    	//Log.console(xmldata.toString());
		//try {
			Document jdoc = DocumentHelper.parseText(xmldata.toString());
		    Element root = jdoc.getRootElement();
		    removeNS(root);
		    String statusstr = jdoc.valueOf("/jhove/repInfo/status"); 
		    //kobj.setStatus(statusstr);
		    if (/*statusstr.indexOf("not valid") != -1 || */statusstr.indexOf("Not well-formed") != -1) {
		    	kobj.setValid(false);
		    }
		    else {
		    	kobj.setValid(true);
		    }
		    kobj.setCheckSum_CRC32(jdoc.valueOf("/jhove/repInfo/checksums/checksum[@type='CRC32']"));
		    kobj.setChecksum_MD5(jdoc.valueOf("/jhove/repInfo/checksums/checksum[@type='MD5']"));
		    kobj.setChecksum_SHA(jdoc.valueOf("/jhove/repInfo/checksums/checksum[@type='SHA-1']"));
		    kobj.setMIMEtype(jdoc.valueOf("/jhove/repInfo/mimeType"));
		    kobj.setSize(Long.parseLong(jdoc.valueOf("/jhove/repInfo/size")));
		    //try {
		    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		    	kobj.setDateModified(sdf.parse(jdoc.valueOf("/jhove/repInfo/lastModified")));
		   
		    String format = jdoc.valueOf("/jhove/repInfo/format"); 
		    kobj.setFormat(format);
		    if (format.equalsIgnoreCase("MP3")) {
		    	String layer = jdoc.valueOf("/jhove/repInfo/properties/property/values/property[name='LayerDescription']/values/value");
		    	String version = jdoc.valueOf("/jhove/repInfo/properties/property/values/property[name='MPEG Audio Version ID']/values/value");
		    	kobj.setVersion(version + ", Layer " + layer);
		    }
		    else {
		    	kobj.setVersion(jdoc.valueOf("/jhove/repInfo/version"));
		    }
		    kobj.setReportingModule(jdoc.valueOf("/jhove/repInfo/reportingModule"));
		    String status = kobj.getStatus();
		    if((status == null || status.length() == 0) || !"BYTESTREAM".equalsIgnoreCase(format))
		    	kobj.setStatus(statusstr);
		    
		    String imageWidth = jdoc.valueOf("//imageWidth");
		    String imageLength = jdoc.valueOf("//imageHeight");
		    if(imageWidth != null && imageLength != null && imageWidth.length() > 0)
		    	kobj.setQuality(imageWidth + "x" + imageLength);

			List resultnodes = jdoc.selectNodes("/jhove/repInfo/messages/message[@severity='error']");
			for (int r = 0; resultnodes != null && r < resultnodes.size(); r++) {
				Object noderesult = resultnodes.get(r);
				if (noderesult instanceof Node) {
					Node nt = (Node)noderesult;
					kobj.setMessage(nt.getStringValue());
				}
			}				
    }

	
	private static synchronized void initModuleMap() throws Exception {
		if(_moduleMap == null){
		if (_moduleNames.length != _fileExten.length) {
			throw new Exception("ModuleNames and fileExten does not match");
			//return false;
		}
		_moduleMap = new HashMap(_moduleNames.length);
		for (int i=0; i<_moduleNames.length; i++) {
			_moduleMap.put(_fileExten[i], _moduleNames[i]);
		}
		//return true;
		}
	}
	
	/**
	 * Note: This code could be initialized only once in the constructor
	 * but based on the comments from Jhove code it is not advisable to do so
	 * 
	 * The JHOVE engine, providing all base services necessary to build an
     * application.   More than one JhoveBase may be instantiated and process files in
     * concurrent threads.  Any one instance must not be multithreaded.
 	 * @return
	 * @throws Exception 
	 * @throws Exception 
	 */
	/*private synchronized void initJhoveEngine() throws Exception {
		if(jhoveconf == null)
			throw new Exception("Configuration file for the Jhove Engine has not set yet.");
		if(_jebase == null){
			String saxClass = MyJhoveBase.getSaxClassFromProperties();
			try {
				_jebase = new MyJhoveBase();
				_jebase.init(MyJhoveBase.jhoveconf, saxClass);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				_jebase = null;
				throw e;
			}
		}
	}*/
	
	public JhoveInfo getJhoveMetaData(String srcFileName) throws Exception {
		File file = new File(srcFileName);
		JhoveInfo dataObj = new JhoveInfo();
		dataObj.setLocalFileName(file.getName());
		dataObj.setFilePath(file.getParent());
		/*if (_jebase == null) {
			initJhoveEngine(); 			
		}*/
		resetAbort ();
		if (!file.canRead()) {
			String emsg = "Can read file for Jhove analysis: " + dataObj.getFilePath() + File.separatorChar + dataObj.getLocalFileName();
			throw new Exception (emsg);										
		}
		JhoveAnalysisProgress jprogress = new JhoveAnalysisProgress();
		jprogress.set_filesize(file.length());
		setCallback(jprogress);		
		String[] paths = new String[1];
		paths[0] = file.getAbsolutePath();
		setShowRawFlag (true);
		setChecksumFlag (true);
//		_jebase.setLogLevel("ALL");
		
		StringWriter swriter = new StringWriter();
		PrintWriter kwriter = new PrintWriter(swriter); 	  
		 App _jeapp = new App (MyJhoveBase.jhvname, MyJhoveBase.jvhrel, 
				 MyJhoveBase.jhvdate, null, MyJhoveBase.jvhrights);   //Moved here to make it thread safe
		 
		 //Module selection
		Module defaultModule = null;
		int indx = srcFileName.lastIndexOf(".");
		if (indx >= 0 && _moduleMap.containsKey(srcFileName.substring(indx))) {
			String selectedModule = (String) _moduleMap.get((String)srcFileName.substring(indx));
			defaultModule = (Module) getModuleMap().get (selectedModule.toLowerCase ());
		}
		
		try {
			dispatch (_jeapp,
					defaultModule,
	                null,   // AboutHandler
	                new XmlHandler(),
	                kwriter,   // output
	                paths);
			parseXml(dataObj, swriter);
			if (!dataObj.getValid()) {
				swriter.close();
				kwriter.close();
				swriter = new StringWriter();
				kwriter = new PrintWriter(swriter);
				Module bytestreamModule = (Module) getModuleMap().get("bytestream");

				dispatch (_jeapp,
						bytestreamModule,
		                null,   // AboutHandler
		                new XmlHandler(),
		                kwriter,   // output
		                paths);
				parseXml(dataObj, swriter);
			}
		}
		catch (Exception e) {
			System.out.println("Jhove analysis error: " + e.toString());
			if(srcFileName.endsWith(".pdf") || srcFileName.endsWith(".PDF")){
				//Accept PDF file.
				swriter.close();
				kwriter.close();
				swriter = new StringWriter();
				kwriter = new PrintWriter(swriter);
				Module bytestreamModule = (Module) getModuleMap().get("bytestream");

				dispatch (_jeapp,
						bytestreamModule,
		                null,   // AboutHandler
		                new XmlHandler(),
		                kwriter,   // output
		                paths);
				parseXml(dataObj, swriter);
			}else
				throw new Exception(e);			
		}finally{
			swriter.close();
			kwriter.close();
		}
		if (!dataObj.getValid())
			throw new Exception("Unable to extract file: " + srcFileName);

		String fileExt = srcFileName.substring(srcFileName.lastIndexOf('.'));
		if(MEDIA_FILES.indexOf(fileExt.toLowerCase()) >= 0){
			String duration = FfmpegUtil.getDuration(srcFileName, ffmpegCommand);
			dataObj.setDuration(duration);
		}
		return dataObj;
	}

	public static synchronized void setJhoveConfig(String jhoveconf){
		MyJhoveBase.jhoveconf = jhoveconf;
		//_jebase = null;
	}
	
	public static synchronized void setFfmpegCommand(String ffmpegCommand){
		MyJhoveBase.ffmpegCommand = ffmpegCommand;
	}
}
