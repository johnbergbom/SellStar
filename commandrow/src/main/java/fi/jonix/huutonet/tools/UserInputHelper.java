package fi.jonix.huutonet.tools;

import java.io.BufferedReader;
import java.io.IOException;

public class UserInputHelper {

	/**
	 * Reads one integer from the user.
	 * 
	 * @param prompt Prompt to present to the user.
	 * @param minVal Number has to be at least minVal, or no lower restriction if minVal is null. 
	 * @param maxVal Number has to be at most maxVal, or no upper restriction if maxVal is null.
	 * @param allowEmpty Should empty value (=enter pressed) be allowed?
	 * @param allowQ Should 'q' be allowed?
	 * @param br Buffered reader to use.
	 * @return Number that the user chose, or null if allowEmpty == true and the user just pressed
	 * enter or -999 if the user pressed 'q' and allowQ == true.
	 */
	public static Integer getOneNumber(String prompt, Integer minVal, Integer maxVal, boolean allowEmpty, boolean allowQ, BufferedReader br) {
		try {
			while (true) {
				System.out.print(prompt + ": ");
				String input = br.readLine();
				if (input.trim().equals("") && allowEmpty) {
					return null;
				}
				if (input.trim().equalsIgnoreCase("q") && allowQ) {
					return -999;
				}
				int val = -1;
				try {
					val = Integer.parseInt(input);
				} catch (NumberFormatException nfe) {
					System.out.println("Faulty input. Please enter a number.");
					continue;
				}
				if (minVal != null && val < minVal.intValue()) {
					System.out.println("Faulty input. Number has to be at least " + minVal);
					continue;
				}
				if (maxVal != null && val > maxVal.intValue()) {
					System.out.println("Faulty input. Number has to be at the most " + maxVal);
					continue;
				}
				return val;
			}
		} catch (IOException e) {
			System.out.println("IOException received. Exiting program.");
			e.printStackTrace();
			System.exit(10);
		}
		return -999;
	}

	/**
	 * Reads a boolean from the user.
	 * 
	 * @param prompt Prompt to present to the user.
	 * @param defaultValue Tells which one will be chosen, true or false, if the user just presses enter,
	 * or if defaultValue == null, then just pressing enter isn't allowed.
	 * @param br Buffered reader to use.
	 * @return True or false, depending on the user's choice.
	 */
	public static boolean getBoolean(String prompt, Boolean defaultValue, BufferedReader br) {
		try {
			while (true) {
				System.out.print(prompt + " ");
				if (defaultValue != null) {
					if (defaultValue == true) {
						System.out.print("[Y/n]");
					} else {
						System.out.print("[y/N]");
					}
				} else {
					System.out.print("[y/n]");
				}
				System.out.print(": ");
				String input = br.readLine();
				if (input.equals("")) {
					if (defaultValue != null) {
						return defaultValue;
					}
					continue;
				} else if (input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes")) {
					return true;
				} else if (input.equalsIgnoreCase("n") || input.equalsIgnoreCase("no")) {
					return false;
				}
				System.out.println("Faulty input. Please enter \"y\" or \"n\".");
			}
		} catch (IOException e) {
			System.out.println("IOException received. Exiting program.");
			e.printStackTrace();
			System.exit(10);
		}
		return false;
	}

}
