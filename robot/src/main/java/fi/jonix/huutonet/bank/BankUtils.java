package fi.jonix.huutonet.bank;

public class BankUtils {

	public static String getSecurityCode(String code) {
		int sum = 0;
		int[] multiplier = new int[] { 7, 3, 1 };
		for (int i = 0; i < code.length(); i++) {
			sum += Integer.parseInt(code.charAt(code.length() - i - 1) + "")
					* multiplier[i % 3];
		}
		int checkNumber = ((sum % 10) - 10) % 10 * -1;
		return code + checkNumber;
	}

}
