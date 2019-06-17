package fi.jonix.huutonet.exception;

public class MarketTemporarilyDownException extends SellStarException {

	private static final long serialVersionUID = 7536544926142940634L;

	public MarketTemporarilyDownException(String message) {
		super(message,false);
	}
	
}
