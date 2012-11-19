package edu.ucsd.library.dams.triple.edit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import edu.ucsd.library.dams.model.DAMSObject;
import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;

/**
 * Core edit logic.
 * @author mcritchlow@ucsd.edu
 * @author escowles@ucsd.edu
**/
public class Edit
{
	private JSONArray adds = null;
	private JSONArray deletes = null;
	private JSONArray updates = null;
	private String ark = null;
	private String tsName = null;

	private TripleStore ts = null;
	private String backupDir = null;
	private String backupFile = null;
	private String idNS = null;
	private String prNS = null;
	private String owlSameAs = null;
	private EditData backup = null;
	private List triplesData = null;

	// for pre/ark mapping
	private DAMSObject trans = null;

	// status tracking
	private Exception exception = null;
	private String status = "";

	// used by EditBackup
	public Edit( JSONArray adds, JSONArray updates, JSONArray deletes,
		String ark, String tsName ) throws TripleStoreException
	{
		this.adds = adds;
		this.updates = updates;
		this.deletes = deletes;
		this.ark = ark;
		this.tsName = tsName;
		status = "init";
	}
	
	// used by DAMSAPIServlet
	public Edit( String backupDir, String adds, String updates, String deletes,
		String ark, TripleStore ts, Map<String,String> nsmap )
	{
		status = "init";
		if ( adds != null && !adds.equals("") )
		{
			this.adds = (JSONArray)JSONValue.parse(adds);
			status = "addsParse";
		}
		if ( updates != null && !updates.equals("") )
		{
			this.updates = (JSONArray)JSONValue.parse(updates);
			status = "updatesParse";
		}
		if ( deletes != null && !deletes.equals("") )
		{
			this.deletes = (JSONArray)JSONValue.parse(deletes);
			status = "deletesParse";
		}
		this.ark = ark.replaceAll(".*/","");
		this.ts = ts;
		this.trans = new DAMSObject( ts, "", nsmap );
		this.idNS = nsmap.get("damsid");
		this.prNS = nsmap.get("dams");
		this.backupDir = backupDir;
	}

	private static String urldec( String s )
	{
		String s2 = null;
		
		try
		{
			 s2 = URLDecoder.decode(s,"UTF-8");
		 }
		 catch ( Exception ex )
		 {
			 ex.printStackTrace();
			 s2 = s;
		 }
		 
		 return s2;
	}
	
	/**
	 * Save data to a backup file to disk for later recovery.
	**/
	public void saveBackup() throws IOException
	{
		backup = new EditData( adds, updates, deletes, ark, ts.name() );
		status = "saving1";
		String id = ark.replaceAll(".*/","");
		backupFile = backupDir + "/" + id + "_"
			+ String.valueOf(System.currentTimeMillis());
		ObjectOutputStream cout = new ObjectOutputStream(
			new FileOutputStream( backupFile )
		);
		cout.writeObject(backup);
		cout.close();
		status = "saving2";
	}
	/**
	 * Remove backup file after edit transaction is complete.
	**/
	public void removeBackup() throws IOException
	{
		File f = new File(backupFile);
		if(!f.exists())
		{
			throw new FileNotFoundException();
		}
		else
		{
			f.delete();
		}
	}

