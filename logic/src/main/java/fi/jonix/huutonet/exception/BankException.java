package fi.jonix.huutonet.exception;

/**
 * This exception automatically puts the program into the error state.
 * 
 * @author john
 *
 */
public class BankException extends SellStarException {

	private static final long serialVersionUID = 5905200023880794704L;

	public BankException(String message) {
		super(message,true);
	}
	
}
