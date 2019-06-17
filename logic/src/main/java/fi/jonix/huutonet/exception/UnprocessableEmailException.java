package fi.jonix.huutonet.exception;

/**
 * This exception can be thrown from the email processors if the
 * email couldn't be processed.
 * 
 * @author john
 *
 */
public class UnprocessableEmailException extends SellStarException {

	private static final long serialVersionUID = 8412016516601215814L;

	public UnprocessableEmailException(String message) {
		super(message,false);
	}
	
}
