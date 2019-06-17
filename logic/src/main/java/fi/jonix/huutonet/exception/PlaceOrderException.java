package fi.jonix.huutonet.exception;

public class PlaceOrderException extends SellStarException {

	private static final long serialVersionUID = 7095576631546522211L;

	public PlaceOrderException(String message) {
		super(message);
	}

	public PlaceOrderException(String message, boolean toErrorState) {
		super(message,toErrorState);
	}

}
