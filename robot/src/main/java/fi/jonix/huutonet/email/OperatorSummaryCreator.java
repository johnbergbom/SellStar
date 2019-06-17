package fi.jonix.huutonet.email;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.EmailDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.exception.SellStarRuntimeException;

@Component(value = "operatorSummaryCreator")
public class OperatorSummaryCreator {

	public static final Logger logger = Logger.getLogger(OperatorSummaryCreator.class);

	@Autowired
	private EmailDAO emailDAO;

	@Autowired
	private UserDAO userDAO;

	public void makeOperatorSummaryReport() {
		List<Email> pendingOperatorSummaryEntries = emailDAO.getEmailsPendingForSendingWithLowPriority();
		logger.info("Creating summary report for operator of " + pendingOperatorSummaryEntries.size() + " entries.");
		if (pendingOperatorSummaryEntries.size() == 0) {
			return;
		}
		
		/* Set all operator emails in a hashtable so we can quickly check
		 * if the intented receiver is an operator. */
		Map<String,String> operators = new HashMap<String,String>();
		for (User oper : userDAO.getOperators()) {
			operators.put(oper.getEmailAddress(),"");
		}

		/* Sort the emails according to the subject line. */
		Map<String,List<Email>> orderedBySubject = new HashMap<String,List<Email>>();
		for (Email summaryEntry : pendingOperatorSummaryEntries) {
			summaryEntry.setDirection(Email.OUT_SENT);
			
			/* Make sure that these are all to an operator. */
			if (operators.get(summaryEntry.getReceiver()) == null) {
				logger.error("Can only send operator summaries to operators, "
						+ summaryEntry.getReceiver() + " isn't operator (email " + summaryEntry.getId() + ")");
				throw new SellStarRuntimeException("Can only send operator summaries to operators",
						summaryEntry.getReceiver() + " isn't operator (email " + summaryEntry.getId() + ")", 0);
			}
			
			/* Add the entry to the list of emails having the same subject. */
			if (orderedBySubject.get(summaryEntry.getSubject()) == null) {
				orderedBySubject.put(summaryEntry.getSubject(), new ArrayList<Email>());
			}
			List<Email> emailList = orderedBySubject.get(summaryEntry.getSubject());
			emailList.add(summaryEntry);
		}
		
		/* Create the summary report. */
		DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
		StringBuffer contents = new StringBuffer();
		Iterator<String> iter = orderedBySubject.keySet().iterator();
		while (iter.hasNext()) {
			String subject = iter.next();
			contents.append(subject + "\n");
			contents.append("--------------\n");
			
			/* Count how many times each entry was found. */
			Map<String,Integer> nbrIdenticalEntries= new HashMap<String,Integer>();
			List<Email> entries = orderedBySubject.get(subject);
			for (Email entry : entries) {
				if (nbrIdenticalEntries.get(entry.getContents()) == null) {
					nbrIdenticalEntries.put(entry.getContents(), new Integer(0));
				}
				int oldValue = nbrIdenticalEntries.get(entry.getContents());
				nbrIdenticalEntries.put(entry.getContents(), oldValue+1);
			}
			
			/* Only add once indentical entries that exist multiple times,
			 * but add the number in front of them (if there are more than one). */
			Map<String,String> alreadyAdded = new HashMap<String,String>();
			for (Email entry : entries) {
				if (alreadyAdded.get(entry.getContents()) == null) {
					//contents.append(timeFormat.format(entry.getDate()));
					if (nbrIdenticalEntries.get(entry.getContents()) > 1) {
						contents.append("Number of the following: " + nbrIdenticalEntries.get(entry.getContents()) + " was found");
					} else {
						contents.append("The following came at " + timeFormat.format(entry.getDate()));
					}
					contents.append(":\n");
					contents.append(entry.getContents() + "\n\n");
					alreadyAdded.put(entry.getContents(),"");
				}
			}
			contents.append("\n\n\n");
		}
		
		//DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		EmailSender.sendOperationProblemReport("Operator summary report",contents.toString());
	}
	
}
