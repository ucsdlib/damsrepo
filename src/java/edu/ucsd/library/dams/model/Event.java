package edu.ucsd.library.dams.model;

import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import edu.ucsd.library.dams.triple.Identifier;
import edu.ucsd.library.dams.triple.TripleStore;
import edu.ucsd.library.dams.triple.TripleStoreException;

/**
 * Event object.
 * @author escowles@ucsd.edu
**/
public class Event
{
	public static String CHECKSUM_CALCULATED = "checksum calculated";
	public static String CHECKSUM_VERIFIED   = "checksum verified";
	public static String DERIVATIVE_CREATED  = "derivative created";
	public static String FILE_ADDED          = "file added";
	public static String FILE_DELETED        = "file deleted";
	public static String FILE_MODIFIED       = "file modified";
	public static String FILE_VIRUS_CHECKED  = "file virus checked ";
	public static String RECORD_CREATED      = "record created";
	public static String RECORD_EDITED       = "record edited";
	public static String RECORD_DELETED      = "record deleted";
	public static String RECORD_INDEXED      = "record indexed";
	public static String RECORD_TRANSFORMED  = "record transformed";
	public static String OBJECT_REPLICATED   = "object replicated";
	public static String OBJECT_VALIDATED    = "object validated";

	private Identifier eventID;
	private Identifier parent;
	private Identifier subject;
	private Identifier userID;
	private boolean success;
	private String type;
	private String detail;
	private String outcomeNote;
	private Date eventDate;

	private SimpleDateFormat fmt = null;

	public Event( Identifier eventID, Identifier parent, Identifier subject,
		Identifier userID, boolean success, String type, String detail,
		String outcomeNote )
	{
		this.eventID     = eventID;
		this.parent      = parent;
		this.subject     = subject;
		this.userID      = userID;
		this.success     = success;
		this.type        = type;
		this.detail      = detail;
		this.outcomeNote = outcomeNote;
		eventDate = new Date();

		// setup time format
		fmt = new SimpleDateFormat( "yyyy-MM-dd'T'hh:mm:ssZ" );
	    fmt.setTimeZone( TimeZone.getTimeZone("UTC") );
	}
	public void save( TripleStore ts, TripleStore es )
		throws TripleStoreException
	{
		// link subject to event
		ts.addStatement( subject, id("dams:event"), eventID, parent );

		// insert event metadata
		es.addStatement( eventID, id("rdf:type"), id("dams:DAMSEvent"), eventID );
		es.addLiteralStatement( eventID, id("dams:type"), q(type), eventID );
		es.addLiteralStatement(
			eventID, id("dams:eventDate"), q(fmt.format(eventDate)), eventID
		);
		if ( detail != null && !detail.equals("") )
		{
			es.addLiteralStatement(
				eventID, id("dams:detail"), q(detail), eventID
			);
		}
		if ( outcomeNote != null && !outcomeNote.equals("") )
		{
			es.addLiteralStatement(
				eventID, id("dams:outcomeNote"), q(outcomeNote), eventID
			);
		}
		String outcome = success ? q("success") : q("failure");
		es.addLiteralStatement( eventID, id("dams:outcome"), q(outcome), eventID );

		Identifier bn = es.blankNode();
		es.addStatement( eventID, id("dams:relationship"), bn, eventID );
		if ( this.userID == null ) { this.userID = id("dams:unknownUser"); }
		es.addStatement( bn, id("dams:name"), userID, eventID );
		es.addStatement( bn, id("dams:role"), id("dams:initiator"), eventID );
	}
	private String q( String s )
	{
		if ( s.startsWith("\"") && (s.endsWith("\"") || s.indexOf("\"@") > 0
							|| s.indexOf("\"^^") > 0 ) )
		{
			return s;
		}
		else
		{
			return "\"" + s.replaceAll("\"","\\\\\\\"") + "\"";
		}
	}
	private Identifier id( String pred ) throws TripleStoreException
	{
		return Identifier.publicURI( pred );
	}
}
