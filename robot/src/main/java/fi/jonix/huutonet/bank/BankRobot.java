package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Transaction;

public abstract class BankRobot {

	public abstract List<Transaction> getTransactions(Calendar beginCalendar) throws Exception;
	public abstract void makeTransaction(String message, VirtualCoin sum, String receiverName, String accountNumber) throws Exception;
	public abstract void confirmTransactions(String user, String password, Map<String,String> bankCodes) throws Exception;
	public abstract boolean isLoggedIn();
	public abstract void login(String user, String password, Map<String,String> bankCodes) throws Exception;
	public abstract void logout();
	public abstract VirtualCoin getAccountBalance();

}
