package fi.jonix.huutonet.exception;

public class SoldOutAtProviderException extends SellStarException {

	private static final long serialVersionUID = -2471116325025240089L;

	public SoldOutAtProviderException(String message) {
		super(message,false);
	}
	
}
