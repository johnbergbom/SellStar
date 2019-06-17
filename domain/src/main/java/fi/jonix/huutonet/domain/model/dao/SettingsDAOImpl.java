package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Settings;

public class SettingsDAOImpl extends GenericDAOImpl<Settings, String> implements
		SettingsDAO {

	public Class<Settings> getEntityClass() {
		return Settings.class;
	}

	/**
	 * @return Returns the value if any exists, or else null. Note that null
	 * will be returned both if the key exists and its value is null, and if
	 * the key doesn't exist in the database.
	 */
	public String getValue(String key) {
		Settings settings = get(key);
		if (settings != null) {
			return settings.getValue();
		}
		return null;
	}

}
