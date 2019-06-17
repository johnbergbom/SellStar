package fi.jonix.huutonet.robot;

/**
 * Base class for all robots.
 * @author john
 *
 */
public interface BaseRobot {

	public void init(String seleniumProfile) throws Exception;
	public void tearDown() throws Exception;
	
}
