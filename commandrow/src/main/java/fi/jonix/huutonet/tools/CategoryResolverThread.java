package fi.jonix.huutonet.tools;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.AdTemplate;


public class CategoryResolverThread extends Thread {
	
	AdTemplate adTemplate;
	
	public CategoryResolverThread(AdTemplate adTemplate) {
		this.adTemplate = adTemplate;
	}
	
	@Override
	public void run() {
		CategoryResolverThreadBean categoryResolverThreadBean = (CategoryResolverThreadBean) ApplicationContextPlaceholder.applicationContext.getBean("categoryResolverThreadBean");
		categoryResolverThreadBean.setAdTemplate(this.adTemplate);
		categoryResolverThreadBean.work();
	}

}
