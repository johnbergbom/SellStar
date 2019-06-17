package fi.jonix.huutonet.order;

import java.util.ArrayList;
import java.util.List;

import javax.mail.internet.InternetAddress;

import fi.jonix.huutonet.tools.StringUtilities;

public class ContactInfoChecker {
	
	/**
	 * 
	 * @param emailAddress
	 * @return True if valid email address, false if invalid.
	 */
	public static boolean correctEmailAddress(String emailAddress) {
		try {
			InternetAddress address = new InternetAddress(emailAddress,true);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Returns a formated string with contact information.
	 * 
	 * @param firstName
	 * @param lastName
	 * @param address
	 * @param postCode
	 * @param city
	 * @param email
	 * @return
	 */
	public static String formatContactInfo(String firstName, String lastName, String address, String postCode, String city, String email) {
		return "Etunimi: " + firstName + "\n"
			+ "Sukunimi: " + lastName + "\n"
			+ "Osoite: " + address + "\n"
			+ "Postinumero: " + postCode + "\n"
			+ "Kaupunki: " + city + "\n"
			+ "Sähköpostiosoite: " + email + "\n";
	}
	
	public static String fixAddress(String address) {
		if (address != null) {
			address = StringUtilities.trimMultipleSpaces(address.trim());
			if (address.trim().indexOf(" ") < 0) {
				/* No space in the address. This might be a faulty address, but far more
				 * common is that the user has simply written the address without a
				 * space between the street name and the street number. If there is
				 * a number within the string, then let's add a space right before
				 * the number. */
				if (StringUtilities.removeAllExceptAZLettersAndDigits(address).
						matches("\\p{Alpha}+\\p{Digit}+.*")) {
					//address starts with a string and then comes a number
					String firstNumber = address.replaceAll("[^\\p{Digit}]","").substring(0,1);
					String streetName = address.substring(0,address.indexOf(firstNumber));
					String endPart = address.substring(address.indexOf(firstNumber));
					address = streetName + " " + endPart;
				}
			}
		}
		return address;
	}
			
	public static String fixPostCode(String postCode) {
		if (postCode != null) {
			postCode = postCode.trim();
			if (postCode.indexOf(" ") > 0) {
				postCode.replaceAll(" ", "");
			}
		}
		return postCode;
	}
	
	public static String checkFirstName(String firstName) {
		return checkName(firstName);
	}
	
	public static String checkLastName(String lastName) {
		return checkName(lastName);
	}
	
	private static String checkName(String name) {
		String error = disallowEmpty(name);
		if (error != null) {
			return error;
		}
		error = disallowDollar(name);
		if (error != null) {
			return error;
		}
		String[] split = name.split(" ");
		if (split.length > 2) {
			return "weirdContentsOfField";
		}
		
		/* Some guy who ordered through Google-order set the following: firstName = "rfffgt" and
		 * lastName = "RRRRergu", so here we make sure that there cannot be more than two similar
		 * letters in a row. */
		char prevChar = name.charAt(0);
		int inARow = 1;
		for (int i = 1; i < name.length(); i++) {
			if (prevChar != ' ' && prevChar != '-' && prevChar == name.charAt(i)) {
				inARow++;
			} else {
				inARow = 1;
			}
			prevChar = name.charAt(i);
			if (inARow > 2) {
				return "weirdContentsOfField";
			}
		}

		return disallowDigits(name);
	}
	
	private static String disallowEmpty(String text) {
		if (text == null || text.trim().equals("")) {
			return "requiredField";
		}
		return null;
	}
	
	private static String disallowDollar(String text) {
		/* Check for dollar signs, because they screw up String.replaceAll(...). Dollar
		 * signs shouldn't be in a normal address anyway. */
		if (text != null && text.contains("$")) {
			return "fieldContainsIllegalCharacter";
		}
		return null;
	}
	
	private static String disallowDigits(String text) {
		if (text != null && text.matches(".*\\p{Digit}+.*")) {
			return "digitsNotAllowedInField";
		}
		return null;
	}
	
	private static String disallowNonDigits(String text) {
		if (text != null && !text.matches("\\p{Digit}+")) {
			return "onlyDigitsAllowedInField";
		}
		return null;
	}
	
	private static String requireDigits(String text) {
		if (text == null || !text.matches(".*\\p{Digit}+.*")) {
			return "digitsRequiredInField";
		}
		return null;
	}
	
	private static String disallowSpaces(String text) {
		if (text != null && text.indexOf(" ") >= 0) {
			return "spacesNotAllowedInField";
		}
		return null;
	}
	
	private static String checkCorrectLength(String text, Integer minLength, Integer maxLength) {
		if (minLength != null) {
			if (text == null || text.length() < minLength) {
				return "tooShortValueInField";
			}
		}
		if (maxLength != null) {
			if (text != null && text.length() > maxLength) {
				return "tooLongValueInField";
			}
		}
		return null;
	}
	
	public static String checkAddress(String address, String postCode, boolean fromGoogleOrder) {
		String error = disallowEmpty(address);
		if (error != null) {
			return error;
		}
		error = disallowDollar(address);
		if (error != null) {
			return error;
		}

		/* Normally we won't accept addresses longer than 35 characters (because that might mean we have some
		 * parsing error or else the address of the customer is seriously weird). However there are a few
		 * cases of real addresses that are longer so let's make a special case for those. For example the
		 * following (correct) addresses have come:
		 * "redacted" (42 characters)
		 * "redacted2" (47 characters)
		 * "redacted3" (40 characters)
		 * 
		 * If these checks still determine that the address is faulty although it really is correct, then
		 * that can be handled manually by setting the state to "new out of stock" and then run housekeeping. */
		int maxLength = 35;
		if (address.indexOf("opisto") >= 0 || address.indexOf("korkeakoulu") >= 0) {
			maxLength = 47;
		}
		if (address.replaceAll(" +","").toUpperCase().indexOf("C/O") >= 0) {
			maxLength += 5;
		} else if (fromGoogleOrder) {
			maxLength += 5;
		}
		error = checkCorrectLength(address,null,maxLength);
		if (error != null) {
			return error;
		}

		// One customer had the address "redacted" and it was actually correct that it was without numbers...
		// However this is extremely rare, so this code still requires digits in the address.
		if (!address.replaceAll(" +","").toLowerCase().trim().equals("posterestante")) {
			error = requireDigits(address);
			if (error != null) {
				return error;
			}
		}
		
		// One guy had the following address: "redacted" (contains seven different "words").
		// For this case see above regarding "redacted".
		// => UPDATE: this guy ordered something again, so now allow up to seven different words.
		String[] split = address.split(" ");
		if (split.length > 7 || split.length < 2 || address.toLowerCase().indexOf("xxx") >= 0) {
			if (split.length < 2) {
				return "spaceMissingFromField";
			} else {
				return "weirdContentsOfField";
			}
		}
		if (postCode != null && postCode.trim().length() > 0 && address.indexOf(postCode) >= 0) {
			//address contains the postcode
			return "weirdContentsOfField";
		}
		//Don't check for city inside of the address, because there can be Tampereentie in Tampere...
		/*if (city != null && city.trim().length() > 0 && address.indexOf(city) >= 0) {
			strBuf.append("Outo osoite: katuosoite näyttää sisältävän myös kaupunki.\n");
			faultyContactInfo = true;
		}*/
		return null;
	}
	
	public static String checkPostCode(String postCode) {
		String error = disallowEmpty(postCode);
		if (error != null) {
			return error;
		}
		error = disallowDollar(postCode);
		if (error != null) {
			return error;
		}
		error = disallowNonDigits(postCode);
		if (error != null) {
			return error;
		}
		error = checkCorrectLength(postCode,5,5);
		if (error != null) {
			return error;
		}
		if (postCode.indexOf("00000") >= 0) {
			return "weirdContentsOfField";
		}
		return null;
	}
	
	public static String checkCity(String city) {
		String error = disallowEmpty(city);
		if (error != null) {
			return error;
		}
		error = disallowDollar(city);
		if (error != null) {
			return error;
		}
		error = disallowDigits(city);
		if (error != null) {
			return error;
		}
		if (city.contains("_") || city.toUpperCase().indexOf("FINLAND") >= 0) {
			//This is added because some dummy put "Tampere_FINLAND" as city...
			return "fieldContainsIllegalCharacter";
		}
		return disallowSpaces(city);
	}
	
	public static String checkEmail(String email) {
		String error = disallowEmpty(email);
		if (error != null) {
			return error;
		}
		error = disallowDollar(email);
		if (error != null) {
			return error;
		}
		if (!correctEmailAddress(email)) {
			return "disallowedValue";
		}
		return null;
	}
	
	public static class AddressFault {
		private String field;
		private String error;
		public AddressFault(String field, String error) {
			this.setField(field);
			this.setError(error);
		}
		public void setField(String field) {
			this.field = field;
		}
		public String getField() {
			return field;
		}
		public void setError(String error) {
			this.error = error;
		}
		public String getError() {
			return error;
		}
	}
	
	/**
	 * This method checks contact information for errors.
	 * 
	 * @param firstName
	 * @param lastName
	 * @param address
	 * @param postCode
	 * @param city
	 * @param email
	 * 
	 * @return String describing what was wrong, or if no errors found null is returned.
	 */
	public static List<AddressFault> checkContactInfo(String firstName, String lastName, String address,
			String postCode, String city, String email, boolean fromGoogleOrder) {
		List<AddressFault> list = new ArrayList<AddressFault>();
		//TODO: fix support for names on the form "Some Name c/o Some Name" (some@email.com).
		//Possibly this can be done using a fixNames()-method along the same lines as fixAddress() and fixPostCode()
		//where we set firstName ) = "Some" and lastName = "Name c/o Some Name". Or else firstName = "Some",
		//lastName = "Name" and address = "c/o Some Name Streetname 123".
		String error = checkFirstName(firstName);
		if (error != null) {
			list.add(new AddressFault("firstName",error));
		}
		error = checkLastName(lastName);
		if (error != null) {
			list.add(new AddressFault("lastName",error));
		}
		error = checkAddress(address,postCode,fromGoogleOrder);
		if (error != null) {
			list.add(new AddressFault("address",error));
		}
		error = checkPostCode(postCode);
		if (error != null) {
			list.add(new AddressFault("postCode",error));
		}
		error = checkCity(city);
		if (error != null) {
			list.add(new AddressFault("city",error));
		}
		error = checkEmail(email);
		if (error != null) {
			list.add(new AddressFault("email",error));
		}
		return list;
	}
	
}
