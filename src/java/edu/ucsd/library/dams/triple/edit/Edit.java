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

import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreUtil;
import edu.ucsd.library.dams.triple.TripleStoreException;
import edu.ucsd.library.dams.triple.Statement;
import edu.ucsd.library.dams.triple.StatementIterator;

/**
 * Core edit logic.
 * @author mcritchlow
 * @author escowles
**/
public class Edit
{
	private JSONArray adds = null;
	private JSONArray deletes = null;
	private JSONArray updates = null;
	private String ark = null;
	private TripleStore ts = null;
	private String ns = null;
	private EditBackup backup = null;
	//the following data structures could change
	private List triplesData = null;
	private String updatedFlag = "bb89430486"; // XXX

	// status tracking
	private Exception exception = null;
	private String status = "";

	public Edit( JSONArray adds, JSONArray updates, JSONArray deletes,
		String ark, TripleStore ts, String ns )
	{
		this.adds = adds;
		this.updates = updates;
		this.deletes = deletes;
		this.ark = ark;
		this.ts = ts;
		this.ns = ns;
		status = "init";
	}
	public Edit( JSONArray adds, JSONArray updates, JSONArray deletes,
		String ark, String tsName, String ns ) throws TripleStoreException
	{
		this.adds = adds;
		this.updates = updates;
		this.deletes = deletes;
		this.ark = ark;
		this.ns = ns;
		try
		{
			this.ts = TripleStoreUtil.getTripleStore(tsName);
		}
		catch ( Exception ex )
		{
			throw new TripleStoreException(
				"Error getting triplestore instance", ex
			);
		}
		status = "init";
	}
	
	public Edit( String adds, String updates, String deletes, String ark,
		TripleStore ts, String ns )
	{
		status = "init";
		if(!adds.equals(""))
		{
			this.adds = (JSONArray)JSONValue.parse(adds);
			status = "addsParse";
		}
		if(!updates.equals(""))
		{
			this.updates = (JSONArray)JSONValue.parse(updates);
			status = "updatesParse";
		}
		if(!deletes.equals(""))
		{
			this.deletes = (JSONArray)JSONValue.parse(deletes);
			status = "deletesParse";
		}
		this.ark = ark.substring(ark.indexOf("/")+1);
		this.ts = ts;
		this.ns = ns;
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
		 
		 //s2 = s2.replace("+"," ");
		 return s2;
	}
	
	public void removeBackup()
	{
		backup.cleanup();
	}
	/**
	 * Save data to a backup file to disk for later recovery.
	**/
	public void saveBackup()
	{
		backup = new EditBackup(adds,updates,deletes,ark,ts.name(),ns);
		status = "saving1";
		backup.saveDataToFile();
		status = "saving2";
	}

