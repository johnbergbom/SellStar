package fi.jonix.huutonet.exception;

/**
 * This exception can be thrown from the email processors if the
 * email couldn't be processed but it is expected that a later
 * processing will succeed.
 * 
 * @author john
 *
 */
public class ProcessEmailLaterException extends SellStarException {

	private static final long serialVersionUID = 2140321112346465491L;

	public ProcessEmailLaterException(String message) {
		super(message,false);
	}
	
}
