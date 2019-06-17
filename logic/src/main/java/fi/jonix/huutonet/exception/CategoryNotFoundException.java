package fi.jonix.huutonet.exception;

public class CategoryNotFoundException extends SellStarException {

	private static final long serialVersionUID = -9021316778767559511L;
	
	public CategoryNotFoundException(String message) {
		super(message,false);
	}
	
}
