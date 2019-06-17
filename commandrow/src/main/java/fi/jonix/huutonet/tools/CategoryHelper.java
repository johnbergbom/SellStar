package fi.jonix.huutonet.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.CategoryMapping;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryMappingDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.order.ProviderHandler;

/**
 * Class for administering category levels.
 * 
 * @author john
 *
 */
@Component(value = "categoryHelper")
public class CategoryHelper {

	public static final Logger logger = Logger.getLogger(CategoryHelper.class);

	@Autowired
	private CategoryDAO categoryDAO;
	
	@Autowired
	private CategoryMappingDAO categoryMappingDAO;
	
	@Autowired
	private ProductDAO productDAO;

	@Autowired
	private List<MarketRobot> listers;

	@Autowired
	private MarketDAO marketDAO;

	@Autowired
	private AdDAO adDAO;

	@Autowired
	private AdTemplateDAO adTemplateDAO;

	@Autowired
	ProviderHandler providerHandler;
	
	@Autowired
	private SellerMarketDAO sellerMarketDAO;

	private BufferedReader in;
	private PrintWriter out;

	private void println(String text) {
		out.println(text);
		out.flush();
	}

	private void print(String text) {
		out.print(text);
		out.flush();
	}

	public void renameDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 4) {
			String[] split = command.substring(4).split(" ");
			if (split.length != 2) {
				throw new RuntimeException("ren: faulty parameters");
			} else {
				List<Category> fromList = parseDirectories(admin,currentDir,split[0]);
				String toName = split[1];
				if (toName.indexOf("/") >= 0 || toName.indexOf(" ") >= 0) {
					throw new RuntimeException("ren: directory names cannot contain \"/\" nor space");
				}
				if (fromList != null) {
					renameDir(lastEntry(fromList),toName);
				} else {
					throw new RuntimeException("ren: directory \"" + split[0] + "\" not found");
				}
			}
		} else {
			throw new RuntimeException("ren: directory name/new name missing");
		}
	}
	
	public void copyDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 3) {
			List<Category> sources = new ArrayList<Category>();
			List<Category> destination = new ArrayList<Category>();
			String error = extractOneOrManySourcesAndOneDestination(admin,command.substring(3), currentDir, sources, destination);
			if (error != null) {
				throw new RuntimeException("cp: " + error);
			} else if (sources.isEmpty()) {
				throw new RuntimeException("cp: the source is empty");
			} else if (destination.size() == 0) {
				throw new RuntimeException("cp: copying files to the root is not supported");
			} else if (destination.size() > 1) {
				throw new RuntimeException("cp: there are multiple destinations");
			} else {
				for (Category cat : sources) {
					if (categoryDAO.getChild(destination.get(0), cat.getName()) != null) {
						throw new RuntimeException("mv: \"" + cat.getName() + "\" already exists in target");
					}
				}
				for (Category cat : sources) {
					copyDirs(cat,destination.get(0));
				}
			}
		} else {
			throw new RuntimeException("cp: directory names missing");
		}
	}
	
	public void moveDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 3) {
			List<Category> sources = new ArrayList<Category>();
			List<Category> destination = new ArrayList<Category>();
			String error = extractOneOrManySourcesAndOneDestination(admin,command.substring(3), currentDir, sources, destination);
			if (error != null) {
				throw new RuntimeException("mv: " + error);
			} else if (sources.isEmpty()) {
				throw new RuntimeException("mv: the source is empty");
			} else if (destination.size() == 0) {
				throw new RuntimeException("mv: moving files to the root is not supported");
			} else if (destination.size() > 1) {
				throw new RuntimeException("mv: there are multiple destinations");
			} else {
				/* Make sure that the user didn't attempt to move directories
				 * from one market (=root directory) to another + make sure that
				 * a directory with the same name exists in the target. */
				for (Category cat : sources) {
					if (!cat.getMarket().getId().equals(destination.get(0).getMarket().getId())) {
						throw new RuntimeException("mv: moving files between markets is not allowed");
					}
					if (categoryDAO.getChild(destination.get(0), cat.getName()) != null) {
						throw new RuntimeException("mv: \"" + cat.getName() + "\" already exists in target");
					}
				}
				for (Category cat : sources) {
					moveDirs(cat,destination.get(0));
				}
			}
		} else {
			throw new RuntimeException("mv: directory names missing");
		}
	}
	
	public void linkDirectories(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 3) {
			List<Category> sources = new ArrayList<Category>();
			List<Category> destination = new ArrayList<Category>();
			String error = extractOneOrManySourcesAndOneDestination(admin,command.substring(3), currentDir, sources, destination);
			if (error != null) {
				throw new RuntimeException("ln: " + error);
			} else if (sources.isEmpty()) {
				throw new RuntimeException("ln: the source is empty");
			} else if (destination.size() == 0) {
				throw new RuntimeException("ln: linking to root is not supported");
			} else if (sources.size() > 1 || destination.size() > 1) {
				throw new RuntimeException("ln: there are multiple sources or destinations");
			} else {
				/* Only links between different markets are allowed (just a safety measure, because
				 * most likely the user won't want to do this, so if it happens, then it's probably
				 * a mistake from the user). */
				if (sources.get(0).getMarket().getId().equals(destination.get(0).getMarket().getId())) {
					throw new RuntimeException("ln: linking between files in the same market is not allowed");
				}
				if (categoryMappingDAO.getCategoryMapping(sources.get(0), destination.get(0)) != null) {
					throw new RuntimeException("ln: mapping already exists");
				}

				CategoryMapping cm = new CategoryMapping();
				cm.setCategory1(sources.get(0));
				cm.setCategory2(destination.get(0));
				categoryMappingDAO.save(cm);
				categoryMappingDAO.flush();
			}
		} else {
			throw new RuntimeException("ln: directory names missing");
		}
	}
	
	public void removeDirectoryLinks(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		int removals = 0;
		if (command.length() > 5) {
			if (command.substring(5).indexOf(" ") > 0) {
				List<Category> sources = new ArrayList<Category>();
				List<Category> destination = new ArrayList<Category>();
				String error = extractOneOrManySourcesAndOneDestination(admin,command.substring(5), currentDir, sources, destination);
				if (error != null) {
					throw new RuntimeException("rmln: " + error);
				} else if (sources.isEmpty()) {
					throw new RuntimeException("rmln: the source is empty");
				} else if (destination.size() == 0) {
					throw new RuntimeException("rmln: removal of links to root is not allowed");
				} else if (sources.size() > 1 || destination.size() > 1) {
					throw new RuntimeException("rmln: there are multiple sources or destinations");
				} else {
					CategoryMapping cm = categoryMappingDAO.getCategoryMapping(sources.get(0), destination.get(0));
					if (cm == null) {
						throw new RuntimeException("rmln: mapping didn't exist");
					}
					boolean canRemove = true;
					boolean restrictions = printCategoryMappingRestriction(sources.get(0),cm);
					if (restrictions && !admin) {
						throw new RuntimeException("rmln: link not removed");
					}
					if (restrictions) {
						canRemove = UserInputHelper.getBoolean("Remove link anyway?",null,in);
					}
					if (!canRemove) {
						throw new RuntimeException("rmln: link not removed");
					}
					categoryMappingDAO.delete(cm);
					categoryMappingDAO.flush();
					removals++;
				}
			} else {
				/* If just one directory is specified, then remove _all_ mappings for that directory. */
				List<Category> dirPath = parseDirectories(admin,currentDir,command.substring(5));
				List<CategoryMapping> mapList = categoryMappingDAO.getCategoryMappings(lastEntry(dirPath));
				if (mapList.isEmpty()) {
					throw new RuntimeException("rmln: " + extractPath(dirPath) + " has no mappings");
				}
				for (CategoryMapping mapping : mapList) {
					boolean canRemove = true;
					boolean restrictions = printCategoryMappingRestriction(lastEntry(dirPath),mapping);
					if (restrictions && !admin) {
						println("Not removing link");
						continue;
					}
					if (restrictions) {
						canRemove = UserInputHelper.getBoolean("Remove link anyway?",null,in);
					}
					if (!canRemove) {
						println("Not removing link");
						continue;
					}
					categoryMappingDAO.delete(mapping);
					categoryMappingDAO.flush();
					removals++;
				}
			}
		} else {
			throw new RuntimeException("rmln: directory names missing");
		}
		println("rmln: " + removals + " link(s) removed.");
	}
	
	public boolean printCategoryMappingRestriction(Category baseCategory, CategoryMapping cm) {
		boolean restrictions = false;
		Category c;
		if (cm.getCategory1().getId().equals(baseCategory.getId())) {
			c = cm.getCategory2();
		} else {
			c = cm.getCategory1();
		}
		List<Product> linkProds = productDAO.getProductsInCategory(c);
		if (!linkProds.isEmpty()) {
			println("  " + extractPath(getWholePathFromRootFor(baseCategory)) + " is mapped to " + extractPath(getWholePathFromRootFor(c))
					+ " and the latter has " + linkProds.size() + " products");
			restrictions = true;
		}
		List<Ad> linkAds = adDAO.getAdsByMarketCategory(c);
		if (!linkAds.isEmpty()) {
			println("  " + extractPath(getWholePathFromRootFor(baseCategory)) + " is mapped to " + extractPath(getWholePathFromRootFor(c))
					+ " and the latter has " + linkAds.size() + " ads mapped to it");
			restrictions = true;
		}
		return restrictions;
	}
	
	public void removeDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 6) {
			if (command.substring(6).indexOf("*") >= 0) { //if wildcards, then match for current directory
				removeChildCategories(admin,lastEntry(currentDir),command.substring(6));
			} else {
				removeChildCategory(admin,lastEntry(currentDir),command.substring(6));
			}
		} else {
			throw new RuntimeException("rmdir: directory name missing");
		}
	}
	
	public void makeDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (!admin && lastEntry(currentDir) == null) {
			throw new RuntimeException("No permission"); //only admin can make directories at the root
		} else if (command.length() > 6) {
			Integer marketSpecId = null;
			String fileName = command.substring(6);
			if (admin && command.charAt(6) == '-') { //admins can specify marketSpecId's
				if (lastEntry(currentDir).getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
					throw new RuntimeException("mkdir: cannot manually specify marketSpecId's for SellStar's categories");
				}
				if (command.length() > 8 && command.substring(7).indexOf(" ") > 0) {
					String[] split = command.substring(7).split(" ");
					try {
						marketSpecId = Integer.parseInt(split[0]);
					} catch (NumberFormatException nfe) {
						throw new RuntimeException("mkdir: faulty marketSpecId specified");
					}
					fileName = split[1];
				} else {
					throw new RuntimeException("mkdir: faulty filename");
				}
			}
			/*if (marketSpecId != null) {
				if (categoryDAO.getCategory(marketSpecId,lastEntry(currentDir).getMarket()) != null) {
					throw new RuntimeException("mkdir: this market already has a directory having this marketSpecId");
				}
			}*/
			if (fileName.indexOf("/") >= 0 || fileName.indexOf(" ") >= 0) {
				throw new RuntimeException("mkdir: directory names cannot contain \"/\" nor space");
			} else {
				createNewChildCategory(admin,lastEntry(currentDir),fileName,marketSpecId);
			}
		} else {
			throw new RuntimeException("mkdir: directory name missing");
		}
	}
	
	public void changeDirectory(boolean admin, Category toCategory, List<Category> currentDir) {
		categoryDAO.refresh(toCategory);
		currentDir.clear();
		currentDir.addAll(getWholePathFromRootFor(toCategory));
	}
	
	public void changeDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 3) {
			List<Category> catList = parseDirectories(admin,currentDir,command.substring(3));
			if (catList != null) {
				currentDir.clear();
				currentDir.addAll(catList);
			}
		} else {
			throw new RuntimeException("cd: directory name missing");
		}
	}
	
	public void findDirectories(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.length() > 5) {
			String searchStr = command.substring(5).replaceAll("\\*","%").replaceAll("_", "\\\\_");
			List<Category> list = categoryDAO.getCategoriesContainingNamePart("%" + searchStr + "%",true);
			TreeSet<String> treeSet = new TreeSet<String>();
			for (Category child : list) {
				if (admin || child.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
					treeSet.add(extractPath(getWholePathFromRootFor(child)));
				}
			}
			Iterator<String> iter = treeSet.iterator();
			while (iter.hasNext()) {
				println(iter.next());
			}
		} else {
			throw new RuntimeException("find: name missing");
		}
		
	}
	
	public Map<Integer,Category> listFiles(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.equals("ls")) {
			return printSubCategories(admin,lastEntry(currentDir));
		} else if (command.length() > 3) {
			if (command.substring(3).indexOf("*") >= 0) { //if wildcards, then match for current directory
				return printSubCategories(admin,lastEntry(currentDir),command.substring(3));
			} else {
				List<Category> catList = parseDirectories(admin,currentDir,command.substring(3));
				if (catList != null) {
					println(MessageFormat.format("{0}:",extractPath(catList)));
					return printSubCategories(admin,lastEntry(catList));
				} else {
					return null;
				}
			}
		} else {
			throw new RuntimeException("ls: directory name missing");
		}
	}

	public Category returnDirectory(boolean admin, String command, List<Category> currentDir) {
		refreshCurrentDir(currentDir);
		if (command.equals("use")) {
			throw new RuntimeException("use: directory name missing");
		} else if (command.indexOf("*") >= 0) {
			throw new RuntimeException("use: wildcards not allowed");
		} else if (command.length() > 4) {
			Category cat = lastEntry(parseDirectories(admin,currentDir,command.substring(4)));
			if (categoryDAO.getChildren(cat).isEmpty()) {
				return cat;
			} else {
				throw new RuntimeException("use: can only use leaf nodes");
			}
		} else {
			throw new RuntimeException("use: directory name missing");
		}
	}

	public Category runBatch(boolean admin, List<Category> currentDir, boolean allowUse, boolean useShortcuts) throws IOException {
		refreshCurrentDir(currentDir);
		Map<Integer,Category> currDirFiles = new HashMap<Integer,Category>();
		if (useShortcuts) {
			currDirFiles = listFiles(admin, "ls", currentDir);
		}
		print("batch: ");
		printPrompt(currentDir);
		String input = in.readLine();
		while (input != null && !input.equals("exit")) {
			try {
				if (input.length() == 0) {
					//do nothing
				} else if (input.equals("ls") || input.startsWith("ls ")) {
					currDirFiles = listFiles(admin, input, currentDir);
				} else if (input.equals("cd") || input.startsWith("cd ")) {
					changeDirectory(admin, input, currentDir);
				} else if (input.startsWith("mkdir ")) {
					makeDirectory(admin, input, currentDir);
				} else if (input.startsWith("rmdir ")) {
					removeDirectory(admin, input, currentDir);
				} else if (input.startsWith("mv ")) {
					moveDirectory(admin, input, currentDir);
				} else if (input.startsWith("cp ")) {
					copyDirectory(admin, input, currentDir);
				} else if (input.startsWith("ren ")) {
					renameDirectory(admin, input, currentDir);
				} else if (input.equals("find") || input.startsWith("find ")) {
					throw new RuntimeException("Command not available in batch mode");
				} else if (admin && (input.equals("ln") || input.startsWith("ln "))) {
					linkDirectories(admin, input, currentDir);
				} else if (admin && (input.equals("rmln") || input.startsWith("rmln "))) {
					removeDirectoryLinks(admin, input, currentDir);
				} else if (allowUse && (input.equals("use") || input.startsWith("use "))) {
					return returnDirectory(admin, input, currentDir);
				} else if (input.equals("batch")) {
					throw new RuntimeException("You are already in batch mode");
				} else if (input.equals("link")) {
					throw new RuntimeException("Command not available in batch mode");
				} else if (input.equals("commit")) {
					return null;
				} else if (input.startsWith("man ")) {
					throw new RuntimeException("Command not available in batch mode");
				} else if (input.equals("?") || input.equals("man")) {
					throw new RuntimeException("Command not available in batch mode");
				} else {
					if (useShortcuts) {
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
								changeDirectory(admin, "cd ..", currentDir);
							} else {
								changeDirectory(admin, currDirFiles.get(shortCutNbr), currentDir);
							}
							Map<Integer,Category> currDirFilesTemp = listFiles(admin, "ls", currentDir);
							if (currDirFilesTemp.isEmpty()) {
								printPrompt(currentDir);
								boolean use = false;
								if (allowUse) {
									use = UserInputHelper.getBoolean("Your hit a leaf. Use this leaf?", true, in);
								}
								if (use) {
									return currentDir.get(currentDir.size()-1);
								} else if (!admin) {
									changeDirectory(admin, "cd ..", currentDir);
								}
							} else {
								currDirFiles.clear();
								currDirFiles.putAll(currDirFilesTemp);
							}
						} else {
							throw new RuntimeException(input + ": command not found");
						}
					} else {
						throw new RuntimeException(input + ": command not found");
					}
				}
			} catch (RuntimeException re) {
				throw new RuntimeException("batch failed when executing the command \"" + input + "\": " + re.getMessage(),re);
			}
			print("batch: ");
			printPrompt(currentDir);
			input = in.readLine();
		}
		return null;
	}
	
	private String getString(String prompt) {
		try {
			print(prompt + ": ");
			return in.readLine();
		} catch (IOException e) {
			println("IOException received. Exiting program.");
			e.printStackTrace();
			System.exit(10);
		}
		return null;
	}

	/**
	 * This method prints out commands for creating the category structure for
	 * a chosen market (without actually running those commands).
	 */
	public void getMarketCategories() {
		for (int i = 0; i < listers.size(); i++) {
			println((i+1) + ": " + listers.get(i).getMarketName());
		}
		int market = UserInputHelper.getOneNumber("Choose market (\"q\" to quit)",1,listers.size(),false,true,in)-1;
		if (market == -999) {
			return;
		}
		println("cd /" + listers.get(market).getMarketName());
		List<Category> categoryList;
		try {
			/* Just pick any sellerMarket that has an account for the given market. */
			List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
			SellerMarket sellerMarket = null;
			for (SellerMarket sm : sellerMarkets) {
				if (sm.getMarket().getName().equals(listers.get(market).getMarketName())) {
					sellerMarket = sm;
					break;
				}
			}
			listers.get(market).login(sellerMarket);
			categoryList = listers.get(market).getCategories(sellerMarket);
		} catch (MarketTemporarilyDownException e) {
			throw new RuntimeException("Cannot get market categories because market is down.");
		} catch (LoggedOutFromMarketException e) {
			throw new RuntimeException("Cannot get market categories because we were logged out from market.");
		} catch (MarketLoggedOnAsWrongUserException e) {
			throw new RuntimeException("Cannot get market categories because market is in use by a different seller.");
		}
		listers.get(market).logout();
		Map<String,String> map = new HashMap<String,String>();
		for (int i = 0; i < categoryList.size(); i++) {
			List<Category> categoryTree = getWholePathFromRootFor(categoryList.get(i));
			String path = "";
			for (int j = 0; j < categoryTree.size(); j++) {
				String categoryName = categoryTree.get(j).getName().replaceAll(" ","_"); //don't allow spaces in category names
				path += " " + categoryName;
				if (map.get(path) == null) {
					println("mkdir -" + categoryTree.get(j).getMarketSpecId() + " " + categoryName);
					map.put(path, "");
				}
				println("cd " + categoryName);
			}
			println("cd /" + listers.get(market).getMarketName());
		}
	}
	
	/**
	 * This method fetches categories for a chosen market and updates the snapshot
	 * categories of that market to the database.
	 */
	public void getSnapshotCategories() {
		for (int i = 0; i < listers.size(); i++) {
			println((i+1) + ": " + listers.get(i).getMarketName());
		}
		int market = UserInputHelper.getOneNumber("Choose market (\"q\" to quit)",1,listers.size(),false,true,in)-1;
		if (market == -999) {
			return;
		}
		List<Category> currentDir = new ArrayList<Category>();
		//println("cd /" + listers.get(market).getMarketName() + "SNAPSHOT");
		changeDirectory(true, "cd /" + listers.get(market).getMarketName() + "SNAPSHOT", currentDir);
		List<Category> categoryList;
		try {
			/* Just pick any sellerMarket that has an account for the given market. */
			List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
			SellerMarket sellerMarket = null;
			for (SellerMarket sm : sellerMarkets) {
				if (sm.getMarket().getName().equals(listers.get(market).getMarketName())) {
					sellerMarket = sm;
					break;
				}
			}
			listers.get(market).login(sellerMarket);
			categoryList = listers.get(market).getCategories(sellerMarket);
		} catch (MarketTemporarilyDownException e) {
			throw new RuntimeException("Cannot get snapshot categories question because market is down.");
		} catch (LoggedOutFromMarketException e) {
			throw new RuntimeException("Cannot get market categories because we were logged out from market.");
		} catch (MarketLoggedOnAsWrongUserException e) {
			throw new RuntimeException("Cannot get snapshot categories because market is in use by a different seller.");
		} finally {
			listers.get(market).logout();
		}
		Map<String,String> map = new HashMap<String,String>();
		for (int i = 0; i < categoryList.size(); i++) {
			if (i % 10 == 0) {
				println("Stored " + i + "/" + categoryList.size());
			}
			List<Category> categoryTree = getWholePathFromRootFor(categoryList.get(i));
			String path = "";
			for (int j = 0; j < categoryTree.size(); j++) {
				String categoryName = categoryTree.get(j).getName().replaceAll(" ","_"); //don't allow spaces in category names
				path += " " + categoryName;
				if (map.get(path) == null) {
					//println("mkdir -" + categoryTree.get(j).getMarketSpecId() + " " + categoryName);
					makeDirectory(true, "mkdir -" + categoryTree.get(j).getMarketSpecId() + " " + categoryName, currentDir);
					map.put(path, "");
				}
				//println("cd " + categoryName);
				changeDirectory(true, "cd " + categoryName, currentDir);
			}
			//println("cd /" + listers.get(market).getMarketName() + "SNAPSHOT");
			changeDirectory(true, "cd /" + listers.get(market).getMarketName() + "SNAPSHOT", currentDir);
		}
	}
	
	private String headLine2Lucene(String headline) {
		//return headline.replaceAll("\\*", "x").replaceAll("\\?", "").replaceAll("\\+", "").replaceAll("\"", "").replaceAll(":", "");
		return headline.replaceAll("\\*", "\\\\*").replaceAll("\\?", "\\\\?").
			replaceAll("\\+", "\\\\+").replaceAll("\\-", "\\\\-").replaceAll("\"", "").
			replaceAll(":", "\\\\:").replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)").
			replaceAll("\\~", "\\\\~");
	}

	private void addDoc(IndexWriter w, String headline, Category category) throws IOException {
		Document doc = new Document();
		doc.add(new Field("title", headline, Field.Store.YES, Field.Index.ANALYZED));
		doc.add(new Field("categoryId", "" + category.getId(), Field.Store.YES, Field.Index.ANALYZED));
		w.addDocument(doc);
	}

	private List<Category> getPossibleCategoriesBasedOnHeadlineForAdTemplates(String headline,
			List<AdTemplate> adTemplates) throws Exception {
		List<Category> hitArray = new ArrayList<Category>();

		/* Create an index for the existing adtemplates and their categories. */
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_30);
		Directory index = new RAMDirectory();
		// the boolean arg to the IndexWriter constructor means to create a new
		// index, overwriting any existing indexes
		IndexWriter indexWriter = new IndexWriter(index, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
		for (AdTemplate adTemplate : adTemplates) {
			Category sellStarCategory = adTemplate.getProduct().getCategory();
			if (sellStarCategory != null) {
				addDoc(indexWriter, headLine2Lucene(adTemplate.getHeadline()), sellStarCategory);
			}
		}
		indexWriter.close();

		// the "title" arg specifies the default field to use
		// when no field is explicitly specified in the query.
		QueryParser qParser = new QueryParser(Version.LUCENE_30, "title", analyzer);
		//println("  qParser.minSim = " + qParser.getFuzzyMinSim());
		Query q = qParser.parse(headLine2Lucene(headline));

		/* Do the actual search. */
		int hitsPerPage = 100;
		IndexSearcher searcher = new IndexSearcher(index, true);
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		// 4. display results
		// printlnToOutput("Found " + hits.length + " hits.");
		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			// this.printlnToOutput((i + 1) + ". " + d.get("title"));
			String categoryId = d.get("categoryId");
			Category cat = categoryDAO.get(Long.parseLong(categoryId));
			if (!hitArray.contains(cat)) {
				hitArray.add(cat);
			}
			// printlnToOutput(d.get("title") + ", " + d.get("categoryId"));
		}

		// searcher can only be closed when there
		// is no need to access the documents any more.
		searcher.close();

		return hitArray;
	}

	private List<Category> getPossibleCategoriesBasedOnHeadlineForProducts(String headline,
			List<Product> products) throws Exception {
		List<Category> hitArray = new ArrayList<Category>();

		/* Create an index for the existing adtemplates and their categories. */
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_30);
		Directory index = new RAMDirectory();
		// the boolean arg to the IndexWriter constructor means to create a new
		// index, overwriting any existing indexes
		IndexWriter indexWriter = new IndexWriter(index, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);
		for (Product product : products) {
			Category sellStarCategory = product.getCategory();
			if (sellStarCategory != null) {
				addDoc(indexWriter, headLine2Lucene(product.getName()), sellStarCategory);
			}
		}
		indexWriter.close();

		// the "title" arg specifies the default field to use
		// when no field is explicitly specified in the query.
		Query q = new QueryParser(Version.LUCENE_30, "title", analyzer).parse(headLine2Lucene(headline));

		/* Do the actual search. */
		int hitsPerPage = 100;
		IndexSearcher searcher = new IndexSearcher(index, true);
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		// 4. display results
		// printlnToOutput("Found " + hits.length + " hits.");
		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			// this.printlnToOutput((i + 1) + ". " + d.get("title"));
			String categoryId = d.get("categoryId");
			Category cat = categoryDAO.get(Long.parseLong(categoryId));
			if (!hitArray.contains(cat)) {
				hitArray.add(cat);
			}
			// printlnToOutput(d.get("title") + ", " + d.get("categoryId"));
		}

		// searcher can only be closed when there
		// is no need to access the documents any more.
		searcher.close();

		return hitArray;
	}

	private String createCategorySuggestion(List<AdTemplate> allAdTemplates, AdTemplate adTemplate) throws Exception {
		StringBuffer categorySuggestion = new StringBuffer();
		/* Get categories having the same provider category AND matches by the headline
		 * of the translated product (=headline of the adtemplate). */
		List<AdTemplate> adTemplateList = adTemplateDAO.findByProviderCategory(adTemplate.getProduct().getProviderCategory());
		List<Category> listHeadLineAndProvCatForLocalized = getPossibleCategoriesBasedOnHeadlineForAdTemplates(adTemplate.getHeadline(), adTemplateList); //ETTAN
		if (listHeadLineAndProvCatForLocalized.size() > 0) {
			categorySuggestion.append("listHeadLineAndProvCatForLocalized:");
			for (int i = 0; i < listHeadLineAndProvCatForLocalized.size(); i++) {
				Category category = listHeadLineAndProvCatForLocalized.get(i);
				categorySuggestion.append(category.getId());
				if (i < (listHeadLineAndProvCatForLocalized.size() - 1)) {
					categorySuggestion.append(",");
				} else {
					categorySuggestion.append(";");
				}
				//System.out.println("listHeadLineAndProvCatForLocalized: " + extractPath(getWholePathFromRootFor(category)));
			}
			/*for (Category category : listHeadLineAndProvCatForLocalized) {
				System.out.println("listHeadLineAndProvCatForLocalized: " + extractPath(getWholePathFromRootFor(category)));
			}*/
		}

		/* Get categories having the same provider category AND matches by the name
		 * of the untranslated product (=name of the product). */
		List<Category> listHeadLineAndProvCatForInternational = getPossibleCategoriesBasedOnHeadlineForProducts //TREAN
		(adTemplate.getProduct().getName(), productDAO.getProductsWithProviderCategory
				(adTemplate.getProduct().getProviderCategory(),adTemplate.getProduct().getProvider()));
		if (listHeadLineAndProvCatForInternational.size() > 0) {
			categorySuggestion.append("listHeadLineAndProvCatForInternational:");
			for (int i = 0; i < listHeadLineAndProvCatForInternational.size(); i++) {
				Category category = listHeadLineAndProvCatForInternational.get(i);
				categorySuggestion.append(category.getId());
				if (i < (listHeadLineAndProvCatForInternational.size() - 1)) {
					categorySuggestion.append(",");
				} else {
					categorySuggestion.append(";");
				}
				//System.out.println("listHeadLineAndProvCatForInternational: " + extractPath(getWholePathFromRootFor(category)));
			}
			/*for (Category category : listHeadLineAndProvCatForInternational) {
				System.out.println("listHeadLineAndProvCatForInternational: " + extractPath(getWholePathFromRootFor(category)));
			}*/
		}
		
		/* Then get all sellstar categories matching the specified provider category. */
		List<CategoryHits> cHits = new ArrayList<CategoryHits>();
		Map<Long, Long> catCountMap = categoryDAO
				.getSellStarCategoryCountForProviderCategory(adTemplate.getProduct().getProviderCategory());
		Iterator<Long> iter = catCountMap.keySet().iterator();
		while (iter.hasNext()) {
			Long key = iter.next();
			cHits.add(new CategoryHits(categoryDAO.get(key), catCountMap.get(key)));
		}
		Collections.sort(cHits);
		Collections.reverse(cHits); // get most matches first
		if (cHits.size() > 0) {
			categorySuggestion.append("providerCategory2SellstarCategory:"); //SEXAN
			for (int i = 0; i < cHits.size(); i++) {
				Category category = cHits.get(i).getSellStarCategory();
				categorySuggestion.append(category.getId());
				if (i < (cHits.size() - 1)) {
					categorySuggestion.append(",");
				} else {
					categorySuggestion.append(";");
				}
			}
		}

		/* Then get categories that match by SellStar category and headline. */
		adTemplateList = new ArrayList<AdTemplate>();
		for (CategoryHits chi : cHits) {
			adTemplateList.addAll(adTemplateDAO.findBySellStarCategory(chi.getSellStarCategory()));
		}
		List<Category> listHeadlineAndSellStarCat = getPossibleCategoriesBasedOnHeadlineForAdTemplates(adTemplate.getHeadline(), adTemplateList); //TVAAN
		if (listHeadlineAndSellStarCat.size() > 0) {
			categorySuggestion.append("listHeadlineAndSellStarCat:");
			for (int i = 0; i < listHeadlineAndSellStarCat.size(); i++) {
				Category category = listHeadlineAndSellStarCat.get(i);
				categorySuggestion.append(category.getId());
				if (i < (listHeadlineAndSellStarCat.size() - 1)) {
					categorySuggestion.append(",");
				} else {
					categorySuggestion.append(";");
				}
				//System.out.println("listHeadlineAndSellStarCat: " + extractPath(getWholePathFromRootFor(category)));
			}
			/*for (Category category : listHeadlineAndSellStarCat) {
				System.out.println("listHeadlineAndSellStarCat: " + extractPath(getWholePathFromRootFor(category)));
			}*/
		}

		/* Then get categories for any matching adtemplate headline independently of what
		 * category it's in (for translated products, i.e. headline of the adtemplate). */
		List<Category> listAnyAdTemplateHeadlineForLocalized = getPossibleCategoriesBasedOnHeadlineForAdTemplates(adTemplate.getHeadline(), allAdTemplates); //FYRAN
		if (listAnyAdTemplateHeadlineForLocalized.size() > 0) {
			categorySuggestion.append("listAnyAdTemplateHeadlineForLocalized:");
			for (int i = 0; i < listAnyAdTemplateHeadlineForLocalized.size(); i++) {
				Category category = listAnyAdTemplateHeadlineForLocalized.get(i);
				categorySuggestion.append(category.getId());
				if (i < (listAnyAdTemplateHeadlineForLocalized.size() - 1)) {
					categorySuggestion.append(",");
				} else {
					categorySuggestion.append(";");
				}
				//System.out.println("listAnyAdTemplateHeadlineForLocalized: " + extractPath(getWholePathFromRootFor(category)));
			}
			/*for (Category category : listAnyAdTemplateHeadlineForLocalized) {
				System.out.println("listAnyAdTemplateHeadlineForLocalized: " + extractPath(getWholePathFromRootFor(category)));
			}*/
		}

		/* Then get categories for any matching adtemplate headline independently of what
		 * category it's in (for untranslated products, i.e. name of the product). */
		List<Category> listAnyAdTemplateHeadlineForInternational = getPossibleCategoriesBasedOnHeadlineForProducts //FEMMAN
			(adTemplate.getProduct().getName(), productDAO.loadAll());
		if (listAnyAdTemplateHeadlineForInternational.size() > 0) {
			categorySuggestion.append("listAnyAdTemplateHeadlineForInternational:");
			for (int i = 0; i < listAnyAdTemplateHeadlineForInternational.size(); i++) {
				Category category = listAnyAdTemplateHeadlineForInternational.get(i);
				categorySuggestion.append(category.getId());
				if (i < (listAnyAdTemplateHeadlineForInternational.size() - 1)) {
					categorySuggestion.append(",");
				} else {
					categorySuggestion.append(";");
				}
				//System.out.println("listAnyAdTemplateHeadlineForInternational: " + extractPath(getWholePathFromRootFor(category)));
			}
			/*for (Category category : listAnyAdTemplateHeadlineForInternational) {
				System.out.println("listAnyAdTemplateHeadlineForInternational: " + extractPath(getWholePathFromRootFor(category)));
			}*/
		}
		
		return categorySuggestion.toString();
	}
	
	public void createCategorySuggestions() {
		List<AdTemplate> adTemplateList = adTemplateDAO.getAdTemplatesWithoutCategory();
		int nbrProdsMissingCategories = 0;
		int nbrCategorySuggestionsExist = 0;
		for (AdTemplate adTemplate : adTemplateList) {
			Product product = adTemplate.getProduct();
			if (product.getCategory() == null) {
				nbrProdsMissingCategories++;
			}
			if (product.getCategorySuggestion() != null) {
				nbrCategorySuggestionsExist++;
			}
			if (product.getCategory() != null && product.getCategorySuggestion() != null) {
				throw new RuntimeException("Error: product " + product.getId() + " has both category and category suggestion.");
			}
		}
		
		/* Make sure there are at the most 50 category suggestions. The reason for this is that
		 * as more categories are chosen, the better guesses will be made for subsequent runs.
		 * Therefore we don't want to make suggestions for too many products, but rather wait
		 * for the administrator to go through the suggestions and choose the correct category
		 * for those products before we continue to make suggestions for other products. */
		int max = 50;
		int nbrAdded = 0;
		println("Creating category suggestions for " + (max - nbrCategorySuggestionsExist) + " products.");
		try {
			if (nbrCategorySuggestionsExist < max && nbrProdsMissingCategories > 0) {
				for (AdTemplate adTemplate : adTemplateList) {
					Product product = adTemplate.getProduct();
					if (product.getCategory() == null && product.getCategorySuggestion() == null) {
						/* Add only ONE category suggestion if no other products from this provider category
						 * has gotten SellStar categories defined. The reason for this is that the suggestions
						 * aren't as reliable for the first suggestion within a given provider category. So
						 * we want the operator to define a SellStar category for the first one before we
						 * generate any suggestions for more products in this provider category. */
						List<Product> prodList = productDAO.getProductsWithProviderCategory(product.getProviderCategory(),product.getProvider());
						int otherProductHasCategoryDefined = 0;
						int otherProductHasCategorySuggestion = 0;
						for (Product prod : prodList) {
							if (prod.getCategory() != null) {
								otherProductHasCategoryDefined++;
							}
							if (prod.getCategorySuggestion() != null) {
								otherProductHasCategorySuggestion++;
							}
						}
						if (otherProductHasCategoryDefined > 0 || otherProductHasCategorySuggestion == 0) {
							String categorySuggestion = createCategorySuggestion(adTemplateList,adTemplate);
							if (otherProductHasCategoryDefined == 0 && otherProductHasCategorySuggestion == 0) {
								categorySuggestion = "FIRST;" + categorySuggestion;
							} else if (otherProductHasCategoryDefined < 3) {
								if (otherProductHasCategorySuggestion < 3) {
									categorySuggestion = "SECOND;" + categorySuggestion; //for this one we have a better suggestion
								} else {
									continue; //don't allow too many categories to be suggested at the second level
								}
							} else {
								categorySuggestion = "THIRD;" + categorySuggestion; //for this one the suggestions should be quite good
							}
							//println("categorySuggestion = " + categorySuggestion);
							product.setCategorySuggestion(categorySuggestion);
							nbrAdded++;
							nbrCategorySuggestionsExist++;
							println("Number of suggestions = " + nbrCategorySuggestionsExist + ".");
						}
					}
					if (nbrCategorySuggestionsExist == max) {
						break;
					}
				}
			}
		} catch (Exception e) {
			/* Print also to the log since this command is mostly called automatically and
			 * not manually. */
			logger.error("Cannot create category suggestions: ", e);
			println("Cannot create category suggestions.");
			e.printStackTrace();
			EmailSender.sendOperationProblemReport("Category creation failed",
					"Category creation failed with the following error:\n\n"
				+ StringUtilities.getStackTrace(e));
			throw new RuntimeException("Cannot create category suggestions: ", e);
		}
		println("Number of suggestions added = " + nbrAdded + ".");
	}
	
	/**
	 * This method updates existing categories for a chosen market, or informs
	 * the user about changes that aren't possible to automatically correct.
	 */
	public void updateMarketCategories(boolean admin) {
		/* Figure out what market to update categories for. */
		println("0: " + Market.SELLSTAR_MARKET_NAME + "<->" + Market.HUUTONET_MARKET_NAME + "SNAPSHOT");
		for (int i = 0; i < listers.size(); i++) {
			println((i+1) + ": " + listers.get(i).getMarketName());
		}
		int marketId = UserInputHelper.getOneNumber("Choose market (\"q\" to quit)",0,listers.size(),false,true,in);
		if (marketId == -999) {
			return;
		}
		marketId -= 1;
		
		/* Fetch the start nodes of the market and of its snapshot categories. */
		Market marketDb;
		Market marketSnap;
		if (marketId == -1) {
			marketDb = marketDAO.getByName(Market.SELLSTAR_MARKET_NAME);
			marketSnap = marketDAO.getByName(Market.HUUTONET_MARKET_NAME + "SNAPSHOT");
		} else {
			marketDb = marketDAO.getByName(listers.get(marketId).getMarketName());
			marketSnap = marketDAO.getByName(listers.get(marketId).getMarketName() + "SNAPSHOT");
		}
		Category rootDb = categoryDAO.getChild(null,marketDb.getName());
		if (rootDb == null) {
			throw new RuntimeException("Directory structure not found for market \"" + marketDb.getName() + "\".");
		}
		List<Category> currentDirDb = new ArrayList<Category>();
		currentDirDb.add(rootDb);
		Category rootSnap = categoryDAO.getChild(null,marketSnap.getName());
		if (rootSnap == null) {
			throw new RuntimeException("Snapshot directory structure not found for market \"" + marketSnap.getName() + "\".");
		}
		List<Category> currentDirSnap = new ArrayList<Category>();
		currentDirSnap.add(rootSnap);
		if (categoryDAO.getChildren(rootSnap).size() == 0) {
			throw new RuntimeException("Snapshot directory empty for market \"" + marketSnap.getName() + "\".");
		}
		List<Category> additions = new ArrayList<Category>();
		List<Category> removals = new ArrayList<Category>();
		BooleanHolder actuallyAdded = new BooleanHolder();
		actuallyAdded.value = false;

		/* 1.) check for categories having changed name or marketSpecId's.
		 * 2.) check for moved categories
		 * 3.) check for new categories
		 * Loop #2 and #3 until no more new categories were discovered. The reason
		 * for this is that an added category may uncover a moved category.
		 * 4.) check for removed categories */
		println("CHECKING FOR CHANGED NAMES OR CHANGED MARKETSPECID'S:");
		// Compare the snapshot to the database.
		ascendToCategoryComp(admin,currentDirSnap,currentDirDb,true,additions,removals,actuallyAdded,false,false);
		// Then compare the database to the snapshot.
		ascendToCategoryComp(admin,currentDirDb,currentDirSnap,false,additions,removals,actuallyAdded,false,false);
		boolean newDirs = true;
		while (newDirs) {
			newDirs = false;
			// Check for moved directories, i.e. check if any of the previously reported
			// removed directories were found among the added directories.
			println("CHECKING FOR MOVED DIRECTORIES:");
			for (Category catR : removals) {
				for (Category catA : additions) {
					if (catR.getMarketSpecId().equals(catA.getMarketSpecId())) {
						List<Category> rDir = getWholePathFromRootFor(catR);
						List<Category> aDir = getWholePathFromRootFor(catA);
						println("Directory " + extractPath(rDir) + " has moved to "
								+ "/" + rDir.get(0).getName() + extractPath(aDir.subList(1, aDir.size())));
						boolean move = UserInputHelper.getBoolean("Do you want to make this change?",true,in);
						if (move) {
							String mvCommand = "mv " + extractPath(rDir) + " /" + rDir.get(0).getName()
								+ extractPath(aDir.subList(1, aDir.size()-1));
							//println("mvCommand = " + mvCommand);
							moveDirectory(true, mvCommand, new ArrayList<Category>());
						}
						break;
					}
				}
			}
			// Check for new categories by comparing the snapshot to the database.
			println("CHECKING FOR NEW DIRECTORIES:");
			additions = new ArrayList<Category>();
			removals = new ArrayList<Category>();
			actuallyAdded = new BooleanHolder();
			actuallyAdded.value = false;
			ascendToCategoryComp(admin,currentDirSnap,currentDirDb,true,additions,removals,actuallyAdded,true,false);
			ascendToCategoryComp(admin,currentDirDb,currentDirSnap,false,additions,removals,actuallyAdded,true,false);
			if (actuallyAdded.value) {
				newDirs = true;
				/* If some directory was added, then we need to go through it again because moved directories
				 * aren't handled correctly if some directory was added during the run. */
				additions = new ArrayList<Category>();
				removals = new ArrayList<Category>();
				ascendToCategoryComp(admin,currentDirSnap,currentDirDb,true,additions,removals,actuallyAdded,false,false);
				ascendToCategoryComp(admin,currentDirDb,currentDirSnap,false,additions,removals,actuallyAdded,false,false);
			}
		}
		
		/* Finally check for removed categories. */
		println("CHECKING FOR REMOVED DIRECTORIES:");
		additions = new ArrayList<Category>();
		removals = new ArrayList<Category>();
		ascendToCategoryComp(admin,currentDirSnap,currentDirDb,true,additions,removals,actuallyAdded,false,true);
		ascendToCategoryComp(admin,currentDirDb,currentDirSnap,false,additions,removals,actuallyAdded,false,true);
	}
	
	private class BooleanHolder {
		public boolean value;
	}
	
	private boolean ascendToCategoryComp(boolean admin, List<Category> basePath, List<Category> comparisonPath, boolean snapshotIsBase,
			List<Category> additions, List<Category> removals, BooleanHolder actuallyAdded, boolean handleNew, boolean handleRemoved) {
		for (Category cat : categoryDAO.getChildren(lastEntry(basePath))) {
			basePath.add(cat);
			Category compChild = getComparisonChild(admin,basePath,comparisonPath,snapshotIsBase,additions,removals,actuallyAdded,handleNew,handleRemoved);
			if (compChild != null) {
				comparisonPath.add(compChild);
				boolean cont = ascendToCategoryComp(admin,basePath,comparisonPath,snapshotIsBase,additions,removals,actuallyAdded,handleNew,handleRemoved);
				if (!cont) {
					return false;
				}
				comparisonPath.remove(comparisonPath.size()-1);
			}
			basePath.remove(basePath.size()-1);
		}
		return true;
	}
	
	private void addAllSubNodes(Category startCategory, List<Category> nodeList) {
		nodeList.add(startCategory);
		for (Category cat : categoryDAO.getChildren(lastEntry(nodeList))) {
			addAllSubNodes(cat,nodeList);
		}
	}
	
	/**
	 * 
	 * @param basePath The base path (the base path is compared to the comparison path)
	 * @param comparisonPath
	 * @param snapshotIsBase Should be true when basePath is the snapshot version and otherwise false.
	 * @return
	 */
	private Category getComparisonChild(boolean admin, List<Category> basePath, List<Category> comparisonPath, boolean snapshotIsBase,
			List<Category> additions, List<Category> removals, BooleanHolder actuallyAdded, boolean handleNew, boolean handleRemoved) {
		if (lastEntry(basePath).getMarket().getId().equals(lastEntry(comparisonPath).getMarket().getId())) {
			throw new RuntimeException("Internal error, category trees are from the same market.");
		}
		Category child = categoryDAO.getChildByNameAndMarketSpecId(lastEntry(comparisonPath),
				lastEntry(basePath).getName(), lastEntry(basePath).getMarketSpecId());
		if (child == null) {
			child = categoryDAO.getChild(lastEntry(comparisonPath),lastEntry(basePath).getName());
			if (child != null) {
				if (categoryDAO.getChildByMarketSpecId(lastEntry(comparisonPath), lastEntry(basePath).getMarketSpecId()) != null) {
					throw new RuntimeException("Cannot handle: category changed marketSpecId AND another"
							+ " category exists using the old marketSpecId");
				}
				if (snapshotIsBase) {
					println("Directory changed marketSpecId: " + extractPath(basePath));
					println("  =>" + lastEntry(basePath).getMarket().getName() + ".marketSpecId = " + lastEntry(basePath).getMarketSpecId());
					println("  =>" + child.getMarket().getName() + ".marketSpecId = " + child.getMarketSpecId());
					boolean change = UserInputHelper.getBoolean("Do you want to change the marketSpecId?",true,in);
					if (change) {
						child.setMarketSpecId(lastEntry(basePath).getMarketSpecId());
					}
				}
			} else {
				child = categoryDAO.getChildByMarketSpecId(lastEntry(comparisonPath), lastEntry(basePath).getMarketSpecId());
				if (child == null) {
					if (snapshotIsBase) {
						if (handleNew) {
							println("New directory found: " + "/" + comparisonPath.get(0).getName()
									+ extractPath(basePath.subList(1, basePath.size())) + " (marketSalesId = "
									+ lastEntry(basePath).getMarketSpecId() + ")");
							boolean change = UserInputHelper.getBoolean("Do you want to add this one?",true,in);
							if (change) {
								//makeDirectory(true, "mkdir " + lastEntry(basePath).getName(), comparisonPath.subList(0,comparisonPath.size()));
								createNewChildCategory(true,lastEntry(comparisonPath),lastEntry(basePath).getName(),
										lastEntry(basePath).getMarketSpecId());
								actuallyAdded.value = true;
							}
						}
						additions.add(lastEntry(basePath));
					} else {
						if (handleRemoved) {
							println("Directory removal found: " + extractPath(basePath));
							boolean remove = UserInputHelper.getBoolean("Try to remove directory?",true,in);
							if (remove) {
								try {
									removeCategory(admin,lastEntry(basePath),true);
									println("Directory removed");
								} catch (RuntimeException e) {
									println(e.getMessage());
								}
							}
						}
						/* Add this directory as well as all of its subnodes to the removals list.*/
						addAllSubNodes(lastEntry(basePath),removals);
					}
				} else {
					if (snapshotIsBase) {
						println("Subdirectory to " + extractPath(comparisonPath) + " changed name: " + child.getName()
								+ " became " + lastEntry(basePath).getName());
						boolean change = UserInputHelper.getBoolean("Do you want to change the name?",true,in);
						if (change) {
							child.setName(lastEntry(basePath).getName());
						}
					}
				}
			}
		}
		return child;
	}
	
	public void linkBatch(boolean admin, List<Category> currentDir) {
		List<Category> basePath = new ArrayList<Category>();
		List<Category> secondaryPath = new ArrayList<Category>();
		List<Category> rootEntries = categoryDAO.getChildren(null);
		if (rootEntries.isEmpty()) {
			println("No root categories found. Exiting link.");
			return;
		} else if (rootEntries.size() == 1) {
			println("Not possible to do any linking because only one root category found. Exiting link.");
			return;
		}
		println("Between which markets do you want to do the linking?");
		println("Specify the base market first and then the secondary.");
		println("For example if SellStar is the base market and Huutonet");
		println("is the secondary market, then this batch will try to find");
		println("SellStar categories that don't have a mapping to Huutonet.");
		println("If instead Huutonet is the base and SellStar is secondary,");
		println("then the mapping will be done in the other direction.");
		for (int i = 0; i < rootEntries.size(); i++) {
			println((i+1) + ": " + rootEntries.get(i).getName());
		}
		int firstMarket = -1;
		int secondMarket = -1;
		while (true) {
			firstMarket = UserInputHelper.getOneNumber("Base market place (\"q\" to quit)",1,rootEntries.size(),false,true,in);
			if (firstMarket == -999) {
				return;
			}
			secondMarket = UserInputHelper.getOneNumber("Secondary market place (\"q\" to quit)",1,rootEntries.size(),false,true,in);
			if (secondMarket == -999) {
				return;
			}
			firstMarket--;
			secondMarket--;
			if (firstMarket == secondMarket) {
				println("Faulty input. Base and secondary market place cannot be the same.");
				continue;
			} else if (!rootEntries.get(firstMarket).getName().equals(Market.SELLSTAR_MARKET_NAME)
					&& !rootEntries.get(secondMarket).getName().equals(Market.SELLSTAR_MARKET_NAME)) {
				println("One of the markets have to be SellStar");
				continue;
			}
			break;
		}
		boolean assignExactMatchAutomatically = UserInputHelper.getBoolean("Do you want to automatically assign exact matches?",false,in);
		boolean mapOnlyForInUse = false;
		if (!assignExactMatchAutomatically && rootEntries.get(firstMarket).getName().equals(Market.SELLSTAR_MARKET_NAME)) {
			mapOnlyForInUse = UserInputHelper.getBoolean("Do you want to only map categories that are actually in use?",true,in);
		}
		basePath.add(rootEntries.get(firstMarket));
		secondaryPath.add(rootEntries.get(secondMarket));
		ascend(new LinkToCategory(rootEntries.get(secondMarket).getMarket(),assignExactMatchAutomatically,mapOnlyForInUse),basePath);
	}
	
	public interface CategoryProcessor {
		/* Return true if the processor should process this category. */
		public boolean shouldProcess(List<Category> path);
		/* Return true if we should continue parsing the category tree, or false if we should stop. */
		public boolean run(List<Category> path);
	}
	
	/**
	 * This class links matching categories between two market places.
	 */
	public class LinkToCategory implements CategoryProcessor {

		private Market otherMarket;
		private boolean assignExact;
		private boolean mapOnlyForInUse;
		
		public LinkToCategory(Market otherMarket, boolean assignExact, boolean mapOnlyForInUse) {
			this.otherMarket = otherMarket;
			this.assignExact = assignExact;
			this.mapOnlyForInUse = mapOnlyForInUse;
		}

		public boolean shouldProcess(List<Category> path) {
			/* Don't process non-leaf nodes. */
			if (!categoryDAO.getChildren(lastEntry(path)).isEmpty()) {
				return false;
			}
			
			/* Don't process categories that already have a mapping to the other market. */
			if (!categoryMappingDAO.getCategoryMappingsForMarket(lastEntry(path),otherMarket).isEmpty()) {
				return false;
			}

			/* If mapOnlyForInUse is true, then check if there are actually some products in this category
			 * and only process this category if it contains products. */
			if (mapOnlyForInUse && productDAO.getProductsInCategory(lastEntry(path)).size() == 0) {
				return false;
			}
			
			return true;
		}

		
		private List<Category> sort(List<Category> list, Map<Category,Integer> scores) {
			List<Category> retList = new ArrayList<Category>();
			while (retList.size() < list.size()) {
				int maxScore = -1;
				Category bestCat = null;
				Iterator<Category> iter = scores.keySet().iterator();
				while (iter.hasNext()) {
					Category c = iter.next();
					int score = scores.get(c);
					if (score >= maxScore) {
						maxScore = score;
						bestCat = c;
					}
				}
				//println("cat " + bestCat.getId() + " has score " + maxScore);
				retList.add(bestCat);
				scores.remove(bestCat);
			}
			return retList;
		}
		
		private List<Category> filterOutNonLeaves(List<Category> list) {
			List<Category> retList = new ArrayList<Category>();
			for (Category cat : list) {
				if (categoryDAO.getChildren(cat).isEmpty()) {
					retList.add(cat);
				}
			}
			return retList;
		}
		
		private Category extractMatchWithSamePath(List<Category> targetPath, List<Category> candidateList) {
			String targetPathStr = extractPath(targetPath.subList(1,targetPath.size()-1));
			for (Category category : candidateList) {
				List<Category> candidatePath = getWholePathFromRootFor(category);
				String candidatePathStr = extractPath(candidatePath.subList(1,candidatePath.size()-1));
				if (targetPathStr.equals(candidatePathStr)) {
					return category;
				}
			}
			return null;
		}
		
		public boolean run(List<Category> path) {
			//println("category " + lastEntry(path).getId() + "(" + lastEntry(path).getName() + ") is a leaf");
			List<Category> exactMatches = categoryDAO.getCategoriesContainingNamePart(lastEntry(path).getName(),otherMarket,true);
			exactMatches = filterOutNonLeaves(exactMatches);
			int ret = 0;
			String fullPathStr = extractPath(path);
			println("\n---------------------------\n");
			int nbrProdsInCategory = productDAO.getProductsInCategory(lastEntry(path)).size();
			if (nbrProdsInCategory == 0) {
				println("*** No product is assigned to this category. ***");
			} else {
				println("(there are " + nbrProdsInCategory + " products in this category)");
			}
			//println("\nCategory " + extractPath(path));
			if (!exactMatches.isEmpty()) {
				println("Found " + exactMatches.size() + " exact match(es) for " + fullPathStr + ":");
				/* There can be several categories that have the exact same name, if they are in different subcategories. */
				Category exactMatch = null;
				if (exactMatches.size() > 1) {
					exactMatch = extractMatchWithSamePath(path, exactMatches);
				} else {
					exactMatch = exactMatches.get(0);
				}
				if (assignExact && exactMatch != null) {
					if (exactMatches.size() > 1) {
						println("Several exact matches were found, but only one of them had the exact same path.");
					}
					println("automatic assignment: " + extractPath(getWholePathFromRootFor(exactMatch)));
					CategoryMapping cm = new CategoryMapping();
					cm.setCategory1(lastEntry(path));
					cm.setCategory2(exactMatch);
					categoryMappingDAO.save(cm);
					categoryMappingDAO.flush();
					ret = -1;
				} else {
					ret = processMatchList(exactMatches,lastEntry(path),
							"Choose one (empty to skip to next category, 0 to get partial matches, \"q\" to quit)");
				}
			} else {
				println("No exact matches found for " + fullPathStr + ":");
			}
			if (ret == 0/* && (exactMatches.isEmpty()
					|| getBoolean("Do you want to search for partial matches (for words of size >= 4)?",!linkMade))*/) {
				List<String> skip = new ArrayList<String>(); //skip very common words that otherwise give too many hits
				skip.add("tarvikkeet");
				skip.add("muut");
				List<Category> partialMatches = new ArrayList<Category>();
				Map<Category,Integer> scores = new HashMap<Category,Integer>();
				String[] split = lastEntry(path).getName().split("_");
				for (String s : split) {
					if (s.length() >= 4 && !skip.contains(s.toLowerCase())) {
						for (Category c : categoryDAO.getCategoriesContainingNamePart("%" + s + "%",otherMarket,true)) {
							Integer score = scores.get(c);
							if (score == null) {
								score = new Integer(0);
							}
							score++;
							scores.put(c,score);
							if (!partialMatches.contains(c)) {
								partialMatches.add(c);
							}
						}
					}
					String[] split2 = s.split("-");
					if (split2.length > 1 && !split2[0].equals(s) && !split2[1].equals(s)) {
						for (String s2 : split2) {
							if (s2.length() >= 4 && !skip.contains(s2.toLowerCase())) {
								for (Category c : categoryDAO.getCategoriesContainingNamePart("%" + s2 + "%",otherMarket,true)) {
									Integer score = scores.get(c);
									if (score == null) {
										score = new Integer(0);
									}
									score++;
									scores.put(c,score);
									if (!partialMatches.contains(c)) {
										partialMatches.add(c);
									}
								}
							}
						}
					}
				}
				partialMatches = sort(partialMatches,scores);
				partialMatches = filterOutNonLeaves(partialMatches);
				if (partialMatches.size() > 0) {
					println("Found " + partialMatches.size() + " partial match(es) for " + fullPathStr);
					println("(the matches are sorted so that the best match comes first):");
					ret = processMatchList(partialMatches,lastEntry(path),
							"Choose one (empty to skip to next category, 0 for manual search, \"q\" to quit)");
				} else {
					println("No partial matches found for " + fullPathStr + ":");
				}
			}
			if (ret == 0) {
				String custom = getString("Enter custom words for manual search (empty row skips, \"q\" to quit)");
				while (ret == 0 && custom != null && !custom.trim().equals("")) {
					if (custom.equalsIgnoreCase("q")) {
						return false;
					}
					List<Category> partialMatches = new ArrayList<Category>();
					Map<Category,Integer> scores = new HashMap<Category,Integer>();
					String[] split = custom.split(" ");
					for (String s : split) {
						for (Category c : categoryDAO.getCategoriesContainingNamePart("%" + s + "%",otherMarket,true)) {
							Integer score = scores.get(c);
							if (score == null) {
								score = new Integer(0);
							}
							score++;
							scores.put(c,score);
							if (!partialMatches.contains(c)) {
								partialMatches.add(c);
							}
						}
					}
					partialMatches = sort(partialMatches,scores);
					partialMatches = filterOutNonLeaves(partialMatches);
					if (partialMatches.size() > 0) {
						println("Found " + partialMatches.size() + " partial match(es) with manual search for " + fullPathStr);
						println("(the matches are sorted so that the best match comes first):");
						ret = processMatchList(partialMatches,lastEntry(path),
							"Choose one (empty to skip to next category, 0 for new search, \"q\" to quit)");
						if (ret == 0) {
							custom = getString("Enter custom words for manual search (empty row skips, \"q\" to quit)");
						}
					} else {
						println("No matches found for custom search for " + fullPathStr + ":");
						custom = getString("Enter custom words for manual search (empty row skips, \"q\" to quit)");
					}
				}
			}
			if (ret == -999) {
				return false;
			}
			/*if (getBoolean("Category done, do you want to continue to the next?",true)) {
				return true;
			} else {
				return false;
			}*/
			return true;
		}

		/**
		 * 
		 * @param matches
		 * @return -999 if "q" was chosen, -1 if no alternative was chosen and 0 if some alternative was chosen.
		 */
		private int processMatchList(List<Category> matches, Category source, String prompt) {
			for (int i = 0; i < matches.size(); i++) {
				println((i+1) + ": " + extractPath(getWholePathFromRootFor(matches.get(i))));
			}
			Integer choice = UserInputHelper.getOneNumber(prompt,0,matches.size(),true,true,in);
			if (choice == null) {
				return -1;
			} else if (choice == -999) {
				return choice;
			}
			while (choice != 0) {
				CategoryMapping cm = new CategoryMapping();
				cm.setCategory1(source);
				cm.setCategory2(matches.get(choice-1));
				categoryMappingDAO.save(cm);
				categoryMappingDAO.flush();
				//println("Link created");
				matches.remove(choice-1);
				if (matches.size() == 0) {
					//choice = 0;
					return -1;
				} else {
					for (int i = 0; i < matches.size(); i++) {
						println((i+1) + ": " + extractPath(getWholePathFromRootFor(matches.get(i))));
					}
					choice = UserInputHelper.getOneNumber(prompt,0,matches.size(),true,true,in);
					if (choice == null) {
						return -1;
					}
				}
			}
			return 0;
		}
		
	}
	
	private boolean ascend(CategoryProcessor categoryProcessor, List<Category> path) {
		if (categoryProcessor.shouldProcess(path)) {
			return categoryProcessor.run(path);
		} else {
			//println("category " + lastEntry(path).getId() + "(" + lastEntry(path).getName() + ") IS NOT A LEAF");
			for (Category cat : categoryDAO.getChildren(lastEntry(path))) {
				path.add(cat);
				boolean cont = ascend(categoryProcessor,path);
				path.remove(path.size()-1);
				if (!cont) {
					return false;
				}
			}
		}
		return true;
	}
	
	public void printPrompt(List<Category> path) {
		print(MessageFormat.format("{0}$ ",extractPath(path)));
	}
	
	private void refreshCurrentDir(List<Category> currentDir) {
		for (Category cat : currentDir) {
			categoryDAO.refresh(cat);
		}
	}
	
	public List<Category> getWholePathFromRootFor(Category category) {
		List<Category> list = new ArrayList<Category>();
		Category p = category;
		list.add(p);
		while (p.getParentCategory() != null) {
			p = p.getParentCategory();
			list.add(p);
		}
		Collections.reverse(list);
		return list;
		
	}
	
	/**
	 * 
	 * @param sources list of source categories
	 * @param destination one destination category
	 * @return null if no error occurred or else an error message
	 */
	private String extractOneOrManySourcesAndOneDestination(boolean admin, String input, List<Category> currentDir,
			List<Category> sources, List<Category> destination) {
		String[] split = input.split(" ");
		if (split.length != 2) {
			return "faulty parameters";
		} else {
			List<Category> toList = parseDirectories(admin,currentDir,split[1]);
			if (toList != null) {
				if (split[0].indexOf("*") >= 0) { //if wildcards, then match for current directory
					List<Category> list = categoryDAO.getChildrenContainingNamePart(lastEntry(currentDir),split[0].replaceAll("\\*","%"));
					if (list != null) {
						boolean canDo = true;
						for (Category cat : list) {
							if (cat.getId().equals(lastEntry(toList).getId())) {
								canDo = false;
							}
						}
						if (!canDo) {
							return "error: destination is the same as the source";
						} else {
							for (Category cat : list) {
								sources.add(cat);
							}
							destination.add(lastEntry(toList));
							return null;
						}
					} else {
						return "directory \"" + split[0] + "\" not found";
					}
				} else {
					List<Category> fromList = parseDirectories(admin,currentDir,split[0]);
					//toList.size == 0 means that the destination dir is the root directory
					if (fromList != null && !fromList.isEmpty()
							&& (toList.size() == 0 || !lastEntry(fromList).getId().equals(lastEntry(toList).getId()))) {
						sources.add(lastEntry(fromList));
						if (toList.size() > 0) {
							destination.add(lastEntry(toList));
						}
						return null;
					} else {
						return "faulty directories or fromDir same as toDir";
					}
				}
			} else {
				return "directory \"" + split[1] + "\" not found";
			}
		}
	}
	
	private void moveDirs(Category fromCat, Category toCat) {
		fromCat.setParentCategory(toCat);
		categoryDAO.update(fromCat);
		categoryDAO.flush();
	}
	
	private void copyDirs(Category fromCat, Category toCat) {
		List<Category> children = categoryDAO.getChildren(fromCat);
		if (!children.isEmpty()) {
			throw new RuntimeException("cp: \"" + fromCat.getName() + "\" has children, cannot copy (only shallow copy supported)");
		}
		Category newCategory = new Category();
		newCategory.setName(fromCat.getName());
		newCategory.setParentCategory(toCat);
		newCategory.setMarket(toCat.getMarket());
		newCategory.setMarketSpecId(9999999);
		categoryDAO.save(newCategory);
		categoryDAO.flush();
		newCategory.setMarketSpecId(newCategory.getId().intValue());
		categoryDAO.update(newCategory);
		categoryDAO.flush();
	}
	
	private void renameDir(Category category, String newName) {
		category.setName(newName);
		categoryDAO.update(category);
		categoryDAO.flush();
	}
	
	private List<Category> parseDirectories(boolean admin, List<Category> currentDir, String path) {
		List<Category> tempDir = new ArrayList<Category>();
		tempDir.addAll(currentDir);
		if (path.equals(".")) {
			return tempDir;
		} else if (path.indexOf('/') >= 0) {
			if (path.charAt(0) == '/') {
				tempDir.clear(); //absolute path so go to the root
			}
			String[] split = path.split("/");
			for (String str : split) {
				if (str.equals("..")) {
					if (tempDir.size() > 0) {
						tempDir.remove(tempDir.size()-1);
					} else {
						throw new RuntimeException("directory \"" + str + "\" not found");
					}
				} else if (!str.equals("")) {
					Category child = findMatchingChildCategory(admin,lastEntry(tempDir),str);
					if (child != null) {
						tempDir.add(child);
					} else {
						throw new RuntimeException("directory \"" + str + "\" not found");
					}
				}
			}
		} else if (path.equals("..")) {
			if (tempDir.size() > 0) {
				tempDir.remove(tempDir.size()-1);
			} else {
				throw new RuntimeException("directory \"" + path + "\" not found");
			}
		} else {
			Category child = findMatchingChildCategory(admin,lastEntry(tempDir),path);
			if (child != null) {
				tempDir.add(child);
			} else {
				throw new RuntimeException("directory \"" + path + "\" not found");
			}
		}
		if (admin || tempDir.isEmpty() || lastEntry(tempDir).getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
			return tempDir;
		} else {
			throw new RuntimeException("directory \"" + path + "\" not found");
		}
	}
	
	private Category lastEntry(List<Category> list) {
		if (list.size() == 0) {
			return null;
		} else {
			return list.get(list.size()-1);
		}
	}
	
	private void removeChildCategory(boolean admin, Category parentCategory, String childName) {
		Category category = findMatchingChildCategory(admin,parentCategory, childName);
		if (!admin && parentCategory == null) {
			//only admins can touch the root directory
			if (category != null && category.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
				throw new RuntimeException("No permission to remove directories in the root");
			} else {
				throw new RuntimeException("directory \"" + childName + "\" not found");
			}
		} else if (category == null) {
			throw new RuntimeException("directory \"" + childName + "\" not found");
		} else {
			removeCategory(admin,category,false);
		}
	}
	
	private void removeCategory(boolean admin, Category category, boolean automaticallyRemoveLinkOnly) {
		if (categoryDAO.getChildren(category).size() > 0) {
			throw new RuntimeException("Cannot remove directory \"" + category.getName() + "\" because it has subdirectories");
		}
		List<Product> prods = productDAO.getProductsInCategory(category);
		if (!prods.isEmpty()) {
			throw new RuntimeException("Cannot remove directory " + category.getName()
					+ " because it has " + prods.size() + " product(s).");
		}
		List<Ad> ads = adDAO.getAdsByMarketCategory(category);
		if (!ads.isEmpty()) {
			throw new RuntimeException("Cannot remove directory " + category.getName()
					+ " because " + ads.size() + " ads are mapped to this category.");
		}
		List<CategoryMapping> categoryMappingList = categoryMappingDAO.getCategoryMappings(category);
		if (!categoryMappingList.isEmpty()) {
			boolean restrictions = false;
			for (CategoryMapping cm : categoryMappingList) {
				if (printCategoryMappingRestriction(category,cm)) {
					restrictions = true;
				}
			}
			if (restrictions) {
				throw new RuntimeException("Cannot remove directory " + category.getName());
			}
			//automaticallyRemoveLinkOnly = false;
			if ((restrictions || !automaticallyRemoveLinkOnly) && admin) {
				println("Directory " + category.getName() + " has " + categoryMappingList.size() + " links to other directories.");
				automaticallyRemoveLinkOnly = UserInputHelper.getBoolean("Remove links as well?",false,in);
			}
			if (!restrictions && automaticallyRemoveLinkOnly && admin) {
				println("Automatically removing " + categoryMappingList.size() + " link(s).");
				for (CategoryMapping cm : categoryMappingList) {
					categoryMappingDAO.delete(cm);
					categoryMappingDAO.flush();
				}
			} else {
				throw new RuntimeException("Cannot remove directory " + category.getName()
						+ " because it has " + categoryMappingList.size() + " links to other directories.");
			}
		}
		categoryDAO.delete(category);
		categoryDAO.flush();
	}
	
	public void moveProducts(boolean admin, String command, List<Category> currentDir) throws IOException {
		refreshCurrentDir(currentDir);
		if (command.length() > 13 && command.substring(13).indexOf("*") >= 0) {
			throw new RuntimeException("moveproducts: wildcards not allowed");
		} else if (command.length() <= 13) {
			throw new RuntimeException("moveproducts: directory name missing");
		}
		String childName = command.substring(13);
		Category category = findMatchingChildCategory(admin,lastEntry(currentDir), childName);
		if (category == null) {
			throw new RuntimeException("directory \"" + childName + "\" not found");
		} else if (!category.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
			//TODO: fix this so that if we get here, then we'll move ad.market_category_id's and
			//no categories of products.
			throw new RuntimeException("moveproducts: only directories under /"
					+ Market.SELLSTAR_MARKET_NAME + " has products, other directories only have links");
		}

		List<Product> prods = productDAO.getProductsWithSellStarCategory(category);
		if (prods.isEmpty()) {
			throw new RuntimeException("moveproducts: there are no products to move from directory " + category.getName());
		}
		
		/* Get all markets to which products are actually listed. */
		List<Market> allMarkets = marketDAO.loadAll();
		List<Market> usedMarkets = new ArrayList<Market>();
		for (Market market : allMarkets) {
			if (!market.getName().equals(Market.GOOGLE_ORDER_MARKET_NAME) && !market.getName().equals(Market.SELLSTAR_MARKET_NAME)
					&& !market.getName().endsWith("SNAPSHOT")) {
				println("Market " + market.getName() + " in use");
				usedMarkets.add(market);
			}
		}
		
		/* Make sure that the category from which we want to move products has mappings to all used markets
		 * (this is so that we will be able to also move ad.market_id to the right place). */
		for (Market market : usedMarkets) {
			List<Category> catMaps = categoryMappingDAO.getCategoryMappingsForMarket(category,market);
			if (catMaps == null || catMaps.size() == 0) {
				throw new RuntimeException("moveproducts: cannot move products because category "
						+ extractPath(getWholePathFromRootFor(category)) + " doesn't have a mapping to market " + market.getName());
			}
		}
		
		Map<Category,Category> marketCatMoving = new HashMap<Category,Category>();
		List<Category> sourceDir = getWholePathFromRootFor(category);
		ArrayList<Category> destinations = new ArrayList<Category>();
		println("There are " + prods.size() + " products in this category.");
		print("Headline to match for (or empty for going through all products): ");
		String match = in.readLine();
		if (match.trim().length() > 0 && match.trim().length() < 4) {
			println("Too short string for matching, going through all products.");
			match = "";
		} else if (match.trim().equals("")) {
			println("Going through all products.");
			match = "";
		}
		for (Product product : prods) {
			if (match.trim().length() > 0 && product.getName().toLowerCase().indexOf(match.toLowerCase()) < 0) {
				println("Skipping " + product.getId() + " (" + product.getName() + ")");
				continue;
			}
			Integer choice = -2;
			/* Print a list of previous destinations. */
			do {
				println("What do you want to do with product " + product.getId() + " (" + product.getName() + ")?");
				println("-4.) Abort (roll back already made changes)");
				println("-3.) Exit (commit already made changes)");
				println("-2.) Keep (default)");
				println("-1.) Show provider product page");
				println(" 0.) Search for destination category");
				if (destinations.size() > 0) {
					for (int i = 0; i < destinations.size(); i++) {
						println(" " + (i+1) + ".) Move to " + extractPath(getWholePathFromRootFor(destinations.get(i))));
					}
				}
				choice = UserInputHelper.getOneNumber("Choose one: ",-4,destinations.size(),true,false,in);
				if (choice == null || choice == -2) {
					println("Keeping");
					break;
				} else if (choice == -1) {
					providerHandler.loadProductPage(product.getProvider(),product.getProviderProdId());
				} else if (choice == -3) {
					return;
				} else if (choice == -4) {
					throw new RuntimeException("Aborting");
				}
			} while (choice == -1);
			if (choice == null || choice == -2) {
				continue;
			}
			Category newCat;
			if (choice == 0) {
				newCat = runBatch(admin, sourceDir,true,true);
			} else {
				newCat = destinations.get(choice-1);
			}
			if (newCat != null) {
				if (newCat.getMarket().getId().equals(category.getMarket().getId())) {
					/* Only perform the move if the destination category has mappings to all used markets
					 * (because if it doesn't, then we don't know how to move ad.market_id correspondingly). */
					boolean canMove = true;
					for (Market market : usedMarkets) {
						List<Category> catMaps = categoryMappingDAO.getCategoryMappingsForMarket(newCat,market);
						if (catMaps == null || catMaps.size() == 0) {
							println("moveproducts: cannot move product to the chosen category because "
									+ extractPath(getWholePathFromRootFor(newCat)) + " doesn't have a mapping to market " + market.getName());
							canMove = false;
						}
					}
					if (canMove) {
						if (!destinations.contains(newCat)) {
							destinations.add(newCat);
						}
						product.setCategory(newCat);
						println("Moved product to " + extractPath(getWholePathFromRootFor(newCat)));
						Map<Category,Category> currMarketCatMoving = getCategoryMoving(category,newCat,marketCatMoving,usedMarkets);
						/* Move ad.market_category_id for the product in question. */
						List<AdTemplate> adTemplates = adTemplateDAO.findByProduct(product);
						int adsSucceeded = 0;
						int adsFailed = 0;
						for (AdTemplate adTemplate : adTemplates) {
							List<Ad> ads = adDAO.findByAdTemplate(adTemplate);
							for (Ad ad : ads) {
								if (currMarketCatMoving.get(ad.getMarketCategory()) != null) {
									ad.setMarketCategory(currMarketCatMoving.get(ad.getMarketCategory()));
									adsSucceeded++;
								} else {
									//we shouldn't normally get here, but we can get here if an ad has at some point been associated
									//to a certain market category through a category mapping which was later removed
									println("Ad " + ad.getId() + " for product " + product.getId() + " wasn't moved because"
											+ " it belongs to market category " + extractPath(getWholePathFromRootFor(ad.getMarketCategory())));
									println("  => Please fix manually.");
									adsFailed++;
								}
							}
						}
						print("Moved " + adsSucceeded + " ads to new categories");
						if (adsFailed > 0) {
							println(" but " + adsFailed + " ads couldn't be moved.");
						} else {
							println("");
						}
					}/* else {
						throw new RuntimeException("Aborting");
					}*/
				} else {
					println("NOT moved - cannot move products between markets.");
				}
			} else {
				println("Not moved.");
			}
		}
		
		/* Finally move ad.market_category_id's. */
		/*println("MOVING MARKET CATEGORIES FOR ADS");
		Iterator<Category> iter = marketCatMoving.keySet().iterator();
		while (iter.hasNext()) {
			Category oldCat = iter.next();
			Category newCat = marketCatMoving.get(oldCat);
			if (!usedMarkets.contains(oldCat.getMarket()) || usedMarkets.contains(newCat.getMarket())) {
				throw new RuntimeException("internal error in moveProducts");
			}
			println("Moving ads for " + extractPath(getWholePathFromRootFor(oldCat)) + " to " + extractPath(getWholePathFromRootFor(newCat)));
			List<Ad> ads = adDAO.getAdsByMarketCategory(oldCat);
			if (ads != null && ads.size() > 0) {
				println("Moving " + ads.size() + " ads for " + extractPath(getWholePathFromRootFor(oldCat))
						+ " to " + extractPath(getWholePathFromRootFor(newCat)));
				boolean move = UserInputHelper.getBoolean("Is this okay?",true,in);
				if (!move) {
					throw new RuntimeException("Aborting");
				} else {
					for (Ad ad : ads) {
						ad.setMarketCategory(newCat);
					}
				}
			}
		}*/
	}
	
	/**
	 * Helper method for moveProducts in order to determine to which categories ads are moved.
	 * 
	 * @param oldSellStarCategory
	 * @param newSellStarCategory
	 * @param marketCatMoving
	 * @param usedMarkets
	 * @return
	 */
	private Map<Category,Category> getCategoryMoving(Category oldSellStarCategory, Category newSellStarCategory,
			Map<Category,Category> marketCatMoving, List<Market> usedMarkets) {
		Map<Category,Category> currMarketCatMoving = new HashMap<Category,Category>();
		for (Market market : usedMarkets) {
			List<Category> oldMarketCategories = categoryMappingDAO.getCategoryMappingsForMarket(oldSellStarCategory,market);
			if (oldMarketCategories == null || oldMarketCategories.size() == 0) {
				//we should never get here, because this was already checked earlier
				throw new RuntimeException("internal error in moveproducts");
			}
			List<Category> newMarketCategories = categoryMappingDAO.getCategoryMappingsForMarket(newSellStarCategory,market);
			if (oldMarketCategories == null || oldMarketCategories.size() == 0) {
				//we should never get here, because this was already checked earlier
				throw new RuntimeException("internal error in moveproducts");
			}
			if (newMarketCategories.size() > 1) {
				for (Category c : oldMarketCategories) {
					if (marketCatMoving.get(c) == null) {
						println("Where do you want to move category " + extractPath(getWholePathFromRootFor(c)));
						for (int i = 0; i < newMarketCategories.size(); i++) {
							Category c2 = newMarketCategories.get(i);
							println((i+1) + ": " + extractPath(getWholePathFromRootFor(c2)));
						}
						int choice2 = UserInputHelper.getOneNumber("Choose one: ",1,newMarketCategories.size(),false,false,in);
						marketCatMoving.put(c,newMarketCategories.get(choice2-1));
					}
					currMarketCatMoving.put(c,marketCatMoving.get(c));
				}
			} else if (oldMarketCategories.size() == 1) {
				if (marketCatMoving.get(oldMarketCategories.get(0)) == null) {
					marketCatMoving.put(oldMarketCategories.get(0), newMarketCategories.get(0));
				}
				currMarketCatMoving.put(oldMarketCategories.get(0), newMarketCategories.get(0));
			} else {
				boolean needToAsk = false;
				for (Category c : oldMarketCategories) {
					if (marketCatMoving.get(c) == null) {
						needToAsk = true;
					}
				}
				if (needToAsk) {
					println("The following mappings exist for the old category " + extractPath(getWholePathFromRootFor(oldSellStarCategory)));
					for (Category c : oldMarketCategories) {
						println(extractPath(getWholePathFromRootFor(c)));
					}
					println("Published ads for those categories for the moved product(s) in question will be moved to category "
							+ extractPath(getWholePathFromRootFor(newMarketCategories.get(0))));
					boolean move = UserInputHelper.getBoolean("Is this okay?",null,in);
					if (!move) {
						throw new RuntimeException("Aborting");
					} else {
						for (Category c : oldMarketCategories) {
							marketCatMoving.put(c, newMarketCategories.get(0));
						}
					}
				}
				for (Category c : oldMarketCategories) {
					currMarketCatMoving.put(c, marketCatMoving.get(c));
				}

			}
		}
		return currMarketCatMoving;
	}
	
	private void removeChildCategories(boolean admin, Category parentCategory, String wildcards) {
		if (!admin && parentCategory == null) {
			throw new RuntimeException("No permission to remove directories in the root");
		}
		List<Category> list = categoryDAO.getChildrenContainingNamePart(parentCategory,wildcards.replaceAll("\\*","%"));
		if (list.isEmpty()) {
			throw new RuntimeException("directories matching \"" + wildcards + "\" not found");
		}
		for (Category child : list) {
			removeCategory(admin,child,false);
		}
	}

	private void createNewChildCategory(boolean admin, Category parentCategory, String childName, Integer marketSpecId) {
		if (parentCategory == null) {
			throw new RuntimeException("Not allowed to create directories in the root");
		}
		Category child = categoryDAO.getChild(parentCategory,childName);
		if (child == null) {
			Category newCategory = new Category();
			newCategory.setName(childName);
			newCategory.setParentCategory(parentCategory);
			newCategory.setMarket(parentCategory.getMarket());
			/* If marketSpecId = null, then set it to the id of the entity (which we won't get until
			 * after the entity is first saved once). */
			if (marketSpecId != null) {
				newCategory.setMarketSpecId(marketSpecId);
				categoryDAO.save(newCategory);
				categoryDAO.flush();
			} else {
				newCategory.setMarketSpecId(9999999);
				categoryDAO.save(newCategory);
				categoryDAO.flush();
				newCategory.setMarketSpecId(newCategory.getId().intValue());
				categoryDAO.update(newCategory);
				categoryDAO.flush();
			}
		} else {
			throw new RuntimeException("directory \"" + childName + "\" already exists");
		}
	}
	
	private Category findMatchingChildCategory(boolean admin, Category parentCategory, String childName) {
		//Only admin can access the K-18 category
		if (!admin && parentCategory != null && parentCategory.getParentCategory() == null && childName.equals("K-18")) {
			return null;
		}
		
		/* First try to get an exact match. */
		Category child = categoryDAO.getChild(parentCategory,childName);
		if (child != null) {
			return child;
		}
		
		/* No exact match, so then see if there exists a unique child starting with the
		 * name "childName". If those are several, then just print out the matching ones. */
		List<Category> list = categoryDAO.getChildrenContainingNamePart(parentCategory,childName + "%");
		
		if (list.size() == 1 && (admin || !list.get(0).getName().equals("K-18"))) {
			return list.get(0);
		}
		for (Category ch : list) {
			if (admin || !ch.getName().equals("K-18")) {
				println(ch.getName());
			}
		}
		
		return null;
	}
	
	private Map<Integer,Category> printSubCategories(boolean admin, Category parentCategory) {
		return printSubCategories(admin,categoryDAO.getChildren(parentCategory));
	}

	private Map<Integer,Category> printSubCategories(boolean admin, Category parentCategory, String wildcards) {
		return printSubCategories(admin,categoryDAO.getChildrenContainingNamePart(parentCategory,wildcards.replaceAll("\\*","%")));
	}

	private Map<Integer,Category> printSubCategories(boolean admin, List<Category> list) {
		Map<Integer,Category> map = new HashMap<Integer,Category>();
		int count = 1;
		if (list.size() == 0 || (list.get(0).getParentCategory() != null
				&& !list.get(0).getParentCategory().getName().equals(Market.SELLSTAR_MARKET_NAME))) {
			println("[0] BACK");
		}
		for (Category child : list) {
			if (admin || child.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
				//Only admin can access the K-18 category
				if (admin || !child.getName().equals("K-18")) {
					/*if (admin && !child.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
						print("[" + child.getMarketSpecId() + "] ");
					} else if (!admin && child.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
						print("[" + count + "] ");
						map.put(count++,child);
					}*/
					print("[" + count + "] ");
					map.put(count++,child);
					print(child.getName());
					if (categoryDAO.getChildren(child).size() > 0) {
						print("/");
					} else {
						print(" ***");
					}
					println("");

					/* Show links in case admin == true. */
					if (admin) {
						List<CategoryMapping> mapList = categoryMappingDAO.getCategoryMappings(child);
						for (CategoryMapping cm : mapList) {
							Category mapped = null;
							if (cm.getCategory1().getId().equals(child.getId())) {
								mapped = cm.getCategory2();
							} else {
								mapped = cm.getCategory1();
							}
							println("        [<-> " + extractPath(getWholePathFromRootFor(mapped)) + "]");
						}
					}
				}
			}
		}
		return map;
	}
	
	public String extractPath(List<Category> path) {
		StringBuffer strBuf = new StringBuffer();
		if (path.isEmpty()) {
			strBuf.append("/");
		} else {
			for (Category category : path) {
				strBuf.append("/" + category.getName());
			}
		}
		return strBuf.toString();
	}

	public void setIn(BufferedReader in) {
		this.in = in;
	}

	public void setOut(PrintWriter out) {
		this.out = out;
	}

}
