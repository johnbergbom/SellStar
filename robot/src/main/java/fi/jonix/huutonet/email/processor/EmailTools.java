package fi.jonix.huutonet.email.processor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;

import com.sun.mail.util.BASE64DecoderStream;

import fi.jonix.huutonet.tools.StringUtilities;

public class EmailTools {

	private static final Logger logger = Logger.getLogger(EmailTools.class);

	private static StringBuffer extractFromMultipart(String mimeType, Multipart multipart) throws Exception {
		StringBuffer strBuf = new StringBuffer();
		for (int i = 0; i < multipart.getCount(); i++) {
			BodyPart bodyPart = multipart.getBodyPart(i);
			try {
				if (bodyPart.isMimeType(mimeType) && bodyPart.getContent() instanceof String) {
					String content = (String) bodyPart.getContent();
					if (content != null) {
						strBuf.append(content);
					}
				} else if (bodyPart.getContent() instanceof MimeMultipart) {
					strBuf.append(extractFromMultipart(mimeType,(MimeMultipart) bodyPart.getContent()));
				}
			} catch (UnsupportedEncodingException e) {
				logger.debug("Got a mime part having unsupported encoding: ", e);
				if (bodyPart.isMimeType(mimeType)) {
					logger.debug("Forcing US-ASCII since mime type is " + mimeType);
					/* Sometimes (very rarely) we get emails that are encoded in some encoding that's not supported
					 * by java (on 2011-11-17 we got a failed delivery notification for someuser@gmail.com where
					 * one of the mime parts were encoded using charset=unicode-1-1-utf-7). In these cases force
					 * US-ASCII. => On 2012-07-11 we got another one from someuser2@gmail.com. */
					DataHandler dh = bodyPart.getDataHandler();
					BufferedReader br = new BufferedReader(new InputStreamReader(dh.getInputStream(),Charset.forName("US-ASCII")));
					String line = br.readLine();
					while (line != null) {
						strBuf.append(line + "\n");
						line = br.readLine();
					}
				} else {
					logger.debug("Skipping this mime part since it has the wrong mime type.");
				}
			}
		}
		return strBuf;
	}
	
	public static String getPlainTextContents(Message message) throws Exception {
		Object content = message.getContent();
		if (message.isMimeType("text/plain") && content instanceof String) {
			logger.debug("Received a straight text/plain");
			return StringUtilities.fixNewLinesToUnix((String) content);
		} else if (content instanceof BASE64DecoderStream) {
			logger.debug("Received a BASE64DecoderStream");
			/* Some email clients send emails as BASE64 encoded RTF streams. This
			 * takes care of those cases. We'll get all the RTF tags as well so it looks
			 * quite ugly but it's at least readable. These types of emails
			 * come very seldom.
			 * TODO: handle these using some class from the javax.swing.text.rtf package. */
			StringBuffer strBuf = new StringBuffer();
			BufferedReader br = new BufferedReader(new InputStreamReader((BASE64DecoderStream)content));
			String line = br.readLine();
			while (line != null) {
				//logger.debug("line = " + line);
				strBuf.append(line + "\n");
				line = br.readLine();
			}
			return strBuf.toString();
		} else if (content instanceof Multipart) {
			StringBuffer textContents = extractFromMultipart("text/plain", (Multipart) content);
			if (textContents != null && !textContents.toString().trim().equals("")) {
				logger.debug("Received a multipart text/plain");
				return StringUtilities.htmlEscapes2PlainText(textContents.toString().trim());
			}
			textContents = extractFromMultipart("text/html", (Multipart) content);
			if (textContents != null && !textContents.toString().trim().equals("")) {
				logger.debug("Received a multipart text/html");
				return StringUtilities.html2Text(textContents.toString().trim());
			}
			logger.debug("Couldn't extract any text from this email. Seems to be an empty email.");
			return "";
		} else if (message.isMimeType("text/html") && content instanceof String) {
			/* Sometimes we get emails where the content part is just a string in html format (comes quite rarely). */
			logger.debug("Received a straight text/html");
			return StringUtilities.html2Text((String) content);
		}
		
		logger.error("Unable to understand structure of multipart message (subject = \"" + message.getSubject() + "\").");
		throw new Exception("Unable to understand structure of multipart message.");
	}
	
}
