package fi.jonix.huutonet.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Category;

/**
 * Class for administering category levels. It's a minimal shell that uses CategoryHelper for actually
 * performing the commands.
 * 
 * @author john
 *
 */
public class CategoryAdmin {

	public static final Logger logger = Logger.getLogger(CategoryAdmin.class);
	
	private CategoryHelper categoryHelper;
	private PrintWriter out;
	
	public CategoryAdmin() {
		categoryHelper = (CategoryHelper) ApplicationContextPlaceholder.applicationContext.getBean("categoryHelper");
	}
	
	private void println(String text) {
		out.println(text);
		out.flush();
	}

	public Category run(boolean admin, BufferedReader in, PrintWriter out, String command) throws IOException {
		this.out = out;
		categoryHelper.setIn(in);
		categoryHelper.setOut(out);
		println("\"?\" or \"man\" for help");
		List<Category> currentDir = new ArrayList<Category>();
		Map<Integer,Category> currDirFiles = null;
		String input = null;
		
		/* Go straight to the SellStar directory. */
		if (!admin) {
			categoryHelper.changeDirectory(admin, "cd SellStar", currentDir);
			currDirFiles = categoryHelper.listFiles(admin, "ls", currentDir);
		}

		categoryHelper.printPrompt(currentDir);
		if (command != null) {
			input = command;
		} else {
			input = in.readLine();
		}
		while (input != null && !input.equals("exit")) {
			try {
				if (input.length() == 0) {
					//do nothing
				}/* else if (input.charAt(input.length()-1) == '\t') {
					println("tab pressed as last char");
					if (input.length() == 1) {
						printSubCategories(lastEntry(currentDir));
					}
				}*/ else if (input.equals("ls") || input.startsWith("ls ")) {
					currDirFiles = categoryHelper.listFiles(admin, input, currentDir);
				} else if (input.equals("cd") || input.startsWith("cd ")) {
					categoryHelper.changeDirectory(admin, input, currentDir);
				} else if (input.equals("use") || input.startsWith("use ")) {
					return categoryHelper.returnDirectory(admin, input, currentDir);
				} else if (input.startsWith("mkdir ")) {
					categoryHelper.makeDirectory(admin, input, currentDir);
				} else if (input.startsWith("rmdir ")) {
					categoryHelper.removeDirectory(admin, input, currentDir);
				} else if (input.startsWith("mv ")) {
					categoryHelper.moveDirectory(admin, input, currentDir);
				} else if (input.startsWith("cp ")) {
					categoryHelper.copyDirectory(admin, input, currentDir);
				} else if (input.startsWith("ren ")) {
					categoryHelper.renameDirectory(admin, input, currentDir);
				} else if (input.equals("find") || input.startsWith("find ")) {
					categoryHelper.findDirectories(admin, input, currentDir);
				} else if (admin && (input.equals("ln") || input.startsWith("ln "))) {
					categoryHelper.linkDirectories(admin, input, currentDir);
				} else if (admin && (input.equals("rmln") || input.startsWith("rmln "))) {
					categoryHelper.removeDirectoryLinks(admin, input, currentDir);
				} else if (admin && input.equals("link")) {
					categoryHelper.linkBatch(admin,currentDir);
				} else if (admin && input.equals("getmarketcategories")) {
					categoryHelper.getMarketCategories();
				} else if (admin && input.equals("updatemarketcategories")) {
					categoryHelper.updateMarketCategories(admin);
				} else if (admin && input.equals("getsnapshotcategories")) {
					categoryHelper.getSnapshotCategories();
				} else if (admin && input.startsWith("moveproducts ")) {
					categoryHelper.moveProducts(admin, input, currentDir);
				} else if (admin && input.equals("batch")) {
					categoryHelper.runBatch(admin, currentDir,false,false);
				} else if (admin && input.equals("createcategorysuggestions")) {
					categoryHelper.createCategorySuggestions();
				} else if (input.startsWith("man ")) {
					if (input.equals("man mkdir")) {
						printMkDirHelp(admin,out);
					} else if (admin && input.equals("man ln")) {
						printLnHelp(admin,out);
					} else if (admin && input.equals("man rmln")) {
						printRmLnHelp(admin,out);
					} else if (admin && input.equals("man link")) {
						printLinkHelp(admin,out);
					} else {
						throw new RuntimeException("man: manual page not found");
					}
				} else if (input.equals("?") || input.equals("man")) {
					printHelp(admin,out);
				} else {
					/* If running as a normal user, then see if the user tried to change directories by using a shortcut. */
					/*if (admin) {
						throw new RuntimeException(input + ": command not found");
					} else {*/
						int shortCutNbr = -1;
						try {
							shortCutNbr = Integer.parseInt(input);
						} catch (NumberFormatException nfe) {
						}
						if (shortCutNbr != -1) {
							if ((shortCutNbr != 0 && currDirFiles.get(shortCutNbr) == null)
									|| (shortCutNbr == 0 && currentDir.size() == 1)) {
								throw new RuntimeException("Shortcut " + input + " not found");
							}
							if (shortCutNbr == 0) {
								categoryHelper.changeDirectory(admin, "cd ..", currentDir);
							} else {
								categoryHelper.changeDirectory(admin, currDirFiles.get(shortCutNbr), currentDir);
							}
							Map<Integer,Category> currDirFilesTemp = categoryHelper.listFiles(admin, "ls", currentDir);
							if (currDirFilesTemp.isEmpty()) {
								categoryHelper.printPrompt(currentDir);
								boolean use = false;
								if (!admin) {
									use = UserInputHelper.getBoolean("Your hit a leaf. Use this leaf?", true, in);
								}
								if (use) {
									return currentDir.get(currentDir.size()-1);
								} else if (!admin) {
									categoryHelper.changeDirectory(admin, "cd ..", currentDir);
								}
							} else {
								currDirFiles.clear();
								currDirFiles.putAll(currDirFilesTemp);
							}
						} else {
							throw new RuntimeException(input + ": command not found");
						}
					//}
				}
			} catch (RuntimeException re) {
				if (re.getMessage() == null) {
					re.printStackTrace();
				} else {
					println(re.getMessage());
					logger.debug("Error running category admin command: ", re);
				}
			}
			if (command != null) {
				break;
			}
			categoryHelper.printPrompt(currentDir);
			input = in.readLine();
		}
		return null;
	}

