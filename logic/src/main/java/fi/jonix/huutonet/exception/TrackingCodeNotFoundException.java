package fi.jonix.huutonet.exception;

public class TrackingCodeNotFoundException extends SellStarException{

	private static final long serialVersionUID = -2634712081899429315L;
	
	public TrackingCodeNotFoundException(String message) {
		super(message,false);
	}

}
