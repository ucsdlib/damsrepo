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
	private Identifier subject;
	private Identifier userID;
	private boolean success;
	private String type;
	private String detail;
	private String outcomeNote;
	private Date eventDate;

	private String predicateNS;
	private SimpleDateFormat fmt = new SimpleDateFormat(
		"yyyy-MM-dd'T'hh:mm:ssZ"
	);

	public Event( Identifier eventID, Identifier subject, Identifier userID,
		boolean success, String type, String detail, String outcomeNote,
		String predicateNS )
	{
		this.eventID     = eventID;
		this.subject     = subject;
		this.userID      = userID;
		this.success     = success;
		this.type        = type;
		this.detail      = detail;
		this.outcomeNote = outcomeNote;
		eventDate = new Date();
	}
	public void save( TripleStore ts ) throws TripleStoreException
	{
		// link subject to event
		ts.addStatement( subject, id("event"), eventID, subject );

		// insert event metadata
		ts.addLiteralStatement( eventID, id("type"), type, eventID );
		ts.addLiteralStatement( eventID, id("detail"), detail, eventID );
		ts.addLiteralStatement(
			eventID, id("eventDate"), fmt.format(eventDate), eventID
		);
		if ( outcomeNote != null && !outcomeNote.equals("") )
		{
			ts.addLiteralStatement(
				eventID, id("outcomeNote"), outcomeNote, eventID
			);
		}
		String outcome = success ? "success" : "failure";
		ts.addLiteralStatement( eventID, id("outcome"), outcome, eventID );

		Identifier bn = ts.blankNode();
		ts.addStatement( eventID, id("relationship"), bn, eventID );
		ts.addStatement( bn, id("name"), userID, eventID );
		ts.addStatement( bn, id("role"), id("initiator"), eventID );
	}
	private Identifier id( String predicateName )
	{
		return Identifier.publicURI( predicateNS + predicateName );
	}
}
