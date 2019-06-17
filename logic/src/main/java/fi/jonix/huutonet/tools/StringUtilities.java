package fi.jonix.huutonet.tools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class StringUtilities {

	/**
	 * @param text String that possibly contains characters that cannot be directly displayed in html.
	 * @return String that has problematic characters HTML escaped.
	 */
	public static String escapeString2HTML(String text, boolean fixNewLine) {
		String returnText = text.replaceAll("å", "&aring;").replaceAll("Å", "&Aring;");
		returnText = returnText.replaceAll("ä", "&auml;").replaceAll("Ä", "&Auml;");
		returnText = returnText.replaceAll("ö", "&ouml;").replaceAll("Ö", "&Ouml;");
		returnText = returnText.replaceAll("€", "&euro;").replaceAll("£", "&pound;");
		if (fixNewLine) {
			returnText = returnText.replaceAll("\n", "<br/>");
		}
		return returnText;
	}

	public static String escapeString2HTML(String text) {
		return escapeString2HTML(text, false);
	}
	
	/**
	 * This method removes very weird characters altogether and removes all
	 * diacritics ("Tĥïŝ ĩš â fůňķŷ Šťŕĭńġ" becomes "This is a funky String").
	 * Exception: scandics are not touched.
	 * 
	 * @param text
	 * @return
	 */
	public static String normalizer(String text) {
		//return text.replaceAll("[^A-Za-z0-9åäöÅÄÖ<>&; .,\\-%/+:;_*\\\\#'\"?=()!@£$€{}\\[\\]~|]", "");
		
		/* Start by replacing scandics. */
		if (text.indexOf("aaaaaaaaaa") > 0 || text.indexOf("AAAAAAAAAA") > 0
				|| text.indexOf("aeaeaeaeae") > 0 || text.indexOf("AEAEAEAEAE") > 0
				|| text.indexOf("oooooooooo") > 0 || text.indexOf("OOOOOOOOOO") > 0) {
			throw new RuntimeException("Couldn't normalize string " + text);
		}
		String tempStr = text.replaceAll("å","aaaaaaaaaa").replaceAll("Å","AAAAAAAAAA").
			replaceAll("ä","aeaeaeaeae").replaceAll("Ä","AEAEAEAEAE").
			replaceAll("ö","oooooooooo").replaceAll("Ö","OOOOOOOOOO");

		/* Then normalize the string (for example "Tĥïŝ ĩš â fůňķŷ Šťŕĭńġ"
		 * becomes "This is a funky String"). */
		//tempStr = Normalizer.normalize(tempStr,Normalizer.Form.NFD).
			//replaceAll("[^A-Za-z0-9åäöÅÄÖ<>&; .,\\-%/+:;_*\\\\#'\"?=()!@£$€{}\\[\\]~|]", "");
		tempStr = Normalizer.normalize(tempStr,Normalizer.Form.NFD).replaceAll("[^\\p{InBasicLatin}£€]+", "");
		
		/* Finally restore the scandics. */
		return tempStr.replaceAll("aaaaaaaaaa","å").replaceAll("AAAAAAAAAA","Å").
			replaceAll("aeaeaeaeae","ä").replaceAll("AEAEAEAEAE","Ä").
			replaceAll("oooooooooo","ö").replaceAll("OOOOOOOOOO","Ö");
	}
	
	public static String htmlEscapes2PlainText(String text) {
		String returnText = text.replaceAll("&aring;", "å").replaceAll("&Aring;", "Å");
		returnText = returnText.replaceAll("&auml;", "ä").replaceAll("&Auml;", "Ä");
		returnText = returnText.replaceAll("&ouml;", "ö").replaceAll("&Ouml;", "Ö");
		returnText = returnText.replaceAll("&euro;", "€").replaceAll("&pound;", "£");
		returnText = returnText.replaceAll("&amp;", "&");
		return returnText;
	}

	public static String escapeString2URL(String text) {
		String returnText = text.replaceAll("å", "%C3%A5").replaceAll("Å", "%C3%85");
		returnText = returnText.replaceAll("ä", "%C3%A4").replaceAll("Ä", "%C3%84");
		returnText = returnText.replaceAll("ö", "%C3%B6").replaceAll("Ö", "%C3%96");
		returnText = returnText.replaceAll(" ", "%20").replaceAll("£", "%C2%A3");
		returnText = returnText.replaceAll("\\$", "%24").replaceAll("@", "%40");
		returnText = returnText.replaceAll("&", "%26").replaceAll(",", "%2C");
		returnText = returnText.replaceAll("\\+", "%2B").replaceAll("/", "%2F");
		returnText = returnText.replaceAll(":", "%3A").replaceAll(";", "%3B");
		return returnText;
	}

	public static String replaceNewLinesWithSpace(String text) {
		return text.replaceAll("\r\n"," ").replaceAll("\n"," ").replaceAll("\r"," ").replaceAll(" +"," ");
	}

	public static String fixNewLinesToUnix(String text) {
		return text.replaceAll("\r\n","\n").replaceAll("\r","\n");
	}

	/**
	 * Returns a string where all occurrences of multiple spaces are truncated to one space,
	 * i.e. also inside text.
	 */
	public static String trimMultipleSpaces(String text) {
		return text.replaceAll("\t"," ").replaceAll(" +"," ");
	}

	/**
	 * This method trims spaces at the beginning of lines. I.e.
	 * line one
	 *  line two
	 *   line three
	 * =>
	 * line one
	 * line two
	 * line three
	 */
	public static String trimSpacesAtEachLine(String text) {
		return text.trim().replaceAll("\n( )+", "\n").replaceAll("\n(\t)+", "\n");
	}

	public static String removeNonUSASCIICharacters(String text) {
		return text.replaceAll("[^\\p{ASCII}]","");
	}
	
	public static String toUSASCIICharacters(String text) {
		return text.replaceAll("å","a").replaceAll("ä","a").replaceAll("ö","o")
			.replaceAll("Å","A").replaceAll("Ä","A").replaceAll("Ö","O")
			.replaceAll("é","e").replaceAll("É","E");
	}

	public static String html2Text(String html) {
		String text = html.replaceAll("&[lL][tT];","<").
			replaceAll("&[gG][tT];",">").
			replaceAll("\n","").
			replaceAll("<[tT][rR]>","\n").
			replaceAll("<[bB][rR]>","\n").
			replaceAll("<\\p{ASCII}*?>","");
		text = htmlEscapes2PlainText(text);
		return StringUtilities.trimMultipleSpaces(StringUtilities.trimSpacesAtEachLine(text));
	}

	/**
	 * Removes all characters except for upper and lower case a-z and digits.
	 * @param text
	 * @return
	 */
	public static String removeAllExceptAZLettersAndDigits(String text) {
		return text.replaceAll("[^\\p{Alnum}]","");
	}
	
	/**
	 * Removes all characters except for a-z letters.
	 * @param text
	 * @return
	 */
	public static String removeAllExceptAZLetters(String text) {
		return text.replaceAll("[^\\p{Alpha}]","");
	}
	
	/**
	 * Removes all characters except for digits.
	 * @param text
	 * @return
	 */
	public static String removeAllExceptDigits(String text) {
		return text.replaceAll("[^\\p{Digit}]","");
	}
	
	/**
	 * This method compares two strings for equality only considering us-ascii characters.
	 * I.e. it strips off eg. newlines and scandinavian characters from str1 and str2 before
	 * making the comparison.
	 * @param str1
	 * @param str2
	 * @return True if str1 and str2 are equal, otherwise false.
	 */
	public static boolean sloppyMatch(String str1, String str2) {
		if (str1 == null || str2 == null) {
			return false;
		}
		String s1 = replaceNewLinesWithSpace(removeAllExceptAZLettersAndDigits(str1)).trim();
		String s2 = replaceNewLinesWithSpace(removeAllExceptAZLettersAndDigits(str2)).trim();
		return s1.equalsIgnoreCase(s2);
	}
	
	/**
	 * This method expects an input string on the form "key1=value1,key2=value2".
	 */
	public static Map<String,String> keyValueArray2Map(String keyValueArray) {
		Map<String,String> map = new HashMap<String,String>();
		StringTokenizer keyValueST = new StringTokenizer(keyValueArray,",");
		while (keyValueST.hasMoreElements()) {
			String keyValue = keyValueST.nextToken();
			String key = keyValue.substring(0,keyValue.indexOf("="));
			String value = keyValue.substring(key.length()+1);
			if (map.get(key) != null) {
				throw new RuntimeException("Key " + key + " was found more than once.");
			}
			map.put(key, value);
		}
		return map;
	}
	
	public static String getStackTrace(Throwable t) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		t.printStackTrace(ps);
		ps.close();
		return new String(baos.toByteArray());
	}
	
	/**
	 * Prints a field of a static length cutting the contents nicely in case it doesn't
	 * fit within the specified length.
	 */
	public static void printField(int length, String contents, StringBuffer strBuf, boolean rightAligned) {
		if (contents.length() > length) {
			strBuf.append(contents.substring(0, (length-3)) + "...");
		} else {
			if (rightAligned) {
				for (int i = 0; i < (length - contents.length()); i++) {
					strBuf.append(" ");
				}
			}
			strBuf.append(contents);
			if (!rightAligned) {
				for (int i = 0; i < (length - contents.length()); i++) {
					strBuf.append(" ");
				}
			}
		}
	}
	
}
