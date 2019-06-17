package fi.jonix.huutonet.market.huuto;

import java.util.Comparator;
import java.util.Map;

import fi.jonix.huutonet.domain.model.Ad;

public class AdComparator implements Comparator<Ad> {
	
	private Map<Ad,Long> adScores;
	
	public AdComparator(Map<Ad,Long> adScores){
		this.adScores = adScores;
	}

	public int compare(Ad ad1, Ad ad2) {
		Long score1 = adScores.get(ad1);
		Long score2 = adScores.get(ad2);
		if (score1.equals(score2)) {
			return 0;
		} else if (score1.longValue() > score2.longValue()) {
			return 1;
		} else {
			return -1;
		}
	}

}
