package fi.jonix.huutonet.domain.model.dao;

import java.security.MessageDigest;
import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.User;

public class UserDAOImpl extends GenericDAOImpl <User, Long> implements UserDAO {

	@Override
	public Class<User> getEntityClass() {
		return User.class;
	}

	public User getByLoginName(String loginName) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("loginName", loginName));
		List<User> users = this.getHibernateTemplate().findByCriteria(criteria);
		if (users == null || users.isEmpty()) {
			return null;
		}
		/* There will never be more than one user here, because loginName
		 * is unique in the database. */
		return users.get(0);
	}
	
	private String getMD5(String plainString) {
		/* Calculate a message digest. */
		byte[] digest;
		try {
			byte[] bytesOfMessage = plainString.getBytes("UTF-8");
			MessageDigest md = MessageDigest.getInstance("MD5");
			digest = md.digest(bytesOfMessage);
		} catch (Exception e) {
			logger.error("Couldn't get md5 hash: ", e);
			return "blablahaj";
		}
		
		/* Then convert to hex format. */
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < digest.length; i++) { 
            int halfbyte = (digest[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do { 
                if ((0 <= halfbyte) && (halfbyte <= 9)) 
                    buf.append((char) ('0' + halfbyte));
                else 
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = digest[i] & 0x0F;
            } while(two_halfs++ < 1);
        } 
        return buf.toString();
	}
	
	public User getByLoginName(String loginName, String plainTextPassword) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("loginName", loginName));
		criteria.add(Restrictions.eq("passwordMd5", getMD5(plainTextPassword)));
		List<User> users = this.getHibernateTemplate().findByCriteria(criteria);
		if (users == null || users.isEmpty()) {
			return null;
		}
		/* There will never be more than one user here, because loginName
		 * is unique in the database. */
		return users.get(0);
	}
	
	public List<User> getOperators() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("operator", new Boolean(true)));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
}
