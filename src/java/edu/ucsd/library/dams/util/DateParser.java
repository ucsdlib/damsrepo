package edu.ucsd.library.dams.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.commons.lang3.time.DateUtils;

/**
 * Utilities to parse dates form structured and semi-structured text.
 * @author escowles@ucsd.edu
**/
public class DateParser
{
    private static String[] datePatterns = new String[]{
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss Z",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy-MM", "yyyy_MM", "yyyy"
    };

    /**
     * Do basic cleanup and try to parse a date from a string using Apache
     * Commons DateUtils.  This is a good first attempt, and works well for
	 * structured dates, but not for free-form dates.
     * @return A Date object, or null if unable to parse.
    **/
    public static Date parseDate( String s )
    {
        Date d = null;
        try
        {
            /* cleanup */
            // remove ending question marks
            s = s.replaceAll("\\?$","");

            // remove ending " hours"
            s = s.replaceAll(" hours$",":00");

            // remove colon from timezone
            s = s.replaceAll(
                "(T\\d\\d:\\d\\d:\\d\\d-)(\\d\\d):(\\d\\d)","$1$2$3"
            );

            // remove spaces before dashes
            s = s.replaceAll(" *-","-");

            // fix 1900-1924 dates so they aren't parsed as yyyy-mm
            s = s.replaceAll("(\\d\\d\\d\\d)-\\d\\d\\d\\d","$1");

            // remove anything after " or "
            s = s.replaceAll(" or .*","");

            d = DateUtils.parseDate( s, datePatterns );
        }
        catch ( Exception ex )
        {
            d = null;
        }

        return d;
    }

    /**
     * Parse a string into Date objects.  Note: this method only parses dates
     * to the granularity of years.
     * @param s A String containing free-format dates
     * @return A Date object for the first parsable date
    **/
    public static Date parseFirst( String s )
    {
        Date[] dates = parse( s );
        return dates[0];
    }
    public static int parseFirstYear( String s )
    {
        int[] years = parseYears( s );
        return years[0];
    }

