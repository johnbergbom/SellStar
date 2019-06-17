package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Settings;

public interface SettingsDAO extends GenericDAO<Settings, String> {
	
	public String getValue(String key);
	
}
