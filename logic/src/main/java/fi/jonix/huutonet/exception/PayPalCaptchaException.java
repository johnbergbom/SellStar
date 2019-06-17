package fi.jonix.huutonet.exception;

public class PayPalCaptchaException extends SellStarException {

	private static final long serialVersionUID = 1384412087288897624L;

	public PayPalCaptchaException(String message) {
		super(message,false);
	}
	
}
