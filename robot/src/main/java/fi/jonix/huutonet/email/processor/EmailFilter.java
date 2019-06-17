package fi.jonix.huutonet.email.processor;

import java.util.Scanner;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.email.EmailProcessor;
import fi.jonix.huutonet.tools.StringUtilities;

public abstract class EmailFilter implements EmailProcessor {

	private static final Logger logger = Logger.getLogger(EmailFilter.class);
	
	/**
	 * Gets the value of a certain field in the email. The value
	 * is the very next word following the field name.
	 * 
	 * @param contents
	 * @param field
	 * @return
	 */
	public static String getFieldValue(String contents, String fieldName) {
		return getFieldValue(contents, fieldName, 1);
	}

	public static String getFieldValue(String contents, String fieldName, String delimiter) {
		return getFieldValue(contents, fieldName, 1, delimiter);
	}
        
	public static String getFieldValue(String contents, String fieldName, int fieldNumber) {
		return getFieldValue(contents, fieldName, fieldNumber, null);
	}

	/**
	 * Gets the value of a certain field in the email. The value
	 * is the fieldNumber:th next word following the field name
	 * (1 is the next word, 2 is the second next word, etc.).
	 * 
	 * @param contents
	 * @param field
	 * @return
	 */
	public static String getFieldValue(String contents, String fieldName, int fieldNumber, String delimiter) {
		if (fieldNumber < 1) {
			throw new RuntimeException("fieldNumber needs to be at least 1");
		}
		Scanner scanner = new Scanner(StringUtilities.replaceNewLinesWithSpace(contents));
		if (delimiter != null) {
			scanner.useDelimiter(delimiter);
		}
		try {
			if (scanner.findWithinHorizon(fieldName,50000) != null) {
				for (int i = 1; i < fieldNumber; i++) {
					scanner.next();
				}
				return scanner.next();
			}
		} catch (Exception e) {
			logger.warn("Couldn't fetch value for field " + fieldName + " (at position " + fieldNumber + "): " + e);
			return null;
		} finally {
			scanner.close();
		}
		return null;
	}

	/**
	 * Gets the row value of a certain field in the email. The row value
	 * is the very next word following the field name stretching until
	 * the end of the line, or null if the field wasn't found.
	 * 
	 * @param contents
	 * @param field
	 * @return
	 */
	protected String getFieldValueToEndOfRow(String contents, String field) {
		int index = contents.indexOf(field);
		if (index < 0) {
			return null;
		}
		String str = contents.substring(index + field.length());
		int newRowIndex = str.indexOf("\n");
		if (newRowIndex < 0) {
			return str;
		}
		return str.substring(0,newRowIndex);
	}

	protected String getTagValue(String text, String field) {
		int startIndex = text.indexOf("<" + field + ">");
		int endIndex = text.indexOf("</" + field + ">");
		if (startIndex < 0 || endIndex < 0) {
			return null;
		}
		String rawContent = text.substring(startIndex + ("<" + field + ">").length(),endIndex);
		
		/* If the string has multiple rows, then let's remove the lines starting with ">"
		 * (because they are usually added automatically when replying to an email). */
		String[] lines = rawContent.split("\n");
		String content = "";
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			while (line.startsWith(">")) {
				line = line.substring(1);
			}
			content += line.trim();
			if (i < lines.length - 1) {
				content += "\n";
			}
		}
		return content;
	}
	
	/**
	 * Gets the value of a certain row starting rowNbr rows below the specified field.
	 * 
	 * @param contents
	 * @param field
	 * @param rowNbr
	 * @return
	 */
	protected String getRowValue(String contents, String field, int rowNbr) throws Exception {
		if (rowNbr < 1) {
			throw new Exception("Internal error, faulty rowNbr");
		}
		StringBuffer strBuf = new StringBuffer();
		int index = contents.indexOf(field);
		int i = 0;
		int newLinesFound = 0;
		if (index >= 0) {
			while (index + i < contents.length()) {
				char ch = contents.charAt(index+i);
				if (newLinesFound == rowNbr) {
					if (ch != '\n' && ch != '\r') {
						strBuf.append(ch);
					}
				}
				if (ch == '\n') {
					if (newLinesFound == rowNbr) {
						break; //stop when we hit the second new line
					}
					newLinesFound++;
				}
				i++;
			}
			return strBuf.toString().trim();
		}
		return null;
	}

	/**
	 * 
	 * @param message
	 * @param senderAddress
	 * @param subjectStart
	 * @param strictSubjectStart If true, then match from the very start of the subject, otherwise there can be some characters before the
	 * subjectStart, such as "Fw: subjectStart". 
	 * @param identificationString
	 * @return True if the message matches the given parameters.
	 * @throws Exception
	 */
	protected String matchEmail(Message message, String senderAddress, String subjectStart, boolean strictSubjectStart,
			String identificationString) throws Exception {
		return matchEmail(message, senderAddress, subjectStart, strictSubjectStart, false, identificationString);
	}
	
	protected String matchEmail(Message message, String senderAddress, String subjectStart, boolean strictSubjectStart,
			boolean ignoreSubjectCase, String identificationString) throws Exception {
		if (senderAddress == null) {
			return null;
		}
		InternetAddress sender = (InternetAddress) message.getFrom()[0];
		if (senderAddress.equalsIgnoreCase(sender.getAddress()) || (senderAddress.startsWith("@")
				&& sender.getAddress().toLowerCase().indexOf(senderAddress.toLowerCase()) >= 0)) {
			String subject = message.getSubject();
			int index = -1;
			if (subject != null) {
				//index = subject.toLowerCase().indexOf(subjectStart.toLowerCase());
				if (ignoreSubjectCase) {
					index = subject.toLowerCase().indexOf(subjectStart.toLowerCase());
				} else {
					index = subject.indexOf(subjectStart);
				}
			}
			//logger.debug("subject of email = " + subject + ", subject to match = " + subjectStart + ", index = " + index);
			if (index == 0 || (!strictSubjectStart && index > 0/* && index < 7*/)) {
				/* Finally check if the identification string matches. */
				String contents = EmailTools.getPlainTextContents(message);
				//logger.debug("contents = " + contents);
				if (identificationString == null || identificationString.trim().equals("")) {
					logger.debug("email matched");
					return contents;
				}
				if (matchEmailContents(contents,identificationString)) {
					logger.debug("email matched");
					return contents;
				}
				logger.debug("email didn't match");
			}
		} else {
			//logger.debug("sender = \"" + sender.getAddress() + "\", sender to match = \"" + senderAddress + "\"");
			return null;
		}
		return null;
	}
	
	/**
	 * This returns true if idString was found in contents. Note that this method
	 * is able to find matches also across new lines (unlike String.indexOf()).
	 * 
	 * @param contents Contents of email
	 * @param idString String to match
	 * @return
	 */
	protected boolean matchEmailContents(String contents, String idString) {
		/* Remove all scandinavian characters before making the comparison
		 * with the idString, just to make sure that we won't
		 * get any ääkkös-problems. Also we need to replace newline characters
		 * with spaces. */
		String contentsPlain = StringUtilities.replaceNewLinesWithSpace(StringUtilities.removeNonUSASCIICharacters(contents));
		String idStringPlain = StringUtilities.removeNonUSASCIICharacters(idString);
		//logger.debug("contentsPlain = " + contentsPlain);
		//logger.debug("idStringPlain = " + idStringPlain);
		if (contentsPlain.indexOf(idStringPlain) != -1) {
			return true;
		}
		return false;
	}
	
}
