package fi.jonix.huutonet.exception;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.ErrorTracking;
import fi.jonix.huutonet.domain.model.Settings;
import fi.jonix.huutonet.domain.model.dao.ErrorTrackingDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.StringUtilities;
//import fi.jonix.huutonet.email.EmailSender;

/**
 * Generic error handler that makes sure that the program won't start if errors have occurred.
 * Errors are handled commandwise. When the operator manually acknowledges the error the command
 * in question will start running normally again. This is done for two reasons:
 * 1.) Safety measure if the program contains a bug.
 * 2.) It won't spam the operator with emails telling that something is wrong, but rather only
 *     send an email at the first error.
 *     
 * For certain bad types of errors we set a global error state which prevents ALL commands from
 * starting.
 * 
 * Unacknowledged errors in any payment module prevents ALL payment modules to be run. The payment
 * modules are: fetchtransactions, makerefunds and transfertoqd.
 *     
 * Note: This is only used in the production environment. In the testing environment all commands
 * always run.
 * 
 * @author john
 *
 */
@Component(value = "errorHandler")
public class ErrorHandler {

	public static final Logger logger = Logger.getLogger(ErrorHandler.class);

	/* Normally we allow a certain amount of similar errors for a certain module
	 * before stopping the execution of the module altogether. I.e. errors for
	 * a specific module are grouped (and counted) by their type. However if we
	 * keep getting different errors each time for the same module, then we should
	 * bail out at some point although the maximum allowed error count for each
	 * error type is never exeeded.
	 * 
	 * If there are some unacknowledged errors but the number of errors for any
	 * specific type isn't great enough to trigger stopping of the module, then
	 * all those errors will automatically be acknowledged at the next successful
	 * run of the command in question (i.e. we assume the errors were just due
	 * to some temporary failure and needs no manual investigation). */
	public static int MAX_ALLOWED_COMMAND_EXCEPTIONS = 10;
	
	/**
	 * Tells how many times we should try to acknowledge errors before giving up
	 * (acknowledgement can fail in case of database connectivity problems).
	 */
	public static int MAX_ERROR_ACKNOWLEDGEMENT_ATTEMPTS = 4;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private ErrorTrackingDAO errorTrackingDAO;
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	private boolean productionEnvironment = true;
	private boolean databaseInitialized = false;
	
	public class SellStarFilenameFilter implements FilenameFilter {

		private String fileNameRegExp;
		
		public SellStarFilenameFilter(String fileNameRegExp) {
			this.fileNameRegExp = fileNameRegExp;
		}
		
		@Override
		public boolean accept(File dir, String fileName) {
			return fileName.matches(fileNameRegExp);
		}
		
	}
	