	/**
	 * Process updates by adding and/or deleting triples from the triplestore.
	**/
	public boolean update()
	{
		TripleStore ts = null;
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
					String subject = (String)obj.get("subject"); //blank node
					String predicate = (String)obj.get("predicate"); //ark for type(mods:type, etc)
					JSONArray object = (JSONArray)obj.get("object"); //{oldValue,new value}
					//perform triplestore update
					/*success = updateTriple(
						ts, subject, predicate,
						urldec((String)object.get(0)),
						urldec((String)object.get(1))
					);*/

					if(((String)object.get(1)).contains(".mp3")) {
						success = updateTriple(
								ts, ark, "bb50179366",
								urldec((String)object.get(0)+".mp3"),
								urldec((String)object.get(1))
							);
						success = updateTriple(
								ts, subject, predicate,
								urldec((String)object.get(0)),
								urldec(((String)object.get(1)).replace(".mp3",""))
							);
					} else {
						success = updateTriple(
								ts, subject, predicate,
								urldec((String)object.get(0)),
								urldec((String)object.get(1))
							);	
					}					
					/*if (!updateTagged)
					{
						addTriple(ts,subject,updatedFlag,"1");
						updateTagged = true;
					}*/
				}
				status = "updatesCompleted";
			}
			//adds
			if(adds != null)
			{
				//System.out.println("adds: " + adds);
				Iterator<JSONObject> iter=adds.iterator();
				HashMap<String,Identifier> blankNodes = new HashMap<String,Identifier>(); //lookup list of blank nodes for adds
				while(iter.hasNext())
				{
					JSONObject obj = iter.next();
					String subject = (String)obj.get("subject"); //blank node
					String predicate = (String)obj.get("predicate"); //ark for type(mods:type, etc)
					String object = (String)obj.get("object"); //new value
					object = urldec(object);
					if(object.equals(""))
					{
						continue; //ignore empty object triples
					}
					//if the object is a new blank node, create one and set as currNode
					Identifier s = null;
					Identifier o = null;
					if(object.startsWith("node"))
					{
						o = ts.blankNode();
						blankNodes.put(object, o);
						//System.out.println("generating new blank node: " + o);
					}
					//if the subject is a new node, there should already be a
					//reference in the hashmap
					if(subject.startsWith("node"))
					{
						s = blankNodes.get(subject);
					}
					//debug statements
					//if(s == null)
					//{
					//System.out.println("Subject blank node is null");
					//}
					//if(o == null)
					//{
					//System.out.println("Object blank node is null");
					//}
					if(predicate.equals("bb5564025j") && object.contains(".mp3")) {
						object = object.replace(".mp3","");
					}
					if(s != null || o != null)
					{
						//structured add
						//System.out.println("Structured add...");
						success = addTriple(
							ts, (s != null) ? s : subject, predicate,
							(o != null) ? o : object
						);
						/*if (!updateTagged)
						{
							addTriple(ts,subject,updatedFlag,"1");
							updateTagged = true;
						}*/
					}
					else
					{
						//low level add
						success = addTriple(ts,subject, predicate, object);
						/*if (!updateTagged)
						{
							addTriple(ts,subject,updatedFlag,"1");
							updateTagged = true;
						}*/
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
					String subject = (String)obj.get("subject"); //blank node
					String predicate = (String)obj.get("predicate"); //ark for type(mods:type, etc)
					String object = (String)obj.get("object"); //new value
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
		finally
		{
			if ( ts != null )
			{
				try { ts.close(); }
				catch ( Exception ex ) { ex.printStackTrace(); }
			}
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
		Identifier parent = Identifier.publicURI(ns+ark);
		Identifier pred = Identifier.publicURI(ns+predicate);
		try
		{
			if( subject instanceof String )
			{
				String s = subject.toString();
				//System.out.println("addTriple(): s = " + s);
				if(s.startsWith("_:"))
				{
					subject = ts.blankNode(Identifier.publicURI(ns+ark), s);
				}
				else
				{
					subject = Identifier.publicURI(ns+s);
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
			//System.out.println("removeTriples(): " + subject + ", " + predicate + ", " + object);
			Identifier pred = Identifier.publicURI(ns+predicate);
			Identifier subj = (subject.startsWith("_:")) ? ts.blankNode(Identifier.publicURI(ns+ark), subject) : Identifier.publicURI(ns+subject);
			if( object.startsWith("_:") )
			{
				//System.out.println("   bnode");
				Identifier obj = ts.blankNode(Identifier.publicURI(ns+ark), object);
				//System.out.println("Going into removeTriples() with object: "+obj.toString());
				removeTriples( ts, obj ); //remove all children
				ts.removeStatements(subj, pred, obj);
			}
			else
			{
				// XXX: what about URI objects???
				//System.out.println("   literal");
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
		//System.out.println("Inside removeTriples() with object: "+blankNode.toString());
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
					//System.out.println("Leaf to remove: s: "+stmt.getSubject().getId()+" p: "+stmt.getPredicate()+" o: "+stmt.getLiteral());
					ts.removeLiteralStatements(stmt.getSubject(), stmt.getPredicate(), stmt.getLiteral());
				}
				else
				{
					//blank node, store for later removal
					//System.out.println("Found blank node in object, add to list");
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
				//System.out.println("Calling removeTriples with: "+bn.toString());
				removeTriples(ts,bn);
			}
			//remove the blank node statements themselves
			try
			{
				for(Statement s : bnTriples)
				{
					//System.out.println("Removing blank node statement: "+s.toString());
					ts.removeStatements(s.getSubject(), s.getPredicate(), s.getObject());
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
		//System.out.println("updateTriple: " + subject + ", " + predicate + ", " + currentObj + " => " + newObj);
		//find triple with subject/predicate/old value
		try
		{   
			if(newObj==null){
				newObj="";
			}
			Identifier parent = Identifier.publicURI(ns+ark);
			StatementIterator iter = null;
			//get Subject
			Identifier s = null;
			//get Predicate
			Identifier p = null;
			Identifier subjNode = null;
			if(subject.startsWith("_:"))
			{
				subjNode = ts.blankNode(parent, subject);
				ts.removeLiteralStatements(
					subjNode, Identifier.publicURI(ns+predicate), currentObj
				);
			}
			else
			{
				iter = ts.listLiteralStatements(
					Identifier.publicURI(ns+subject),
					Identifier.publicURI(ns+predicate),
					currentObj
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
				subjNode, Identifier.publicURI(ns+predicate), newObj, parent
			);
		}
		catch (TripleStoreException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
