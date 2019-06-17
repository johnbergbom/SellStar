package fi.jonix.huutonet.exception;

public class ProviderTemporarilyDownException extends SellStarException {

	private static final long serialVersionUID = -3031611520912152763L;

	public ProviderTemporarilyDownException(String message) {
		super(message,false);
	}
	
}