	/**
	 * @return True on two occasions:
	 * 1.) error.command = ErrorTracking.GLOBAL_ERROR,
	 * 2.) FATAL.txt was found.
	 * Otherwise false is returned.
	 */
	public boolean getGlobalError(String command) throws Exception {
		/* Don't start if FATAL.txt exists. For more information about the purpose of this
		 * file, see the method handleException. */
		boolean fatalError = false;
		
		// Set a filter for fetching FATAL-files. This one matches also FATAL.txt without any
		// ".module_name" (unlike the bash prompt wildcard matching)
		SellStarFilenameFilter filter = new SellStarFilenameFilter("FATAL.txt.*");
		File directory = new File(".");
		File[] fatalFiles = directory.listFiles(filter);
		
		if (fatalFiles.length > 0) {
			fatalError = true;
		}
		
		boolean inGlobalErrorState = true;
		boolean returnStateWhenNotFatalError = true;
		try {
			productionEnvironment = Boolean.parseBoolean(settingsDAO.getValue("productionEnvironment"));
			List<ErrorTracking> globalErrorList = errorTrackingDAO.getUnacknowledgedGlobalError();
			if (globalErrorList.size() == 0) {
				inGlobalErrorState = false;
			}
			if (!fatalError) {
				if (productionEnvironment) {
					returnStateWhenNotFatalError = inGlobalErrorState;
				} else {
					returnStateWhenNotFatalError = false;
				}
			}
			databaseInitialized = true;
		} catch (Exception e) {
			logger.error("Couldn't access database: ", e);
			return false;
		}

		if (!fatalError) {
			return returnStateWhenNotFatalError;
		}
		
		/* We get here if we have a fatal error. Then don't start SellStar at all. */
		if (!databaseInitialized) {
			/* If we couldn't reach the database, then it's not possible to do anything else
			 * than to put an error message in the log. */
			logger.error("Cannot start because FATAL.txt exists and no database connection was obtained. Please remove by hand.");
		} else {
			logger.info("FATAL.txt exists, but database connection obtained. Trying to set to error state + inform operator.");
			/* If we got database access, then we can inform the operator, set the database to
			 * the error state and finally remove FATAL.txt (it's no longer needed after the
			 * database is set to the error state). */
			//EmailSender.sendErrorReport("FATAL.txt found","Refusing to execute " + args[0] + " because FATAL.txt was found. Trying to fix automatically.");
			
			for (File fatalFile : fatalFiles) {
				boolean stopModule = false;
				/* Determine the command in question. */
				String errorCommand = fatalFile.getName().substring("FATAL.txt.".length());
				if (errorCommand.indexOf("." + ErrorTracking.MODULE_STOPPED) >= 0) {
					stopModule = true;
					errorCommand = errorCommand.substring(0,errorCommand.indexOf("." + ErrorTracking.MODULE_STOPPED));
				}
				
				/* Read the contents of FATAL.txt. */
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				FileInputStream fis = new FileInputStream(fatalFile);
				int b = fis.read();
				while (b != -1) {
					baos.write(b);
					b = fis.read();
				}
				fis.close();
				String contents = new String(baos.toByteArray());
				contents = StringUtilities.normalizer(contents);
				logger.debug("Old contents of " + fatalFile.getName() + ": " + contents);
				
				/* Set into error state, inform the operator and remove the FATAL.txt file. */
				if (!inGlobalErrorState && productionEnvironment) {
					ErrorTracking commandError = new ErrorTracking();
					commandError.setCommand(errorCommand);
					commandError.setDate(new Date(fatalFile.lastModified()));
					commandError.setMessage((stopModule ? ErrorTracking.MODULE_STOPPED : contents.split("\n")[0]));
					commandError.setStacktrace("FATAL.txt exists (" + fatalFile.getName() + "):\n\n" + contents);
					errorTrackingDAO.save(commandError);
				}
				if (stopModule) {
					EmailSender.sendErrorReport("FATAL.txt error detected for module " + errorCommand,fatalFile.getName()
							+ " was found (and removed) because SellStar was successfully"
							+ " set into the error state. Previous contents:\n\n" + contents);
				}
				fatalFile.delete();
			}
		}
		return true;
	}
	
	public boolean getCommandError(String command) throws Exception {
		List<ErrorTracking> errorList = errorTrackingDAO.getUnacknowledgedCommandError(command);
		logger.debug("getCommandError for " + command + ": errorList.size = " + errorList.size());
		for (String paymentErrorCommand : ErrorTracking.PAYMENT_ERROR_MODULES) {
			if (command.equals(paymentErrorCommand)) {
				errorList.addAll(errorTrackingDAO.getUnacknowledgedPaymentError());
				break;
			}
		}
		for (ErrorTracking errorTracking : errorList) {
			if (errorTracking.getMessage().equals(ErrorTracking.MODULE_STOPPED)) {
				return true;
			}
		}
		/*if (errorList.size() > MAX_ALLOWED_COMMAND_EXCEPTIONS) {
			return true;
		}*/
		return false;
	}
	
	/* This method acknowledges all errors except for STOPPED-errors (those ones need
	 * to be taken care of by hand). */
	public void acknowledgeSuccessfulRun(String command) throws Exception {
		List<ErrorTracking> commandErrorList = errorTrackingDAO.getUnacknowledgedCommandError(command);
		logger.debug("acknowledgeSuccessfulRun for " + command + ": commandErrorList.size = " + commandErrorList.size());
		if (commandErrorList.size() > 0) {
			logger.info("Command " + command + " had " + commandErrorList.size() + " errors."
					+ " Automatically acknowledging them since a successful run was detected.");
			StringBuffer strBuf = new StringBuffer();
			int nbrAcknowledged = 0;
			for (ErrorTracking error : commandErrorList) {
				if (error.getAcknowledged() == null && !error.getMessage().equals(ErrorTracking.MODULE_STOPPED)) {
					logger.debug("Error " + error.getId() + " acknowledged automatically (" + error.getMessage() + ").");
					error.setAcknowledged(new Date());
					errorTrackingDAO.update(error);
					//strBuf.append(error.getDate() + ":\n" + error.getStacktrace() + "\n\n\n--------------\n\n\n");
					strBuf.append(error.getDate() + ":" + error.getMessage() + "\n");
					nbrAcknowledged++;
				}
			}
			EmailSender.sendOperationSummaryReport("Automatically acknowledged errors","Acknowledging "
					+ nbrAcknowledged + " errors for " + command + ": " + strBuf.toString());
		}
	}
	
