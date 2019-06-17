package fi.jonix.huutonet.exception;

public class MarketLoggedOnAsWrongUserException extends SellStarException {

	private static final long serialVersionUID = 6837147972783685106L;

	public MarketLoggedOnAsWrongUserException() {
		super("",false);
	}
	
	public MarketLoggedOnAsWrongUserException(String message) {
		super(message,false);
	}
	
}
