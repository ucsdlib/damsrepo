package edu.ucsd.library.dams.util;

import java.util.Map;

/**
 * Interface for streaming output utilities.
 * @author escowles
**/
public interface OutputStreamer
{
	/**
	 * Start output.
	**/
	public void start( String groupName ) throws Exception;

	/**
	 * Output a record.
	 * @param record Map containg key-value pairs.
	**/
	public void output( Map<String,String> record ) throws Exception;

	/**
	 * Finish output.
	 * @param info Map containg key-value pairs for extra response-scoped
	 *   metadata (such as status, record count, etc.).
	**/
	public void finish( Map<String,String> info ) throws Exception;
}
