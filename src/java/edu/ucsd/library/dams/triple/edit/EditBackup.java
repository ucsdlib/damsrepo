package edu.ucsd.library.dams.triple.edit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.json.simple.JSONArray;

import edu.ucsd.library.dams.triple.TripleStoreException;

/**
 * JSON serialization code for queuing updates to disk.
 * @author mcritchlow
**/
public class EditBackup implements Serializable
{
	private EditData data = null;
	private String fileName = null;
	private static final String BACKUP_PATH = "java:comp/env/dams/backupPath";
	
	public EditBackup(){}
	public EditBackup( JSONArray adds, JSONArray updates, JSONArray deletes,
		String ark, String tsName, String ns )
	{
		data = new EditData( adds, updates, deletes, ark, tsName, ns );
	}

	/** SECTION TO SAVE NEW BACKUPS **/
	/**
	 * Main function for storing current json data to a serialized object which 
	 * can later be retrieved for processing in the triplestore if an error occurs.
	 */
	public boolean saveDataToFile()
	{
		try
		{
			Context initCtx = new InitialContext();
			String backupDir = (String)initCtx.lookup(BACKUP_PATH);
			fileName = backupDir+"/"+data.getArk()+"_"+String.valueOf(System.currentTimeMillis());
			OutputStream outputFile = new FileOutputStream( fileName );
			ObjectOutputStream cout = new ObjectOutputStream( outputFile );
			cout.writeObject(data);
			cout.close();
		}
		catch (NamingException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}


	/** SECTION TO PROCESS EXISTING BACKUPS **/
	/**
	 * main function for processing backup files. This loops through the entire backup directory looking to 
	 * process objects which have not yet been processed and removed.
	 */
	public boolean processBackupFiles() throws TripleStoreException
	{
		//loop through each file in the backup directory
		String backupDir = "";
		try
		{
			Context initCtx = new InitialContext();
			backupDir = (String)initCtx.lookup(BACKUP_PATH);
		}
		catch (NamingException e)
		{
			e.printStackTrace();
			return false;
		}
		
		File dir = new File(backupDir);
	    
	    String[] children = dir.list();
	    if (children == null && children.length == 0)
		{
	        // nothing to process	
	    }
		else
		{
	        for (int i=0; i<children.length; i++)
			{
	        	if(!processBackupFile(children[i]))
				{
	        		return false;
	        	}
	        }
	    }
	    return true;
	}
	/**
	 * Process an individual file based on it's filename
	 * @param filename
	 * @return True if the process succeeds, false if it fails or encounters an
	 *   error.
	 */
	public boolean processBackupFile(String filename)
		throws TripleStoreException
	{
		boolean processed = true;
		FileInputStream fis = null;
        ObjectInputStream in = null;
        String backupDir = "";
		try
		{
			Context initCtx = new InitialContext();
			backupDir = (String)initCtx.lookup(BACKUP_PATH);
		}
		catch (NamingException e)
		{
			e.printStackTrace();
			return false;
		}
        
        fileName = backupDir+filename;
		try
		{
			fis = new FileInputStream(fileName);
			in = new ObjectInputStream(fis);
			data = (EditData)in.readObject();
			in.close();
			if(processed)
			{
				//get ark
				Edit edit = new Edit(
					data.getAdds(), data.getUpdates(),
					data.getDeletes(), data.getArk(), data.getTsName(),
					data.getNS()
				);
				if(edit.update())
				{
					removeOutputFile(filename);
				}
				else
				{
					//triplestore issue, write log or send response?
				}
			}
		}
		catch (FileNotFoundException e)
		{
			processed = false;
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			processed = false;
		}
		catch (ClassNotFoundException e)
		{
			processed = false;
			e.printStackTrace();
		}
		return processed;
	}
	/**
	 * if required, remove the file created
	 * @return True if the file is removed, false if removal fails or an error
	 *   occurs.
	 */
	public boolean cleanup()
	{
		return removeOutputFile( fileName );
	}
	private boolean removeOutputFile( String fileName )
	{
		File f = new File(fileName);
		if(!f.exists())
		{
			//throw some error
			return false;
		}
		else
		{
			return f.delete();
		}
	}
	
	public String[] getQueueList()
	{
		String backupDir = "";
		try
		{
			Context initCtx = new InitialContext();
			backupDir = (String)initCtx.lookup(BACKUP_PATH);
		}
		catch (NamingException e)
		{
			e.printStackTrace();
		}
		return new File(backupDir).list();
	}
	
	public static void main (String[] args) throws Exception
	{
		EditBackup backupProcess = new EditBackup();
		backupProcess.processBackupFiles();
	}
}