	/**
	 * Process updates by adding and/or deleting triples from the triplestore.
	**/
	public boolean update()
	{
		boolean success = true;
		boolean updateTagged = false;

		try
		{
			//updates
			if(updates != null)
			{
				//loop through updates
				Iterator<JSONObject> iter= updates.iterator();
				while(iter.hasNext())
				{
					JSONObject obj = iter.next();
					String subject = (String)obj.get("subject");    //bnode
					String predicate = (String)obj.get("predicate");//dams:type
					JSONArray object = (JSONArray)obj.get("object");//{old,new}
					//perform triplestore update
					success = updateTriple(
						ts, subject, predicate,
						urldec((String)object.get(0)),
						urldec((String)object.get(1))
					);	
				}
				status = "updatesCompleted";
			}
			//adds
			if(adds != null)
			{
				Iterator<JSONObject> iter=adds.iterator();
				Map<String,Identifier> blankNodes
					= new HashMap<String,Identifier>(); //bnode map for adds
				while(iter.hasNext())
				{
					JSONObject obj = iter.next();
					String subject = (String)obj.get("subject");    //bnode
					String predicate = (String)obj.get("predicate");//dams:type
					String object = (String)obj.get("object");      //new value
					object = urldec(object);
					if(object.equals(""))
					{
						continue; //ignore empty object triples
					}
					//if the object is a new bnode, create it & set as currNode
					Identifier s = null;
					Identifier o = null;
					if(object.startsWith("node"))
					{
						o = ts.blankNode();
						blankNodes.put(object, o);
					}
					else if ( (object.startsWith("<") && object.endsWith(">"))
						|| object.startsWith("_:") || object.indexOf(":") > 0 )
					{
						o = objectURI( object );
					}
					//if the subject is a new node, there should already be a
					//reference in the hashmap
					if(subject.startsWith("node"))
					{
						s = blankNodes.get(subject);
					}
					if(s != null || o != null)
					{
						//structured add
						success = addTriple(
							ts, (s != null) ? s : subject, predicate,
							(o != null) ? o : object
						);
					}
					else
					{
						//low level add
						success = addTriple(ts,subject, predicate, object);
					}
					//add triple to triplestore
				}
				status = "addsCompleted";
			}
			//deletes
			if(deletes != null)
			{
				Iterator<JSONObject> iter=deletes.iterator();
				while(iter.hasNext())
				{
					JSONObject obj = iter.next();
					String subject = (String)obj.get("subject");    //bnode
					String predicate = (String)obj.get("predicate");//dams:type
					String object = (String)obj.get("object");      //new value
					object = urldec(object);
					success = removeTriples(ts,subject, predicate, object);
				}
				status = "deletesCompleted";
			}
		}
		catch ( Exception ex )
		{
			success = false;
			exception = ex;
			ex.printStackTrace();
		}
		return success;
	}
	public Exception getException()
	{
		return exception;
	}
	public String getStatus()
	{
		return status;
	}

	private String updateToString( String subject, String predicate,
		Object object)
	{
		StringBuffer buffer = new StringBuffer();
		buffer.append("Subject: "+subject);
		buffer.append(" Predicate: "+predicate);
		if(object instanceof JSONArray)
		{
			buffer.append(" Old Object: "+((JSONArray)object).get(0));
			buffer.append(" New Object: "+((JSONArray)object).get(1));
		}
		else
		{
			buffer.append(" Object: "+predicate);
		}
		buffer.append("\n");
		return buffer.toString();
	}
	
	/**************************************************************************
	 ** methods from edu.ucsd.itd.edit.AG
	 **************************************************************************/