    public static int[] parseYears( String s )
    {
        // remove any enclosing parens
        s = s.replaceAll( "^\\((.*)\\)$", "$1" );
        int[] years = null;

        /* set default era
                default = CE
                if BCE or equiv, BCE
        */
        int era = GregorianCalendar.AD;
        boolean beforePresent = false;
        if ( s.indexOf("BC") > -1   ||
                s.indexOf("B.C") > -1 || s.indexOf("BCE") > -1 )
        {
            era = GregorianCalendar.BC;
        }
        else if ( s.indexOf("BP") > -1  || s.indexOf("years ago") > -1 )
        {
            era = GregorianCalendar.BC;
            beforePresent = true;
        }

        /* set default time unit
                default = 1 year
                if Century-equiv is present, 100 years
        */
        int unit = 1;
        if ( s.indexOf("cent") > -1 || s.indexOf(" C. ") > -1 ||
            s.endsWith(" C.") || s.endsWith(" c.") || s.indexOf(" C ") > -1 ||
            s.lastIndexOf(" C") > s.length() - 3 ||
            s.lastIndexOf(" c") > s.length() - 3 )
        {
            unit = 100;
        }
        else if ( s.indexOf("million") > -1 || s.indexOf("M.") > -1 )
        {
            unit = 1000000;
            era = GregorianCalendar.BC;
            beforePresent = true;
        }

        /* strip month and day portions of ISO 9601 dates (YYYY-MM-DD), so they
           don't get interpreted as YYYY-YY-YY */
        s = s.replaceAll("(\\d+)-\\d+-\\d+","$1");

        /* remove initial - */
        if ( s.startsWith("-") )
        {
            s = s.substring(1);
        }

        /* split into fields based on: - ; and to */
        String[] fields = s.split( "-|;|\\(|\\)|\\band\\b|\\bto\\b" );
        years = new int[ fields.length];

        /* process each field */
        boolean spansEra = false;
        String txt;
        int val = 0;
        int last = -1;
        for( int i = 0; i < fields.length; i++ )
        {
            txt = fields[i];

            // remove w3cdtf
            txt = txt.replaceAll("w3cdtf","");

            // override era
            if ( i != 0 && era == GregorianCalendar.BC &&
                ( txt.indexOf("AD") > -1 ||
                txt.indexOf("A.D") > -1 ||
                txt.indexOf("CE") > -1 ) )
            {
                era = GregorianCalendar.AD;
                spansEra = true;
            }

            try
            {
                // get the numeric value as an int
                String num = txt.replaceAll("\\d+, ",""); // Jan 26, 1960
                boolean mdyFormat = false;
                if ( num.matches("\\d+/\\d+/\\d+") )
                {
                    mdyFormat = true;
                    num = num.replaceAll("\\d+/\\d+/","");    // 11/23/36
                }
                num = num.replaceAll(".+ quarter","");
                num = num.replaceAll("\\D+"," ");
                num = num.replaceAll("^\\D+","");
                num = num.replaceAll("\\D+.*","");
                val = Integer.parseInt( num );

                // fix truncated dates
                if ( mdyFormat && val < 100 )
                {
                    // 11/23/36
                    val += 1900;
                }
                if( !spansEra && val < last && val < 1000 )
                {
                    // 1508-12
                    val += last - ( last % Math.pow( 10, num.length() ) );
                }

                // don't continue processing for repeated dates
                if ( val != last || spansEra)
                {
                    // store unmodified date value
                    last = val;

                    // calculate the year value for the date
                    val *= unit;
                    if ( era == GregorianCalendar.AD && unit == 100 )
                    {
                        if ( i == 0 )
                        {
                            val -= 100;
                        }
                        else
                        {
                            val--;
                        }
                    }

                    // they probably don't mean before *present* ...
                    if ( beforePresent )
                    {
                        val += Calendar.getInstance().get( Calendar.YEAR );
                    }

                    // correct for fractional centuries (3rd quarter 14th C)
                    if ( txt.matches(".*2\\w+ quarter.*") ||
                        txt.matches(".*[Ss]econd quarter.*") )
                    {
                        val += 25;
                    }
                    else if ( txt.matches(".*3\\w+ quarter.*") ||
                        txt.matches(".*[Tt]hird quarter.*") )
                    {
                        val += 50;
                    }
                    else if ( txt.matches(".*4\\w+ quarter.*") ||
                        txt.matches(".*[Ff]ourth quarter.*") )
                    {
                        val += 75;
                    }
                    else if ( txt.matches(".*2\\w+ half.*") ||
                        txt.matches(".*[Ss]econd half.*") )
                    {
                        val += 50;
                    }

                    if ( era == GregorianCalendar.BC )
                    {
                        years[i] = 0 - val;
                    }
                    else
                    {
                        years[i] = val;
                    }
                }
            }
            catch ( NumberFormatException ex )
            {
                /* number format exceptions mostly stem from tokens that
                    contain no parseable numbers. */
            }
        }

        return years;
    }

    /**
     * Parse a string into Date objects.  Note: this method only parses dates
     * to the granularity of years.
     * @param s A String containing free-format dates
     * @return Array containing a Date object for each parsable date
    **/
    public static Date[] parse( String s )
    {
        int[] years = parseYears( s );
        Date[] dates = new Date[ years.length ];

        // convert the year to a date value
        for ( int i = 0; i < years.length; i++ )
        {
            Calendar c = Calendar.getInstance();
            c.set( Math.abs(years[i]), 0, 1, 1, 1, 1 ); // set year and default date
            if ( years[i] < 0 )
            {
                c.set( Calendar.ERA, GregorianCalendar.BC ); // set era
            }
            else
            {
                c.set( Calendar.ERA, GregorianCalendar.AD ); // set era
            }
            dates[i] = c.getTime();
        }
        return dates;
    }

    /**
     * Command-line operation.
     * @param args[0] Text to parse for dates.
    **/
    public static void main( String[] args )
    {
        String text = null;
        try
        {
            text = args[0];
        }
        catch ( RuntimeException ex )
        {
            System.err.println("usgae: DateParser [text]");
            System.exit(1);
        }

        int[] years = DateParser.parseYears( text );
        for( int i = 0; i < years.length; i++ )
        {
            System.out.println( i + ": " + years[i] );
        }
    }
}
