package fi.jonix.huutonet.robot.bank;

import org.springframework.test.AbstractDependencyInjectionSpringContextTests;

import fi.jonix.huutonet.tools.Encryption;

public class EncryptionTest extends AbstractDependencyInjectionSpringContextTests {

	public void testEncryption() {
		String plainText = "abcdefghijklmnopqrstuvwxyzåäö ABCDEFGHIJKLMNOPQRSTUVWXYZÅÄÖ 0123456789";
		String encrypted = Encryption.encrypt(plainText);
		String decrypted = Encryption.decrypt(encrypted);
		assertEquals(plainText,decrypted);
	}

}
