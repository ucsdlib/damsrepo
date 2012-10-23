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
	private Identifier subject;
	private Identifier initiator;
	private boolean success;
	private String type;
	private String detail;
	private String outcomeNote;
	private Date eventDate;

	private String ns;
	private SimpleDateFormat fmt = new SimpleDateFormat(
		"yyyy-MM-dd'T'hh:mm:ssZ"
	);

	public Event( Identifier subject, Identifier initiator, boolean success,
		String type, String detail, String outcomeNote, String ns )
	{
		this.subject     = subject;
		this.initiator   = initiator;
		this.success     = success;
		this.type        = type;
		this.detail      = detail;
		this.outcomeNote = outcomeNote;
		eventDate = new Date();
	}
	public void insert( TripleStore ts ) throws TripleStoreException
	{
		ts.addLiteralStatement(subject, id("type"), type, subject );
		ts.addLiteralStatement(subject, id("detail"), detail, subject );
		ts.addLiteralStatement(subject, id("eventDate"), fmt.format(eventDate), subject);
		if ( outcomeNote != null && !outcomeNote.equals("") )
		{
			ts.addLiteralStatement(subject, id("outcomeNote"), outcomeNote, subject);
		}
		String outcome = success ? "success" : "failure";
		ts.addLiteralStatement(subject, id("outcome"), outcome, subject );

		Identifier bn = ts.blankNode();
		ts.addStatement(subject, id("relationship"), bn, subject );
		ts.addStatement(bn, id("name"), initiator, subject );
		ts.addStatement(bn, id("role"), id("initiator"), subject );
	}
	private Identifier id( String qname )
	{
		return Identifier.publicURI( ns + qname );
	}
}
