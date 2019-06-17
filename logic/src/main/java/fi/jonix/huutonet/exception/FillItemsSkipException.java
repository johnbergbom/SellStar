package fi.jonix.huutonet.exception;

/**
 * We should skip items in fillitems using an exception in order
 * to roll back the transaction and not persisting already filled
 * out information.
 */
public class FillItemsSkipException extends SellStarException {

	private static final long serialVersionUID = 9148924031452391895L;

	public FillItemsSkipException() {
		super(false);
	}
	
}
