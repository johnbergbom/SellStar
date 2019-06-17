package fi.jonix.huutonet.exception;

/**
 * We should quit from fillitems using an exception in order
 * to roll back the transaction and not persisting already filled
 * out information.
 */
public class FillItemsQuitException extends SellStarException {

	private static final long serialVersionUID = -6988229986793669012L;

	public FillItemsQuitException() {
		super(false);
	}
	
}