	private File getScreenshot() {
		try {
			File file = File.createTempFile("SellStarScreenshot_" + System.currentTimeMillis() + "_", null);
			logger.debug("Saving screenshot " + file.getName());
			CustomWebDriver driver = seleniumRobot.getWebDriver();
			File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			FileUtils.copyFile(screenshot, file);
			return file;
		} catch (Exception e) {
			logger.debug("Screenshot generation failed: ", e);
			return null;
		}
	}

	public void handleException(String[] args, Throwable e) {
		String command = args[0];
		String extraInfo = "";
		if (e instanceof SellStarRuntimeException) {
			SellStarRuntimeException se = (SellStarRuntimeException) e;
			logger.error("Error performing " + command + " (mainMessage = " + se.getMainMessage()
					+ ", extraInfo = " + se.getExtraInfo() + ", nbrAllowedErrors = " + se.getNbrAllowedErrors() + "): ", e);
			if (se.getExtraInfo() != null) {
				extraInfo += "extraInfo = " + se.getExtraInfo() + ", nbrAllowedErrors = " + se.getNbrAllowedErrors() + "\n\n";
			} else {
				extraInfo += "nbrAllowedErrors = " + se.getNbrAllowedErrors() + "\n\n";
			}
		} else {
			logger.error("Error performing " + command + " (" + e.getMessage() + "): ", e);
		}

		/* Check if the database can still be reached. */
		try {
			String dummy = settingsDAO.getValue("aaa" + MathUtilities.getRandomLongNumber(5));
		} catch (Throwable t) {
			logger.error("Database connection seems to be gone, can only store FATAL.txt: " + t.getMessage());
			String fileName = "FATAL.txt." + command + "." + ErrorTracking.MODULE_STOPPED;
			logger.error("VERY BAD ERROR: couldn't acknowledge error, creating " + fileName + ": ", t);
			File fatalFile = new File(fileName);
			try {
				PrintWriter pw = new PrintWriter(fatalFile);
				pw.println(StringUtilities.getStackTrace(e));
				pw.close();
				logger.info(fileName + " created");
			} catch (Exception e3) {
				logger.fatal("FATAL ERROR: couldn't create " + fileName + ": ", e3);
			}
			return;
		}
		
		/* Count the total number of unacknowledged errors of this kind. */
		int nbrSimilarErrors = 1; //1 since also the just occurred error needs to be counted
		List<ErrorTracking> errorList = errorTrackingDAO.getUnacknowledgedCommandError(command);
		for (ErrorTracking errorTracking : errorList) {
			if (errorTracking.getMessage().equals(e.getMessage())) {
				nbrSimilarErrors++;
			}
		}
		
		/* Count the total number of unacknowledged errors for this command
		 * (no matter what the type is). */
		int totalNbrOfCommandErrors = errorList.size();

		/* Count the total number of payment errors (if this is a payment command). */
		int nbrPaymentErrors = 0;
		for (String paymentErrorCommand : ErrorTracking.PAYMENT_ERROR_MODULES) {
			if (command.equals(paymentErrorCommand)) {
				nbrPaymentErrors = errorTrackingDAO.getUnacknowledgedPaymentError().size();
				break;
			}
		}
		logger.info("For command " + command + ": number of similar errors = " + nbrSimilarErrors + ", total number of errors for command = "
				+ totalNbrOfCommandErrors + ", number of payment errors = " + nbrPaymentErrors);
		
		/* If the allowed number of errors are greater than zero, then it's possible that we don't
		 * need to stop this module from executing. */
		int nbrAllowedErrors = 0; //default is zero allowed errors
		if (e instanceof SellStarRuntimeException) {
			SellStarRuntimeException se = (SellStarRuntimeException) e;
			nbrAllowedErrors = se.getNbrAllowedErrors();
		}
		boolean stopModule = true;
		if (nbrSimilarErrors <= nbrAllowedErrors) {
			logger.info("Module " + command + " is not yet stopped because number of similar errors ("
					+ nbrSimilarErrors + ") is less than the allowed limit (" + nbrAllowedErrors + ").");
			stopModule = false;
		}
		
		if (!stopModule && ((totalNbrOfCommandErrors + nbrPaymentErrors) > MAX_ALLOWED_COMMAND_EXCEPTIONS)) {
			logger.info("Command " + command + " is now stopped because the total amount of errors"
					+ " exceeds MAX_ALLOWED_COMMAND_EXCEPTIONS.");
			stopModule = true;
		}
		
		
		/* No error handling is necessary for getitems (it's necessary
		 * only for the automatized modules). */
		if (!command.equals("getitems")) {
			/* Extract screenshot from selenium. */
			File screenshotFile = getScreenshot();
			String errorMessage = extraInfo + StringUtilities.getStackTrace(e);
			if (screenshotFile != null) {
				errorMessage += "\n\nScreenshot: " + screenshotFile.getAbsolutePath();
			} else {
				errorMessage += "\n\nScreenshot: null";
			}

			/* Send email with information about the error. */
			boolean errorReportSent = !stopModule; //only send email to operator if we go to the error state
			int commandErrorSet = 0;
			int nbrAttempts = 0;
			while (true) {
				nbrAttempts++;
				try {
					if (!errorReportSent) {
						EmailSender.sendErrorReport("Error performing " + command,"Error performing " + command + ":"
								+ (stopModule ? "STOPPING execution of this module." : "NOT stopping execution of this module.")
								+ "\n\n" + errorMessage + "\n\n"
								+ "Number of similar unacknowledged errors for command: " + nbrSimilarErrors + ", total number of unacknowledged"
								+ " errors for command: " + totalNbrOfCommandErrors + ".\n\n");
						errorReportSent = true;
					}
					if (commandErrorSet == 0) {
						ErrorTracking commandError = new ErrorTracking();
						commandError.setCommand(command);
						commandError.setDate(new Date());
						if (stopModule) {
							commandError.setMessage(ErrorTracking.MODULE_STOPPED);
						} else if (e.getMessage() == null) {
							commandError.setMessage(e.toString());
						} else {
							commandError.setMessage(e.getMessage());
						}
						commandError.setStacktrace(errorMessage);
						errorTrackingDAO.save(commandError);
						commandErrorSet++;
					}
					break;
				} catch (Throwable t) {
					/* In case of database connectivity problems the acknowledgement of the error might fail. In that case sleep for some
					 * time and then try again. If acknowledging keeps failing, then resort to the last possibility: create FATAL.txt which
					 * which will set SellStar into the global error state once the database connection is back. */
					if (nbrAttempts < MAX_ERROR_ACKNOWLEDGEMENT_ATTEMPTS) {
						logger.info("Couldn't acknowledge error (likely database connectivity problems for "
								+ command + "). Sleeping for five minutes and then trying again.", t);
						try { Thread.sleep(1000*60*5); } catch (Exception e2) { }
						logger.info("Woke up from sleep (" + command + ").");
					} else {
						if (nbrAttempts > 1) {
							logger.error("Giving up acknowledging the error after trying " + nbrAttempts + " times.");
						}
						String fileName = "FATAL.txt." + command + (stopModule ? "." + ErrorTracking.MODULE_STOPPED : "");
						logger.error("VERY BAD ERROR: couldn't acknowledge error (nbrAttempts = " + nbrAttempts + "), creating " + fileName + ": ", t);
						File fatalFile = new File(fileName);
						try {
							PrintWriter pw = new PrintWriter(fatalFile);
							pw.println(errorMessage);
							pw.close();
							logger.info(fileName + " created");
						} catch (Exception e3) {
							logger.fatal("FATAL ERROR: couldn't create " + fileName + ": ", e3);
						}
						break;
					}
				}
			}
		}
	}

	public boolean getProductionEnvironment() {
		return productionEnvironment;
	}

	public boolean isDatabaseInitialized() {
		return databaseInitialized;
	}
	
}
