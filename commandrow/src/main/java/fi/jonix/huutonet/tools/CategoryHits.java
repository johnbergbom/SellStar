package fi.jonix.huutonet.tools;

import fi.jonix.huutonet.domain.model.Category;

public class CategoryHits implements Comparable<CategoryHits>{
	
	private Category sellStarCategory;
	private long count;
	
	public CategoryHits(Category sellStarCategory, long count) {
		this.setSellStarCategory(sellStarCategory);
		this.setCount(count);
	}
	
	public int compareTo(CategoryHits another) {
		if (getCount() > another.getCount()) {
			return 1;
		} else if (getCount() < another.getCount()) {
			return -1;
		}
		return 0;
	}

	public void setSellStarCategory(Category sellStarCategory) {
		this.sellStarCategory = sellStarCategory;
	}

	public Category getSellStarCategory() {
		return sellStarCategory;
	}

	public void setCount(long count) {
		this.count = count;
	}

	public long getCount() {
		return count;
	}
	
}
