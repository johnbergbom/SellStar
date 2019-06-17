package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Settings;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.Verification;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAOImpl;
import fi.jonix.huutonet.domain.model.dao.VerificationDAO;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "moneyTransfererHelper")
public class MoneyTransfererHelper {

	public static final Logger logger = Logger.getLogger(MoneyTransfererHelper.class);
	
	public static String TRANSFER_THRESHOLD = "1500";
	
	@Autowired
	private TransactionDAO transactionDAO;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private VerificationDAO verificationDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	//@Autowired
	//private FaultyPaymentFixer faultyPaymentFixer;

	public VirtualCoin calculateSumToTransfer(StringBuffer verification) throws ProcessEmailLaterException {
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		StringBuffer tempBuf = new StringBuffer();

		/* Safety check. */
		Long currency = null;
		Currency currencyObj = null;
		if (transactionDAO.getNbrTransactionsForCurrency(Currency.EURO)
				!= transactionDAO.getTotalNbrTransactions()) {
			throw new RuntimeException("Not yet supported: different currencies in the transaction table.");
		} else {
			currency = Currency.EURO;
			currencyObj = currencyDAO.get(currency);
		}

		/* Skip transfer if the account balance is below the transfer threshold. */
		VirtualCoin balance = new VirtualCoin(currency,transactionDAO.getBalance());
		if (balance.getValue().compareTo(new BigDecimal(TRANSFER_THRESHOLD)) < 0) {
			logger.debug("Account balance only " + balance + " which is lower than threshold ("
					+ TRANSFER_THRESHOLD + "). Skipping money transfer.");
			return new VirtualCoin(currency,BigDecimal.ZERO);
		}
		//faultyPaymentFixer.fixFaultyPayments(false); //first fix possible faulty payments
		
		/* Set some header information. */
		StringBuffer header = new StringBuffer();
		header.append("Päivämäärä: " + dateFormat.format(new Date()) + "\n\n");
		List<BigDecimal> distinctCustomerVatPercentages = orderDAO.getDistinctCustomerVatPercentages();
		for (BigDecimal customerVat : orderBundleDAO.getDistinctCustomerVatPercentages()) {
			if (!distinctCustomerVatPercentages.contains(customerVat)) {
				distinctCustomerVatPercentages.add(customerVat);
			}
		}
		header.append("Tilanne jakson alussa:\n");
		for (BigDecimal customerVat : distinctCustomerVatPercentages) {
			Verification verif = verificationDAO.getVerification(customerVat, currencyObj);
			if (verif != null) {
				BigDecimal vatPerc = verif.getVatPercentage().multiply(new BigDecimal("100.00"));
				VirtualCoin totalInclVatVC = new VirtualCoin(verif.getCurrency(),verif.getTotalSalesPriceInclVat());
				String totalInclVatVCStr = CurrencyFormatter.print(totalInclVatVC, Statics.FINNISH);
				header.append("Alv " + MathUtilities.roundTo2Decimals(vatPerc)
						+ "% myynti yhteensä jakson alussa (sis. alv):          " + totalInclVatVCStr + "\n");
				VirtualCoin totalExclVatVC = new VirtualCoin(verif.getCurrency(),verif.getTotalSalesPriceExclVat());
				String totalExclVatVCStr = CurrencyFormatter.print(totalExclVatVC, Statics.FINNISH);
				header.append("Alv " + MathUtilities.roundTo2Decimals(vatPerc)
						+ "% myynti yhteensä jakson alussa (ilman alvia):       " + totalExclVatVCStr + "\n");
			} else {
				/* If now verification row was found for this vat percentage, then that's because
				 * we didn't have any sales for this alv percentage last time a verification was made. */
				logger.warn("No verification information was found for alv " + customerVat + " - using zero.");
				BigDecimal vatPerc = customerVat.multiply(new BigDecimal("100.00"));
				VirtualCoin totalInclVatVC = new VirtualCoin(currency,BigDecimal.ZERO);
				String totalInclVatVCStr = CurrencyFormatter.print(totalInclVatVC, Statics.FINNISH);
				header.append("Alv " + MathUtilities.roundTo2Decimals(vatPerc)
						+ "% myynti yhteensä jakson alussa (sis. alv):          " + totalInclVatVCStr + "\n");
				header.append("Alv " + MathUtilities.roundTo2Decimals(vatPerc)
						+ "% myynti yhteensä jakson alussa (ilman alvia):       " + totalInclVatVCStr + "\n");
			}
		}
		header.append("\n");
		
		/* First handle new orders/bundles. */
		List<Order> untransferedOrders = orderDAO.getOrdersNotTransferedToVerification();
		List<OrderBundle> untransferedBundles = orderBundleDAO.getBundlesNotTransferedToVerification();
		if (untransferedBundles.size() > 0 || untransferedOrders.size() > 0) {
			BigDecimal totalCostInclVat = BigDecimal.ZERO;
			BigDecimal totalCostExclVat = BigDecimal.ZERO;
			BigDecimal totalTargetedPaidInclVat = BigDecimal.ZERO;
			Map<Long,Date> bundleDates = new HashMap<Long,Date>();
			tempBuf.append("-------------------------------------------------------------------------------------------------------\n");
			tempBuf.append("UUDET/MUUTETUT TILAUKSET (jos cost_incl_vat = 0 ja cost_excl_vat = 0, niin kyseessä on peruttu tilaus):\n");
			tempBuf.append("-------------------------------------------------------------------------------------------------------\n");
			tempBuf.append("    date    | bundle_id | order_id | vat | cost_incl_vat | cost_excl_vat | paid_incl_vat | customer\n");
			tempBuf.append("------------+-----------+----------+-----+---------------+---------------+---------------+---------\n");
			
			/* First go through the orders and store the dates of the orders so that they can be printed
			 * out for the bundles (the bundles don't have any dates specified, so for the bundles pick
			 * the date of the order). */
			StringBuffer orderStrBuf = new StringBuffer();
			for (Order order : untransferedOrders) {
				if (!order.getOrderBundle().getUsedCustomerCurrency().getId().equals(Currency.EURO)) {
					throw new RuntimeException("Not yet supported: orders having different customer currency than euro.");
				}
				Date creationDate = order.getCreationDate();
				orderStrBuf.append(" ");
				StringUtilities.printField("   date   ".length(),dateFormat.format(creationDate),orderStrBuf,true);
				orderStrBuf.append(" | ");
				if (bundleDates.get(order.getOrderBundle().getId()) == null ||
						creationDate.getTime() < bundleDates.get(order.getOrderBundle().getId()).getTime()) {
					bundleDates.put(order.getOrderBundle().getId(), creationDate);
				}
				StringUtilities.printField("bundle_id".length(),order.getOrderBundle().getId()+"",orderStrBuf,true);
				orderStrBuf.append(" | ");
				StringUtilities.printField("order_id".length(),order.getId()+"",orderStrBuf,true);
				orderStrBuf.append(" | ");
				BigDecimal vatPerc = order.getCustomerVatPercentage().multiply(new BigDecimal("100.00"));
				StringUtilities.printField("vat".length(),MathUtilities.roundTo2Decimals(vatPerc) + "%",orderStrBuf,true);
				orderStrBuf.append(" | ");
				BigDecimal costInclVat = order.getFinalSalesPriceInMarketCurrencyInclVat();
				totalCostInclVat = totalCostInclVat.add(costInclVat);
				VirtualCoin costInclVatVC = new VirtualCoin(order.getOrderBundle().getUsedCustomerCurrency(),costInclVat);
				String costInclVatStr = CurrencyFormatter.print(costInclVatVC, Statics.FINNISH);
				StringUtilities.printField("cost_incl_vat".length(),costInclVatStr,orderStrBuf,true);
				orderStrBuf.append(" | ");
				BigDecimal costExclVat = order.getFinalSalesPriceInMarketCurrencyExclVat();
				totalCostExclVat = totalCostExclVat.add(costExclVat);
				VirtualCoin costExclVatVC = new VirtualCoin(order.getOrderBundle().getUsedCustomerCurrency(),costExclVat);
				String costExclVatStr = CurrencyFormatter.print(costExclVatVC, Statics.FINNISH);
				StringUtilities.printField("cost_excl_vat".length(),costExclVatStr,orderStrBuf,true);
				orderStrBuf.append(" | ");
				BigDecimal targetedPaid = order.getTargetedPaidInMarketCurrency();
				totalTargetedPaidInclVat = totalTargetedPaidInclVat.add(targetedPaid);
				VirtualCoin targetedPaidVC = new VirtualCoin(order.getOrderBundle().getUsedCustomerCurrency(),targetedPaid);
				String targetedPaidStr = CurrencyFormatter.print(targetedPaidVC, Statics.FINNISH);
				StringUtilities.printField("paid_incl_vat".length(),targetedPaidStr,orderStrBuf,true);
				orderStrBuf.append(" | ");
				StringUtilities.printField("customer".length(),order.getOrderBundle().getCustomer().getId()+"",orderStrBuf,true);
				orderStrBuf.append("\n");
				order.setTransferedToVerification(true);
			}
			
			/* Then go through the bundles. */
			StringBuffer bundleStrBuf = new StringBuffer();
			for (OrderBundle bundle : untransferedBundles) {
				if (!bundle.getUsedCustomerCurrency().getId().equals(Currency.EURO)) {
					throw new RuntimeException("Not yet supported: orders having different customer currency than euro.");
				}
				Date creationDate = bundleDates.get(bundle.getId());
				bundleStrBuf.append(" ");
				StringUtilities.printField("   date   ".length(),dateFormat.format(creationDate),bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				StringUtilities.printField("bundle_id".length(),bundle.getId()+"",bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				StringUtilities.printField("order_id".length(),"-",bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				BigDecimal vatPerc = bundle.getCustomerShippingVatPercentage().multiply(new BigDecimal("100.00"));
				StringUtilities.printField("vat".length(),MathUtilities.roundTo2Decimals(vatPerc) + "%",bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				BigDecimal costInclVat = bundle.getCustomerShippingCostInMarketCurrencyInclVat();
				totalCostInclVat = totalCostInclVat.add(costInclVat);
				VirtualCoin costInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),costInclVat);
				String costInclVatStr = CurrencyFormatter.print(costInclVatVC, Statics.FINNISH);
				StringUtilities.printField("cost_incl_vat".length(),costInclVatStr,bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				BigDecimal costExclVat = bundle.getCustomerShippingCostInMarketCurrencyExclVat();
				totalCostExclVat = totalCostExclVat.add(costExclVat);
				VirtualCoin costExclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),costExclVat);
				String costExclVatStr = CurrencyFormatter.print(costExclVatVC, Statics.FINNISH);
				StringUtilities.printField("cost_excl_vat".length(),costExclVatStr,bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				BigDecimal targetedPaid = bundle.getTargetedPaidShippingCostInMarketCurrency();
				totalTargetedPaidInclVat = totalTargetedPaidInclVat.add(targetedPaid);
				VirtualCoin targetedPaidVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),targetedPaid);
				String targetedPaidStr = CurrencyFormatter.print(targetedPaidVC, Statics.FINNISH);
				StringUtilities.printField("paid_incl_vat".length(),targetedPaidStr,bundleStrBuf,true);
				bundleStrBuf.append(" | ");
				StringUtilities.printField("customer".length(),bundle.getCustomer().getId()+"",bundleStrBuf,true);
				bundleStrBuf.append("\n");
				bundle.setTransferedToVerification(true);
			}
			
			/* Print out the bundles first, then the orders and finally the total sum. */
			tempBuf.append(bundleStrBuf.toString());
			tempBuf.append(orderStrBuf.toString());
			tempBuf.append("------------+-----------+----------+-----+---------------+---------------+---------------+---------\n");
			tempBuf.append("summa:                                     ");
			VirtualCoin sumVC = new VirtualCoin(currency,totalCostInclVat);
			String sumStr = CurrencyFormatter.print(sumVC, Statics.FINNISH);
			StringUtilities.printField("cost_incl_vat".length(),sumStr,tempBuf,true);
			sumVC = new VirtualCoin(currency,totalCostExclVat);
			sumStr = CurrencyFormatter.print(sumVC, Statics.FINNISH);
			StringUtilities.printField(" | cost_excl_vat".length(),sumStr,tempBuf,true);
			sumVC = new VirtualCoin(currency,totalTargetedPaidInclVat);
			sumStr = CurrencyFormatter.print(sumVC, Statics.FINNISH);
			StringUtilities.printField(" | paid_incl_vat".length(),sumStr,tempBuf,true);
			tempBuf.append("\n(" + (untransferedBundles.size() + untransferedOrders.size()) + " rows)\n\n");
		} else {
			tempBuf.append("Ei uusia tilauksia.\n\n");
		}
		
		/* Then handle new transactions that are mapped to an order. */
		StringBuffer transStrBuf = new StringBuffer();
		List<Transaction> untransferedTransactions = transactionDAO.getTransactionsNotTransferedToVerification();
		int includedTrans = 0;
		BigDecimal mappedTransactionsTotalSum = BigDecimal.ZERO;
		if (untransferedTransactions.size() > 0) {
			transStrBuf.append("------------------------------------------------------------------------------------------\n");
			transStrBuf.append("KOHDISTETUT TRANSAKTIOT (SISÄLTÄÄ SISÄÄNTULEVAT MAKSUT JA ULOSLÄHTEVÄT ASIAKASHYVITYKSET):\n");
			transStrBuf.append("------------------------------------------------------------------------------------------\n");
			transStrBuf.append("   id |    date    | message                   |   order_id |     amount\n");
			transStrBuf.append("------+------------+---------------------------+------------+-----------\n");
			for (Transaction transaction : untransferedTransactions) {
				if (!transaction.getCurrency().getId().equals(Currency.EURO)) {
					throw new RuntimeException("Transaction " + transaction.getId() + " has an unsupported currency.");
				}
				if (transaction.getAmount().compareTo(new BigDecimal("135.0")) > 0
						|| (transaction.getAmount().compareTo(new BigDecimal("-50.0")) < 0
								&& (transaction.getOperatorDescription() == null
								|| !transaction.getOperatorDescription().equals(TransactionDAOImpl.QD_TRANSFER_IDENTIFICATION)))) {
					/* Fishy, where did this transaction come from? */
					throw new RuntimeException("Transaction " + transaction.getId()
							+ " is suspiciously large (positively or negatively). Is something fishy going on?");
				}
				if (transaction.getOrder() != null) {
					includedTrans++;
					transStrBuf.append(" ");
					StringUtilities.printField("  id".length(),transaction.getId()+"",transStrBuf,true);
					transStrBuf.append(" | ");
					StringUtilities.printField("   date   ".length(),dateFormat.format(transaction.getDate()),transStrBuf,true);
					transStrBuf.append(" | ");
					StringUtilities.printField("message                  ".length(),transaction.getMessage(),transStrBuf,false);
					transStrBuf.append(" | ");
					StringUtilities.printField("  order_id".length(),transaction.getOrder().getId()+"",transStrBuf,true);
					transStrBuf.append(" | ");
					mappedTransactionsTotalSum = mappedTransactionsTotalSum.add(transaction.getAmount());
					VirtualCoin transactionVC = new VirtualCoin(transaction.getCurrency(),transaction.getAmount());
					String sum = CurrencyFormatter.print(transactionVC, Statics.FINNISH);
					StringUtilities.printField("    amount".length(),sum,transStrBuf,true);
					transStrBuf.append("\n");
					transaction.setTransferedToVerification(true);
				} else {
					if (transaction.getAmount().compareTo(BigDecimal.ZERO) < 0
							&& (transaction.getOperatorDescription() == null
							|| !transaction.getOperatorDescription().equals(TransactionDAOImpl.QD_TRANSFER_IDENTIFICATION))) {
						/* Transactions not associated with any order are only allowed if they are positive
						 * (since refunds to customers always need to be associated with some order). */
						throw new RuntimeException("Transaction " + transaction.getId()
								+ " is negative and isn't associated with any order.");
					} else if (transaction.getOperatorDescription() == null
							|| transaction.getOperatorDescription().startsWith("Unknown payment")) {
						/* All transactions not associated with any order should have an
						 * operator description starting with "Unknown payment" unless they
						 * are of type "Transfer from SellStar to QD" */
						logger.info("Skipping unmapped transaction " + transaction.getId() + ".");
					} else if (transaction.getOperatorDescription().equals(TransactionDAOImpl.QD_TRANSFER_IDENTIFICATION)) {
						logger.info("Skipping transaction " + transaction.getId() + " because it's a \"transfer to qd\".");
						transaction.setTransferedToVerification(true);
					} else {
						logger.warn("Skipping unmapped transaction " + transaction.getId() + " having operator description "
								+ transaction.getOperatorDescription());
					}
				}
			}
		}
		
		/* Print out the transaction information. */
		VirtualCoin transactionTotalSumVC = new VirtualCoin(currency,mappedTransactionsTotalSum);
		String transactionTotalSumVCStr = CurrencyFormatter.print(transactionTotalSumVC, Statics.FINNISH);
		if (includedTrans > 0) {
			tempBuf.append(transStrBuf.toString());
			tempBuf.append("------+------------+---------------------------+------------+-----------\n");
			tempBuf.append("summa:                                                        ");
			StringUtilities.printField("    amount".length(),transactionTotalSumVCStr,tempBuf,true);
			tempBuf.append("\n(" + includedTrans + " rows)\n\n");
		} else {
			tempBuf.append("Ei uusia kohdistettuja transaktioita.\n\n");
		}

		/* Print out the sales information. The sales information is generated using the following logics:
		 * sales vat = what it should be according to the database - what it was in the beginning of the period */
		header.append("Tilanne jakson lopussa:\n");
		for (BigDecimal customerVat : distinctCustomerVatPercentages) {
			Verification verif = verificationDAO.getVerification(customerVat, currencyObj);
			if (verif == null) {
				/* If no verification row was found for this vat percentage, then that's because
				 * we didn't have any sales for this alv percentage last time a verification was made. */
				logger.warn("No verification information was found for alv " + customerVat + " - creating a new one.");
				verif = new Verification();
				verif.setCurrency(currencyObj);
				verif.setTotalSalesPriceInclVatCorrection(BigDecimal.ZERO);
				verif.setTotalSalesPriceExclVatCorrection(BigDecimal.ZERO);
				verif.setVatPercentage(customerVat);
				verif.setTotalSalesPriceInclVat(BigDecimal.ZERO);
				verif.setTotalSalesPriceExclVat(BigDecimal.ZERO);
			}
			String alvPercentageString = MathUtilities.roundTo2Decimals(customerVat.multiply(new BigDecimal("100")));
			
			/* Handle sales including vat. */
			logger.debug("orderDAO.getTotalSalesInclVat = " + orderDAO.getTotalSalesInclVat(currencyObj,customerVat)
					+ ", orderBundleDAO.getTotalSalesInclVat = " + orderBundleDAO.getTotalSalesInclVat(currencyObj,customerVat));
			if (verif.getTotalSalesPriceInclVatCorrection().compareTo(BigDecimal.ZERO) != 0) {
				logger.debug("Correcting sales price incl. vat according to db by subtracting "
						+ verif.getTotalSalesPriceInclVatCorrection().toPlainString());
			}
			BigDecimal totalSalesPriceInclVatDB = orderDAO.getTotalSalesInclVat(currencyObj,customerVat).
				add(orderBundleDAO.getTotalSalesInclVat(currencyObj,customerVat)).subtract(verif.getTotalSalesPriceInclVatCorrection());
			logger.debug("totalSalesPriceInclVatDB = " + totalSalesPriceInclVatDB);
			BigDecimal salesPriceDuringPeriodInclVat = totalSalesPriceInclVatDB.subtract(verif.getTotalSalesPriceInclVat());
			VirtualCoin salesPriceDuringPeriodInclVatVC = new VirtualCoin(currency,salesPriceDuringPeriodInclVat);
			String salesPriceDuringPeriodInclVatVCStr = CurrencyFormatter.print(salesPriceDuringPeriodInclVatVC, Statics.FINNISH);
			tempBuf.append("Alv " + alvPercentageString + "% myynti jakson aikana (sis. alv):    "
					+ salesPriceDuringPeriodInclVatVCStr + "\n");
			BigDecimal newTotalSalesPriceInclVat = verif.getTotalSalesPriceInclVat().add(salesPriceDuringPeriodInclVat);
			verif.setTotalSalesPriceInclVat(newTotalSalesPriceInclVat);
			VirtualCoin newTotalSalesPriceInclVatVC = new VirtualCoin(verif.getCurrency(),newTotalSalesPriceInclVat);
			String newTotalSalesPriceInclVatVCStr = CurrencyFormatter.print(newTotalSalesPriceInclVatVC, Statics.FINNISH);
			header.append("Alv " + alvPercentageString
					+ "% myynti yhteensä jakson lopussa (sis. alv):          " + newTotalSalesPriceInclVatVCStr + "\n");

			/* Handle sales excluding vat. */
			logger.debug("orderDAO.getTotalSalesExclVat = " + orderDAO.getTotalSalesExclVat(currencyObj,customerVat)
					+ ", orderBundleDAO.getTotalSalesExclVat = " + orderBundleDAO.getTotalSalesExclVat(currencyObj,customerVat));
			if (verif.getTotalSalesPriceExclVatCorrection().compareTo(BigDecimal.ZERO) != 0) {
				logger.debug("Correcting sales price excl. vat according to db by subtracting "
						+ verif.getTotalSalesPriceExclVatCorrection().toPlainString());
			}
			BigDecimal totalSalesPriceExclVatDB = orderDAO.getTotalSalesExclVat(currencyObj,customerVat).
				add(orderBundleDAO.getTotalSalesExclVat(currencyObj,customerVat)).subtract(verif.getTotalSalesPriceExclVatCorrection());
			logger.debug("totalSalesPriceExclVatDB = " + totalSalesPriceExclVatDB);
			BigDecimal salesPriceDuringPeriodExclVat = totalSalesPriceExclVatDB.subtract(verif.getTotalSalesPriceExclVat());
			VirtualCoin salesPriceDuringPeriodExclVatVC = new VirtualCoin(currency,salesPriceDuringPeriodExclVat);
			String salesPriceDuringPeriodExclVatVCStr = CurrencyFormatter.print(salesPriceDuringPeriodExclVatVC, Statics.FINNISH);
			tempBuf.append("Alv " + alvPercentageString + "% myynti jakson aikana (ilman alvia): "
					+ salesPriceDuringPeriodExclVatVCStr + "\n");
			BigDecimal newTotalSalesPriceExclVat = verif.getTotalSalesPriceExclVat().add(salesPriceDuringPeriodExclVat);
			verif.setTotalSalesPriceExclVat(newTotalSalesPriceExclVat);
			VirtualCoin newTotalSalesPriceExclVatVC = new VirtualCoin(verif.getCurrency(),newTotalSalesPriceExclVat);
			String newTotalSalesPriceExclVatVCStr = CurrencyFormatter.print(newTotalSalesPriceExclVatVC, Statics.FINNISH);
			header.append("Alv " + alvPercentageString
					+ "% myynti yhteensä jakson lopussa (ilman alvia):       " + newTotalSalesPriceExclVatVCStr + "\n");
			
			/* Handle vat changes. */
			VirtualCoin addedVatVC = new VirtualCoin(currency,salesPriceDuringPeriodInclVat.subtract(salesPriceDuringPeriodExclVat));
			String addedVatVCStr = CurrencyFormatter.print(addedVatVC, Statics.FINNISH);
			tempBuf.append("Alv " + alvPercentageString + "% kertymä jakson aikana:              " + addedVatVCStr + "\n");
			verificationDAO.save(verif);
		}
		header.append("\n");
		
		/* Check if there are cases of excessive money transfers for which the customer has lost his right to
		 * get a refund. This code has some problems and is therefore commented out.See "fixa automaattinen
		 * täsmäytys för liian isot maksut" in Test.java and FaultyPaymentFixer.java for more information.*/
		/*Settings lostRefundRightAmountInclVatSetting = settingsDAO.get("lostRefundRightAmountInclVat");
		Settings lostRefundRightForBundleIdsSetting = settingsDAO.get("lostRefundRightForBundleIds");
		if (lostRefundRightAmountInclVatSetting != null) {
			BigDecimal lostRefundRightAmountInclVat = new BigDecimal(lostRefundRightAmountInclVatSetting.getValue());
			VirtualCoin lostRefundRightAmountInclVatVC = new VirtualCoin(currency,lostRefundRightAmountInclVat);
			String lostRefundRightAmountInclVatStr = CurrencyFormatter.print(lostRefundRightAmountInclVatVC, Statics.FINNISH);
			BigDecimal lostRefundRightAmountExclVat = MathUtilities.getPriceExclVat(lostRefundRightAmountInclVat, new BigDecimal("0.23"));
			VirtualCoin lostRefundRightAmountExclVatVC = new VirtualCoin(currency,lostRefundRightAmountExclVat);
			String lostRefundRightAmountExclVatStr = CurrencyFormatter.print(lostRefundRightAmountExclVatVC, Statics.FINNISH);
			tempBuf.append("KORJAUS LIITTYEN YRITYSTILILLE SIIRRETTYYN RAHAMÄÄRÄÄN:\n");
			tempBuf.append("Tulossa olevat asiakashyvitykset joihin asiakas on menettänyt\n");
			tempBuf.append("oikeutensa saada hyvityksen: " + lostRefundRightAmountInclVatStr + "\n");
			tempBuf.append("Tästä summasta pitää maksaa alvia (23%), eli:\n");
			tempBuf.append("Alvillinen summa: " + lostRefundRightAmountInclVatStr + "\n");
			tempBuf.append("Alviton summa   : " + lostRefundRightAmountExclVatStr + "\n");
			tempBuf.append("(tämä ylimääräinen raha oli kohdistettu seuraaviin nippuihin:\n");
			tempBuf.append(lostRefundRightForBundleIdsSetting.getValue() + ")\n\n");
			settingsDAO.delete(lostRefundRightAmountInclVatSetting);
			settingsDAO.delete(lostRefundRightForBundleIdsSetting);
		}*/
		
		/* Then print out the amount that was transfered to QD. */
		tempBuf.append("Siirretty yritystilille:                    " + transactionTotalSumVCStr + "\n");
		Settings totalAmountTransferedToQDSetting = settingsDAO.get("totalAmountTransferedToQD");
		logger.debug("Total amount transfered to QD: " + totalAmountTransferedToQDSetting.getValue());
		BigDecimal totalTransferedAmount = new BigDecimal(totalAmountTransferedToQDSetting.getValue());
		totalTransferedAmount = totalTransferedAmount.add(mappedTransactionsTotalSum);
		VirtualCoin totalTransferedAmountVC = new VirtualCoin(currency,totalTransferedAmount);
		String totalTransferedAmountVCStr = CurrencyFormatter.print(totalTransferedAmountVC, Statics.FINNISH);
		header.append("Yhteensä siirretty rahamäärä yritystilille jakson lopussa: " + totalTransferedAmountVCStr + "\n\n");
		totalAmountTransferedToQDSetting.setValue(totalTransferedAmount.toPlainString());
		
		verification.append(header);
		verification.append(tempBuf);
		logger.debug("verification = " + verification.toString());
		return transactionTotalSumVC;
	}
}
