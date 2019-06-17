package fi.jonix.huutonet.exception;

/**
 * When exceptions are thrown we need to let the calling method know if a
 * critical section of the code was executing. That information is conveyed
 * through this class (since java doesn't support methods returning more
 * than one value).
 * 
 * @author john
 *
 */
public class CriticalSectionInfo {

	private boolean inCriticalSection;
	private boolean transactionMustGoThrough;
	private boolean canProcessEmailOnlyOnce;

	public CriticalSectionInfo() {
		this.inCriticalSection = false;
		this.transactionMustGoThrough = false;
		this.canProcessEmailOnlyOnce = false;
	}
	
	public void setInCriticalSection(boolean inCriticalSection) {
		this.inCriticalSection = inCriticalSection;
	}

	public boolean isInCriticalSection() {
		return inCriticalSection;
	}

	public void setTransactionMustGoThrough(boolean transactionMustGoThrough) {
		this.transactionMustGoThrough = transactionMustGoThrough;
	}

	public boolean isTransactionMustGoThrough() {
		return transactionMustGoThrough;
	}

	public void setCanProcessEmailOnlyOnce(boolean canProcessEmailOnlyOnce) {
		this.canProcessEmailOnlyOnce = canProcessEmailOnlyOnce;
	}

	public boolean isCanProcessEmailOnlyOnce() {
		return canProcessEmailOnlyOnce;
	}
	
}
