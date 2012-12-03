package edu.ucsd.library.dams.jhove;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

//KB DTO

public class JhoveInfo implements Serializable {
	String entityID = "";

	String localFileName = "";
	
	String filePath = "";

	String arkID = "";

	String filePrefix = "";

	// Jhove attributes below
	long size = 0L;

	Date dateCreated = null;

	Date dateModified = null;

	String reportingModule = "";

	String format = "";

	String version = "";

	String status = "";
	
	String duration = "";
	
	String quality = "";

	boolean valid = true;
	ArrayList message = null;

	String MIMEtype = "";
	String checkSum_CRC32 = "";
	String checksum_MD5 = "";
	String checksum_SHA = "";
	//SRB Server checksum value
	String checksum_SRB = "";
	StringBuffer metaxml = null;
	TreeMap additionalMetaData = null;
	
	int rowNumber = -1;     //Row number on KB display window

	public JhoveInfo() {
		super();
	}

	public String toString() {
		String kobject = entityID + ":" + arkID + ":" + localFileName + ":"
				+ MIMEtype + ":" + format;
		return kobject;
	}

	public JhoveInfo(String arkid, String entityid, String prefix,
			String name) {
		super();
		arkID = arkid;
		entityID = entityid;
		filePrefix = prefix;
		localFileName = name;
	}

	public String getArkID() {
		return arkID;
	}

	public void setArkID(String arkID) {
		this.arkID = arkID;
	}

	public String getEntityID() {
		return entityID;
	}

	public void setEntityID(String entityID) {
		this.entityID = entityID;
	}

	public String getFilePrefix() {
		return filePrefix;
	}

	public void setFilePrefix(String filePrefix) {
		this.filePrefix = filePrefix;
	}

	public String getLocalFileName() {
		return localFileName;
	}

	public void setLocalFileName(String localFileName) {
		this.localFileName = localFileName;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public Date getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Date getDateModified() {
		return dateModified;
	}

	public void setDateModified(Date dateModified) {
		this.dateModified = dateModified;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public ArrayList getMessages() {
		return message;
	}

	public void setMessage(String _message) {
		if (_message != null && _message.length() > 0) {
			if (this.message == null) {
				this.message = new ArrayList();
			}
			this.message.add(_message);
		}
	}

	public String getMIMEtype() {
		return MIMEtype;
	}

	public void setMIMEtype(String etype) {
		MIMEtype = etype;
	}
	
	
	public String getCheckSum_CRC32() {
		return checkSum_CRC32;
	}

	public void setCheckSum_CRC32(String checkSum_CRC32) {
		this.checkSum_CRC32 = checkSum_CRC32;
	}

	public String getChecksum_MD5() {
		return checksum_MD5;
	}

	public void setChecksum_MD5(String checksum_MD5) {
		this.checksum_MD5 = checksum_MD5;
	}

	public String getChecksum_SHA() {
		return checksum_SHA;
	}

	public void setChecksum_SHA(String checksum_SHA) {
		this.checksum_SHA = checksum_SHA;
	}

	public StringBuffer getMetaxml() {
		return metaxml;
	}

	public void setMetaxml(StringBuffer metaxml) {
		this.metaxml = metaxml;
	}

	public String getReportingModule() {
		return reportingModule;
	}

	public void setReportingModule(String reportingModule) {
		this.reportingModule = reportingModule;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean getValid() {
		return valid;
	}

	public void setValid(boolean valid) {
		this.valid = valid;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public int getRowNumber() {
		return rowNumber;
	}

	public void setRowNumber(int rowNumber) {
		this.rowNumber = rowNumber;
	}
	
	public TreeMap getAdditionalMetaData() {
		return additionalMetaData;
	}

	public void setAdditionalMetaData(TreeMap additionalMetaData) {
		this.additionalMetaData = additionalMetaData;
	}

	public void showall(PrintStream out) {
		// Jhove attributes below
		out.println("SIZE: " + this.size);
		out.println("MIMEtype: " + this.MIMEtype);
	}

	public String getChecksum_SRB() {
		return checksum_SRB;
	}

	public void setChecksum_SRB(String checksum_SRB) {
		this.checksum_SRB = checksum_SRB;
	}

	public String getDuration() {
		return duration;
	}

	public void setDuration(String duration) {
		this.duration = duration;
	}

	public String getQuality() {
		return quality;
	}

	public void setQuality(String quality) {
		this.quality = quality;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	
}
