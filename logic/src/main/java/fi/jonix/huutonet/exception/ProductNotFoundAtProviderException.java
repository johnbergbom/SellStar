package fi.jonix.huutonet.exception;

public class ProductNotFoundAtProviderException extends SellStarException {

	private static final long serialVersionUID = 7441324594889905204L;

	public ProductNotFoundAtProviderException(String message) {
		super(message,false);
	}
	
}
