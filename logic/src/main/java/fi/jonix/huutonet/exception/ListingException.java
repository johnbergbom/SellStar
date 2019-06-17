package fi.jonix.huutonet.exception;

public class ListingException extends SellStarException {

	private static final long serialVersionUID = -4257399906905278187L;

	public ListingException(String message, boolean toErrorState) {
		super(message,toErrorState);
	}

}
