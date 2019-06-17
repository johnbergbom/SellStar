package fi.jonix.huutonet.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryDAO;

public class CategoryResolverThreadBean {

	@Autowired
	AdTemplateDAO adTemplateDAO;
	
	@Autowired
	CategoryDAO categoryDAO;
	
	AdTemplate adTemplate;

	private final Object mutex = new Object();
	
	List<Category> listHeadLineAndProvCat = new ArrayList<Category>();
	List<Category> listHeadlineAndSellStarCat = new ArrayList<Category>();
	List<Category> finalCandidates = new ArrayList<Category>();
	List<Category> disjointCategories = new ArrayList<Category>();
	List<Category> listHeadline = new ArrayList<Category>();
	List<CategoryHits> disjointCategoryHits = new ArrayList<CategoryHits>();
	private boolean done;
	
	public void initialize(){
		this.listHeadline = new ArrayList<Category>();
		this.listHeadLineAndProvCat =  new ArrayList<Category>();
		this.listHeadlineAndSellStarCat = new ArrayList<Category>();
		this.disjointCategories = new ArrayList<Category>();
		this.disjointCategoryHits = new ArrayList<CategoryHits>();
		this.finalCandidates = new ArrayList<Category>();
		this.adTemplate = null;
		synchronized (mutex) {
			done = false;
		}
	}

	public void stopWork() {
		synchronized (mutex) {
			done = true;
		}
	}
	
	public void work() {
		/*
		 * First get categories that have the same provider category AND matches
		 * by the headline.
		 */
		List<AdTemplate> adTemplateList = adTemplateDAO.findByProviderCategory(adTemplate.getProduct().getProviderCategory());
		
		while (adTemplate.getHeadline() == null) {
			synchronized (mutex) {
				try {
					mutex.wait(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (done) {
					return;
				}
			}
		}
		
		try {
			this.listHeadLineAndProvCat = getPossibleCategoriesBasedOnHeadline(adTemplate.getHeadline(), adTemplateList);
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		List<CategoryHits> cHits = new ArrayList<CategoryHits>();

		/*
		 * Then get all sellstar categories matching the specified provider
		 * category.
		 */
		Map<Long, Long> catCountMap = categoryDAO
				.getSellStarCategoryCountForProviderCategory(adTemplate.getProduct().getProviderCategory());
		Iterator<Long> iter = catCountMap.keySet().iterator();
		while (iter.hasNext()) {
			Long key = iter.next();
			cHits.add(new CategoryHits(categoryDAO.get(key), catCountMap.get(key)));
		}
		Collections.sort(cHits);
		Collections.reverse(cHits); // get most matches first

		/* Then get categories that match by SellStar category and headline. */
		adTemplateList = new ArrayList<AdTemplate>();
		for (CategoryHits chi : cHits) {
			adTemplateList.addAll(adTemplateDAO.findBySellStarCategory(chi.getSellStarCategory()));
		}
		
		try {
			this.listHeadlineAndSellStarCat = getPossibleCategoriesBasedOnHeadline(adTemplate.getHeadline(), adTemplateList);
		} catch (Exception e) {
			e.printStackTrace();
		}

		List<AdTemplate> allAdTemplates = adTemplateDAO.loadAll();
		
		try {
			this.listHeadline = getPossibleCategoriesBasedOnHeadline(adTemplate.getHeadline(), allAdTemplates);
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.finalCandidates = new ArrayList<Category>();
		if (listHeadLineAndProvCat.size() > 0) {
			for (Category c : listHeadLineAndProvCat) {
				if (!finalCandidates.contains(c)) {
					finalCandidates.add(c);
				}
			}
		}

		this.disjointCategories = getDisjointCategories(finalCandidates, listHeadlineAndSellStarCat);

		if (disjointCategories.size() > 0) {
			for (Category c : disjointCategories) {
				finalCandidates.add(c);
			}
		}

		if (listHeadLineAndProvCat.size() == 0 && disjointCategories.size() == 0) {
			for (Category c : listHeadline) {
				finalCandidates.add(c);
			}
		}

		this.disjointCategoryHits = getDisjointCategoryHits(finalCandidates, cHits);
		synchronized (mutex) {
			done = true;
		}
	}

	private List<Category> getPossibleCategoriesBasedOnHeadline(String headline, List<AdTemplate> adTemplates) throws Exception {
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

	private String headLine2Lucene(String headline) {
		return headline.replaceAll("\\*", "x").replaceAll("\\?", "").replaceAll("\\+", "").replaceAll("\"", "");
	}

	private void addDoc(IndexWriter w, String headline, Category category) throws IOException {
		Document doc = new Document();
		doc.add(new Field("title", headline, Field.Store.YES, Field.Index.ANALYZED));
		doc.add(new Field("categoryId", "" + category.getId(), Field.Store.YES, Field.Index.ANALYZED));
		w.addDocument(doc);
	}

	/**
	 * 
	 * @param source
	 * @param target
	 * @return A list of categories that exist in target and not in source.
	 */
	private List<Category> getDisjointCategories(List<Category> source, List<Category> target) {
		List<Category> retList = new ArrayList<Category>();
		for (Category c : target) {
			if (!source.contains(c)) {
				retList.add(c);
			}
		}
		return retList;
	}

	/**
	 * 
	 * @param source
	 * @param target
	 * @return A list of categories that exist in target and not in source.
	 */
	private List<CategoryHits> getDisjointCategoryHits(List<Category> source, List<CategoryHits> target) {
		List<CategoryHits> retList = new ArrayList<CategoryHits>();
		for (CategoryHits chi : target) {
			if (!source.contains(chi.getSellStarCategory())) {
				retList.add(chi);
			}
		}
		return retList;
	}

	public List<Category> getListHeadLineAndProvCat() {
		return listHeadLineAndProvCat;
	}

	public List<Category> getListHeadlineAndSellStarCat() {
		return listHeadlineAndSellStarCat;
	}

	public List<Category> getFinalCandidates() {
		return finalCandidates;
	}

	public List<Category> getDisjointCategories() {
		return disjointCategories;
	}

	public List<Category> getListHeadline() {
		return listHeadline;
	}

	public List<CategoryHits> getDisjointCategoryHits() {
		return disjointCategoryHits;
	}

	public AdTemplate getAdTemplate() {
		return adTemplate;
	}

	public void setAdTemplate(AdTemplate adTemplate) {
		this.adTemplate = adTemplate;
	}

	public boolean isDone() {
		return done;
	}

}
