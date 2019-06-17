package fi.jonix.huutonet.exception;

public abstract class SellStarException extends Exception {
	
	private static final long serialVersionUID = 6670656528333840913L;
	protected Boolean toErrorState;
	
	public SellStarException() {
		super();
		toErrorState = true;
	}

	public SellStarException(String message) {
		super(message);
		toErrorState = true;
	}

	public SellStarException(String message, Throwable cause) {
		super(message,cause);
		toErrorState = true;
	}

	public SellStarException(Throwable cause) {
		super(cause);
		toErrorState = true;
	}

	public SellStarException(boolean toErrorState) {
		super();
		this.toErrorState = toErrorState;
	}

	public SellStarException(String message, boolean toErrorState) {
		super(message);
		this.toErrorState = toErrorState;
	}

	public SellStarException(String message, Throwable cause, boolean toErrorState) {
		super(message,cause);
		this.toErrorState = toErrorState;
	}

	public SellStarException(Throwable cause, boolean toErrorState) {
		super(cause);
		this.toErrorState = toErrorState;
	}
	
	public boolean isToErrorState() {
		if (toErrorState == null || toErrorState.booleanValue() == true) {
			return true;
		} else {
			return false;
		}
	}
	
	public void setToErrorState(boolean toErrorState) {
		this.toErrorState = toErrorState;
	}

}
