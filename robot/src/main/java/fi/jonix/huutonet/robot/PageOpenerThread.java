package fi.jonix.huutonet.robot;

import fi.jonix.huutonet.order.ProviderOrder;

public class PageOpenerThread extends Thread {
	
	ProviderOrder providerOrder;
	private String providerProductId;
	private boolean success = false;
	private boolean done = false;
	private String url;
	CustomSelenium selenium;
	
	public PageOpenerThread(ProviderOrder providerOrder, String providerProductId) {
		this.providerOrder = providerOrder;
		this.providerProductId = providerProductId;
	}
	
	public PageOpenerThread(CustomSelenium selenium, String url) {
		this.url = url;
		this.selenium = selenium;
	}
	
	public void run() {
		try {
			if(this.url == null){
				this.providerOrder.loadProductPage(providerProductId, 2, "20000");
			} else {
				for(int i=0; i < 3; i++){
					if(this.done)
						break;
					try {
						selenium.open(this.url, 1, "20000");
					} catch (Exception e){}
				}
			}
			this.success = true;
		} catch (Exception e) {}
		done = true;
	}

	public boolean isSuccess() {
		return success;
	}
	
	public boolean isDone() {
		return done;
	}
	
	public void stopWork() {
		this.done = true;
	}
	
}