	private void printLnHelp(boolean admin, PrintWriter out) {
		println("Syntax: ln directory directory");
	}
	
	private void printRmLnHelp(boolean admin, PrintWriter out) {
		println("Syntax:");
		println("rmln directory1 directory2 - removes the links between directory1 and directory2");
		println("rmln directory             - removes all links for directory");
	}
	
	private void printLinkHelp(boolean admin, PrintWriter out) {
		println("\"link\" is an interactive helper program that helps with linking several");
		println("SellStar categories with the corresponding categories at a different market");
		println("place. The changes are commited when you exit \"link\".");
	}
	
	private void printMkDirHelp(boolean admin, PrintWriter out) {
		if (admin) {
			println("Syntax: mkdir [-marketSpecId] directory_name");
		} else {
			println("Syntax: mkdir directory_name");
		}
		println("Spaces nor \"/\" are not allowed in directory names.");
	}
	
	private void printHelp(boolean admin, PrintWriter out) {
		println("This module is used for creating or modifying category structures.");
		println("Categories have the structure of a directory tree and unix syntax");
		println("is used for administering the categories. Each root directory");
		println("contains the categories of a different market. The following commands");
		println("are supported:");
		println("?                      - help");
		println("ls                     - lists files");
		println("cd                     - change directory");
		println("mkdir                  - creates directories");
		println("rmdir                  - removes directories");
		println("mv                     - moves a directory");
		println("ren                    - renames a directory");
		println("cp                     - copies a directory");
		println("use                    - use a directory");
		println("find                   - finds categories with similar names, syntax: \"find some_category_name\"");
		println("                         also wildcards are allowed, e.g. \"find some_*_name*\"");
		println("man command            - shows manual for \"command\"");
		if (admin) {
			println("ln                     - links categories between SellStar and another market place");
			println("rmln                   - removes category links between SellStar and another market place");
			println("link                   - helper program for linking several categories in one transaction");
			println("batch                  - executes commands in one transaction as a batch");
			println("commit                 - commits the batch to the database");
			println("getmarketcategories    - prints commands for creating categories for a certain market");
			println("updatemarketcategories - updates all current categories from a certain market");
			println("getsnapshotcategories  - gets all current categories from a certain market");
			println("moveproducts           - moves products from the chosen directory");
		}
		println("exit        - exits");
		println("");
		println("Note #1: there are a few differences between normal unix commands and");
		println("this simplified shell:");
		println("1.) Wildcards only work for the current directory, so something");
		println("    like \"ls ../*kortti*\" doesn't work. In order to get the");
		println("    same effect the following is needed: \"cd ..\" and then \"ls *kortti*\"");
		println("2.) In order to decrease typing normal unix uses the tab-character");
		println("    for expanding directory names. In this simplified shell the start");
		println("    of the filename is used as it is. For example if you want to go");
		println("    to the directory \"Tietotekniikka/PC-ohjelmat\", then it's enough to type");
		println("    \"cd T/P\". In case several directories start the same way");
		println("    then the command will fail (then you might need to type e.g. \"cd Tie/PC\").");
		println("3.) The \"mv\" command can only be used for moving directories and cannot be");
		println("    used for renaming files. For renaming the separate \"ren\" command is used.");
		println("");
		println("Note #2: For safety reasons it's not possible to move directories from");
		println("one market place to another.");
		println("");
		println("");
	}
	
}
