package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;

import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Verification;

public interface VerificationDAO extends GenericDAO<Verification, Long> {

	public Verification getVerification(BigDecimal vatPercentage, Currency currency);
	
}
