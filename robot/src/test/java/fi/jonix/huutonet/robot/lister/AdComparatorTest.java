package fi.jonix.huutonet.robot.lister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.test.AbstractDependencyInjectionSpringContextTests;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;

public class AdComparatorTest extends AbstractDependencyInjectionSpringContextTests{
	
	public void testCompare(){
		/*Ad ad1 = new Ad();
		AdTemplate adTemplate1 = new AdTemplate();
		adTemplate1.setId(1L);
		adTemplate1.setCategory("category1");
		adTemplate1.setSubCategory("subCategory1");
		ad1.setStartPriceInEuro(10.0);
		ad1.setAdTemplate(adTemplate1);
		
		Ad ad2 = new Ad();
		AdTemplate adTemplate2 = new AdTemplate();
		adTemplate2.setId(2L);
		adTemplate2.setCategory("category1");
		adTemplate2.setSubCategory("subCategory1");
		ad2.setStartPriceInEuro(20.0);
		ad2.setAdTemplate(adTemplate2);
		
		Ad ad3 = new Ad();
		AdTemplate adTemplate3 = new AdTemplate();
		adTemplate3.setId(3L);
		adTemplate3.setCategory("category1");
		adTemplate3.setSubCategory("subCategory1");
		ad3.setStartPriceInEuro(30.0);
		ad3.setAdTemplate(adTemplate3);
		
		Ad ad4 = new Ad();
		AdTemplate adTemplate4 = new AdTemplate();
		adTemplate4.setId(4L);
		adTemplate4.setCategory("category1");
		adTemplate4.setSubCategory("subCategory2");
		ad4.setStartPriceInEuro(40.0);
		ad4.setAdTemplate(adTemplate4);
		
		Ad ad5 = new Ad();
		AdTemplate adTemplate5 = new AdTemplate();
		adTemplate5.setId(5L);
		adTemplate5.setCategory("category1");
		adTemplate5.setSubCategory("subCategory2");
		ad5.setStartPriceInEuro(50.0);
		ad5.setAdTemplate(adTemplate5);
		
		Ad ad6 = new Ad();
		AdTemplate adTemplate6 = new AdTemplate();
		adTemplate6.setId(6L);
		adTemplate6.setCategory("category1");
		adTemplate6.setSubCategory("subCategory2");
		ad6.setStartPriceInEuro(60.0);
		ad6.setAdTemplate(adTemplate6);
		
		List<Ad> ads = new ArrayList<Ad>();
		ads.add(ad1);
		ads.add(ad2);
		ads.add(ad3);
		ads.add(ad4);
		ads.add(ad5);
		ads.add(ad6);
		
		Collections.sort(ads, new AdComparator("subCategory1", 30.0));
		Collections.reverse(ads);
		
		assertEquals(3, ads.get(0).getAdTemplate().getId().intValue());
		assertEquals(2, ads.get(1).getAdTemplate().getId().intValue());
		assertEquals(1, ads.get(2).getAdTemplate().getId().intValue());
		assertEquals(4, ads.get(3).getAdTemplate().getId().intValue());
		assertEquals(5, ads.get(4).getAdTemplate().getId().intValue());
		assertEquals(6, ads.get(5).getAdTemplate().getId().intValue());*/
		
	}
	
}
