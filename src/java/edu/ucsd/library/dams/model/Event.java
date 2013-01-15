package edu.ucsd.library.dams.model;

import java.util.Date;
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
	private Identifier eventID;
	private Identifier parent;
	private Identifier subject;
	private Identifier userID;
	private boolean success;
	private String type;
	private String detail;
	private String outcomeNote;
	private Date eventDate;

	private SimpleDateFormat fmt = new SimpleDateFormat(
		"yyyy-MM-dd'T'hh:mm:ssZ"
	);

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