	public boolean addTriple( TripleStore ts, Object subject,
		String predicate, Object object)
	{
		try
		{
			Identifier parent = identifier(ark);
			Identifier pred = predicate(predicate);
			if( subject instanceof String )
			{
				String s = subject.toString();
				if(s.startsWith("_:"))
				{
					subject = ts.blankNode(identifier(ark), s);
				}
				else
				{
					subject = identifier(s);
				}
			}
			if( object instanceof String )
			{
				ts.addLiteralStatement(
					(Identifier)subject, pred, object.toString(), parent
				);
			}
			else
			{
				ts.addStatement(
					(Identifier)subject, pred, (Identifier)object, parent
				);
			}
		}
		catch (TripleStoreException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}

   /**
	 * Main removal method which will either remove a single statement, or if
	 * the object is a blank node, will recurse and remove all children.
	 * @param subject
	 * @param predicate
	 * @param object
	 */
	public boolean removeTriples( TripleStore ts, String subject,
		String predicate, String object )
	{
		try
		{
			Identifier pred = predicate(predicate);
			Identifier subj = (subject.startsWith("_:")) ?
				ts.blankNode(identifier(ark), subject) : identifier(subject);
			if( object.startsWith("_:") )
			{
				Identifier obj = ts.blankNode( identifier(ark), object );
				removeTriples( ts, obj ); //remove all children
				ts.removeStatements(subj, pred, obj);
			}
			else
			{
				// XXX: what about URI objects???
				ts.removeLiteralStatements(subj, pred, object);
			}
		}
		catch (TripleStoreException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	/**
	 * Recursive method to delete child statements of a blank node.
	 * @param blankNode Subject(parent) to use in deleting child triples
	 * @return True if the removal succeeded, false if it failed.
	 */
	public boolean removeTriples( TripleStore ts, Identifier blankNode )
	{
		ArrayList<Identifier> blankNodes = new ArrayList<Identifier>();
		ArrayList<Statement> bnTriples = new ArrayList<Statement>();
		try
		{
			StatementIterator iter = ts.listStatements(blankNode, null, null);
			while(iter.hasNext())
			{
				Statement stmt = iter.nextStatement();
				if(stmt.hasLiteralObject())
				{
					//leaf, remove
					ts.removeLiteralStatements(
						stmt.getSubject(), stmt.getPredicate(),
						stmt.getLiteral()
					);
				}
				else
				{
					//blank node, store for later removal
					blankNodes.add(stmt.getObject());
					bnTriples.add(stmt);
				}
			}
			iter.close();
		}
		catch (TripleStoreException e)
		{
			e.printStackTrace();
			return false;
		}
		if(blankNodes.size() > 0)
		{
			//remove the children of the blank nodes
			for(Identifier bn : blankNodes)
			{
				removeTriples(ts,bn);
			}
			//remove the blank node statements themselves
			try
			{
				for(Statement s : bnTriples)
				{
					ts.removeStatements(
						s.getSubject(), s.getPredicate(), s.getObject()
					);
				}
			}
			catch (TripleStoreException e)
			{
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	/**
	 * Update method, which removes the statement which contained the
	 * previous object value, and creates a new statement with the same
	 * predicate and object, and uses the new value for the object.
	 * @param ts TripleStore object to perform updates
	 * @param subject Triple subject
	 * @param predicate Triple predicate
	 * @param currentObj Current object value
	 * @param newObj New object value
	 * @return True if the update succeeded, false if it failed.
	**/
	public boolean updateTriple( TripleStore ts, String subject,
		String predicate, String currentObj, String newObj )
	{
		//find triple with subject/predicate/old value
		try
		{   
			if(newObj==null) { newObj=""; }
			Identifier parent = identifier(ark);
			StatementIterator iter = null;
			Identifier s = null;
			Identifier p = null;
			Identifier subjNode = null;
			if(subject.startsWith("_:"))
			{
				subjNode = ts.blankNode(parent, subject);
				ts.removeLiteralStatements(
					subjNode, predicate(predicate), currentObj
				);
			}
			else
			{
				iter = ts.listLiteralStatements(
					identifier(subject), predicate(predicate), currentObj
				);
				if(iter.hasNext())
				{
					Statement currStatement = iter.nextStatement();
					subjNode = currStatement.getSubject();
					ts.removeLiteralStatements(
						subjNode, currStatement.getPredicate(), currentObj
					);
				} 
			}
			if (iter != null) { iter.close(); }
			ts.addLiteralStatement(
				subjNode, predicate(predicate), newObj, parent
			);
		}
		catch (TripleStoreException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	/**
	 * predicate translation from human-readable URIs to ARK URIs
	**/
	private Identifier predicate( String name ) throws TripleStoreException
	{
		// make sure name is full URI
		String localName = name;
		String arkName = null;
		// XXX: add support for owl:, rdf:, etc.
		if ( localName.startsWith("dams:") )
		{
			localName = prNS + localName.substring(5);
			arkName = trans.preToArk(localName);
		}
		else
		{
			arkName = trans.lblToArk(localName);
		}
		if ( arkName == null )
		{
			throw new TripleStoreException("Can't find ARK for " + name);
		}
		return Identifier.publicURI(arkName);
	}
	private Identifier objectURI( String object )
	{
		if ( object == null ) { return null; }
		else if ( object.startsWith("<") && object.endsWith(">") )
		{
			object = object.substring(1,object.length()-1);
		}

		if ( object.startsWith(idNS) ) { return Identifier.publicURI(object); }
		else
		{
			try
			{
				return predicate(object);
			}
			catch ( Exception ex )
			{
				return null;
			}
		}
	}
	/**
	 * predicate translation from human-readable URIs to ARK URIs
	**/
	private Identifier identifier( String ark )
	{
		String id = ark.startsWith("http") ? ark : idNS + ark;
		return Identifier.publicURI( id );
	}
}
