package fi.jonix.huutonet.order.hautala;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.order.ProviderEmailFilter;
import fi.jonix.huutonet.order.ProviderOrder;

@Component(value = "hautalaProcessor")
public class HautalaProcessor extends ProviderEmailFilter {

	public static final Logger logger = Logger.getLogger(HautalaProcessor.class);
	
	public static final String ORDER_PLACED_ID_STRING = "Tilauksenne on käsittelyssä ja tuotteet lähetetään tilauksessa ilmoittamaanne osoitteeseen";

	private static final String SALES_AT_HAUTALA = "someuser@gmail.com";
	
	@Autowired
	private ProviderDAO providerDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private OrderDAO orderDAO;
	
	private boolean processOrderPlacedReceipt(String contents) throws Exception {
		String providerOrderId = getFieldValue(contents,"Tilausnumero:");
		logger.debug("providerOrderId = " + providerOrderId);
		Provider provider = providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		OrderBundle bundle = orderBundleDAO.getOrderBundleByProviderOrderId(providerOrderId,provider);
		if (bundle == null) {
			/* Now and then it happens that paypal sends out an email so quickly after placeorder that
			 * the place ordering thread hasn't yet stored the new ad to the database. In those cases it works
			 * if we try again a little later to process the same email. */
			throw new ProcessEmailLaterException("HautalaProcessor.processOrderPlacedReceipt: bundle.providerOrderId "
					+ providerOrderId + " not found in database.");
		}
		logger.info("Order placed receipt received from Hautala regarding order bundle "
				+ bundle.getId() + " (order confirmation from Hautala).");
		ProviderOrder.orderBundleConfirmedByProvider(bundle,orderDAO.getOrdersInBundle(bundle));
		return true;
	}

	@Override
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo)
			throws Exception {
		/* Check if it's an order placed receipt message from FocalPrice. */
		String contents = matchEmail(message,SALES_AT_HAUTALA,"Kiitos tilauksestasi!",true,ORDER_PLACED_ID_STRING);
		if (contents != null) {
			return processOrderPlacedReceipt(contents);
		}
		return false;
	}

}
