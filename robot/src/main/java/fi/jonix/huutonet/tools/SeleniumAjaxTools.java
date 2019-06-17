package fi.jonix.huutonet.tools;

import fi.jonix.huutonet.robot.SeleniumRobot;

/*
 * TODO: Move this whole thing into the SeleniumRobot class!
 */
public class SeleniumAjaxTools {

	public static void waitUntilAjaxElementIsPresent(String ajaxElement, SeleniumRobot seleniumRobot) throws Exception {
		SeleniumAjaxTools.waitUntilAjaxElementIsPresent(ajaxElement, seleniumRobot, 60, 500);
	}

	public static void waitUntilAjaxElementIsPresent(String ajaxElement, SeleniumRobot seleniumRobot, int maxWaitTimeInSeconds,
			int intervalInMillSeconds) throws Exception {

		// Loop initialization.
		for (int second = 0; second < maxWaitTimeInSeconds; second++) {

			// Search for element {ajaxElement} and if available then break
			// loop.
			try {
				seleniumRobot.getSelenium().getText(ajaxElement);
				break;
			} catch (Exception e) {
			}

			Thread.sleep(intervalInMillSeconds);
		}

	}

}
