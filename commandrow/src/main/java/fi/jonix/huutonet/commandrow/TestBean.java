package fi.jonix.huutonet.commandrow;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.context.support.AbstractApplicationContext;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Settings;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;

/**
 * This is a bean for which the transaction boundaries are defined.
 * 
 */
public class TestBean {
	
	public void run(AbstractApplicationContext applicationContext) {
		AdDAO adDAO = (AdDAO) applicationContext.getBean("adDAO");
		Ad adTest = adDAO.get(2L);
		if (adTest != null) {
			System.out.println("adTest = " + adTest);
			System.out.println("adTest.market = " + adTest.getMarket());
			System.out.println("adTest.market.name = " + adTest.getMarket().getName());
		}
		ProviderDAO providerDAO = (ProviderDAO) applicationContext.getBean("providerDAO");
	}
	
}
