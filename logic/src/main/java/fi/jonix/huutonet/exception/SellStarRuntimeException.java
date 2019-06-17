package fi.jonix.huutonet.exception;

public class SellStarRuntimeException extends RuntimeException {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3132771955690338580L;

	/**
	 * This one tells broadly what the error is about, for example "Too many errors in updateStatusOfOrders".
	 */
	private String mainMessage;
	
	/**
	 * This one gives more details about the error (if any). For example "nbrFailed=38,nbrSucceeded=96".
	 */
	private String extraInfo;
	
	/**
	 * This one tells how many of these errors (taking into account only mainMessage and not extraInfo)
	 * we can accept before stopping the running of the module in question.
	 */
	private int nbrAllowedErrors;

	public SellStarRuntimeException(String mainMessage, String extraInfo, int nbrAllowedErrors) {
		super(mainMessage);
		this.mainMessage = mainMessage;
		this.extraInfo = extraInfo;
		this.nbrAllowedErrors = nbrAllowedErrors;
	}
	
	public SellStarRuntimeException(String mainMessage, String extraInfo, int nbrAllowedErrors, Throwable cause) {
		super(mainMessage,cause);
		this.mainMessage = mainMessage;
		this.extraInfo = extraInfo;
		this.nbrAllowedErrors = nbrAllowedErrors;
	}
	
	public void setMainMessage(String mainMessage) {
		this.mainMessage = mainMessage;
	}

	public String getMainMessage() {
		return mainMessage;
	}

	public void setExtraInfo(String extraInfo) {
		this.extraInfo = extraInfo;
	}

	public String getExtraInfo() {
		return extraInfo;
	}

	public void setNbrAllowedErrors(int nbrAllowedErrors) {
		this.nbrAllowedErrors = nbrAllowedErrors;
	}

	public int getNbrAllowedErrors() {
		return nbrAllowedErrors;
	}
	
	
}
