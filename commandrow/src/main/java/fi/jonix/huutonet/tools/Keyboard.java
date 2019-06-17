package fi.jonix.huutonet.tools;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;

import org.apache.log4j.Logger;

public class Keyboard {
	
	public static int PLATFORM_LINUX = 1;
	public static int PLATFORM_WINDOWS = 2;
	public static final Logger logger = Logger.getLogger(Keyboard.class);
	
    private Robot robot;
    private int platform;

    public Keyboard() throws AWTException {
        this.robot = new Robot();
        platform = PLATFORM_WINDOWS;
    }

    public Keyboard(int platform) throws AWTException {
        this.robot = new Robot();
        this.platform = platform;
    }

    public Keyboard(Robot robot) {
        this.robot = robot;
        platform = PLATFORM_WINDOWS;
    }

    public void type(CharSequence characters) {
        int length = characters.length();
        for (int i = 0; i < length; i++) {
        	char character = characters.charAt(i);
        	type(character);
        }
    }

    public boolean isPrintable(char character) {
    	try {
    		typeWithException(character,false);
    		return true;
    	} catch (IllegalArgumentException e) {
    		return false;
    	}
    }
    
    public void type(char character) {
    	try {
    		typeWithException(character,true);
    	} catch (IllegalArgumentException e) {
    		logger.debug("Cannot type character " + character);
    		doType(true,KeyEvent.VK_X);
    	}
    }
    
    private void typeWithException(char character, boolean doPrint) {
    	switch (character) {
	        case 'a': doType(doPrint,KeyEvent.VK_A); break;
	        case 'b': doType(doPrint,KeyEvent.VK_B); break;
	        case 'c': doType(doPrint,KeyEvent.VK_C); break;
	        case 'd': doType(doPrint,KeyEvent.VK_D); break;
	        case 'e': doType(doPrint,KeyEvent.VK_E); break;
	        case 'f': doType(doPrint,KeyEvent.VK_F); break;
	        case 'g': doType(doPrint,KeyEvent.VK_G); break;
	        case 'h': doType(doPrint,KeyEvent.VK_H); break;
	        case 'i': doType(doPrint,KeyEvent.VK_I); break;
	        case 'j': doType(doPrint,KeyEvent.VK_J); break;
	        case 'k': doType(doPrint,KeyEvent.VK_K); break;
	        case 'l': doType(doPrint,KeyEvent.VK_L); break;
	        case 'm': doType(doPrint,KeyEvent.VK_M); break;
	        case 'n': doType(doPrint,KeyEvent.VK_N); break;
	        case 'o': doType(doPrint,KeyEvent.VK_O); break;
	        case 'p': doType(doPrint,KeyEvent.VK_P); break;
	        case 'q': doType(doPrint,KeyEvent.VK_Q); break;
	        case 'r': doType(doPrint,KeyEvent.VK_R); break;
	        case 's': doType(doPrint,KeyEvent.VK_S); break;
	        case 't': doType(doPrint,KeyEvent.VK_T); break;
	        case 'u': doType(doPrint,KeyEvent.VK_U); break;
	        case 'v': doType(doPrint,KeyEvent.VK_V); break;
	        case 'w': doType(doPrint,KeyEvent.VK_W); break;
	        case 'x': doType(doPrint,KeyEvent.VK_X); break;
	        case 'y': doType(doPrint,KeyEvent.VK_Y); break;
	        case 'z': doType(doPrint,KeyEvent.VK_Z); break;
	        case 'A': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_A); break;
	        case 'B': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_B); break;
	        case 'C': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_C); break;
	        case 'D': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_D); break;
	        case 'E': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_E); break;
	        case 'F': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_F); break;
	        case 'G': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_G); break;
	        case 'H': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_H); break;
	        case 'I': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_I); break;
	        case 'J': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_J); break;
	        case 'K': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_K); break;
	        case 'L': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_L); break;
	        case 'M': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_M); break;
	        case 'N': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_N); break;
	        case 'O': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_O); break;
	        case 'P': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_P); break;
	        case 'Q': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_Q); break;
	        case 'R': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_R); break;
	        case 'S': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_S); break;
	        case 'T': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_T); break;
	        case 'U': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_U); break;
	        case 'V': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_V); break;
	        case 'W': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_W); break;
	        case 'X': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_X); break;
	        case 'Y': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_Y); break;
	        case 'Z': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_Z); break;
	        case '0': doType(doPrint,KeyEvent.VK_0); break;
	        case '1': doType(doPrint,KeyEvent.VK_1); break;
	        case '2': doType(doPrint,KeyEvent.VK_2); break;
	        case '3': doType(doPrint,KeyEvent.VK_3); break;
	        case '4': doType(doPrint,KeyEvent.VK_4); break;
	        case '5': doType(doPrint,KeyEvent.VK_5); break;
	        case '6': doType(doPrint,KeyEvent.VK_6); break;
	        case '7': doType(doPrint,KeyEvent.VK_7); break;
	        case '8': doType(doPrint,KeyEvent.VK_8); break;
	        case '9': doType(doPrint,KeyEvent.VK_9); break;
	        case '\t': doType(doPrint,KeyEvent.VK_TAB); break;
	        case '\n': doType(doPrint,KeyEvent.VK_ENTER); break;
	        case ' ': doType(doPrint,KeyEvent.VK_SPACE); break;
	        default: {
	        	/* For the rest of the characters the printing is platform specific. */
	        	if (platform == PLATFORM_LINUX) {
	    	        switch (character) {
	    	        case 'å': doType(doPrint,KeyEvent.VK_A); break;
	    	        case 'ä': doType(doPrint,KeyEvent.VK_A); break;
	    	        case 'ö': doType(doPrint,KeyEvent.VK_O); break;
	    	        case 'Å': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_A); break;
	    	        case 'Ä': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_A); break;
	    	        case 'Ö': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_O); break;
	    	        case '`': doType(doPrint,KeyEvent.VK_BACK_QUOTE); break;
	    	        case '-': doType(doPrint,KeyEvent.VK_MINUS); break;
	    	        case '=': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_EQUALS); break;
	    	        case '~': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '!': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_EXCLAMATION_MARK); break;
	    	        case '@': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '#': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_NUMBER_SIGN); break;
	    	        case '$': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '%': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_5); break;
	    	        case '^': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '&': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_AMPERSAND); break;
	    	        case '*': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_ASTERISK); break;
	    	        case '(': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_LEFT_PARENTHESIS); break;
	    	        case ')': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_RIGHT_PARENTHESIS); break;
	    	        case '_': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_UNDERSCORE); break;
	    	        case '+': doType(doPrint,KeyEvent.VK_PLUS); break;
	    	        case '[': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case ']': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '\\': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '{': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '}': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '|': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case ';': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_SEMICOLON); break;
	    	        case ':': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_COLON); break;
	    	        case '\'': doType(doPrint,KeyEvent.VK_QUOTE); break;
	    	        case '"': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_QUOTEDBL); break;
	    	        case ',': doType(doPrint,KeyEvent.VK_COMMA); break;
	    	        case '<': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '.': doType(doPrint,KeyEvent.VK_PERIOD); break;
	    	        case '>': throw new IllegalArgumentException("Cannot type character " + character);
	    	        case '/': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_SLASH); break;
	    	        case '?': doType(doPrint,KeyEvent.VK_SHIFT, KeyEvent.VK_PLUS); break;
	    	        default: throw new IllegalArgumentException("Cannot type character " + character);
	    	        }
	        	} else { //windows
	    	        switch (character) {
		    	        case 'å': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD4); break;
		    	        case 'ä': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD2); break;
		    	        case 'ö': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD8); break;
		    	        case 'Å': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD3); break;
		    	        case 'Ä': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD2); break;
		    	        case 'Ö': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD3); break;
		    	        case '`': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD6); break;
		    	        case '-': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5); break;
		    	        case '=': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD1); break;
		    	        case '~': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD6); break;
		    	        case '!': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD3); break;
		    	        case '@': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD4); break;
		    	        case '#': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD5); break;
		    	        case '$': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD6); break;
		    	        case '%': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD7); break;
		    	        case '^': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD4); break;
		    	        case '&': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD8); break;
		    	        case '*': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD2); break;
		    	        case '(': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD0); break;
		    	        case ')': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD1); break;
		    	        case '_': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD5); break;
		    	        case '+': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD3); break;
		    	        case '[': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD1); break;
		    	        case ']': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD3); break;
		    	        case '\\': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD2); break;
		    	        case '{': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD3); break;
		    	        case '}': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD5); break;
		    	        case '|': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD4); break;
		    	        case ';': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD9); break;
		    	        case ':': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD8); break;
		    	        case '\'': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD9); break;
		    	        case '"': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD4); break;
		    	        case ',': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD4); break;
		    	        case '<': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD0); break;
		    	        case '.': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD6); break;
		    	        case '>': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD2); break;
		    	        case '/': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD7); break;
		    	        case '?': doTypeAscii(doPrint,KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD3); break;
		    	        default: throw new IllegalArgumentException("Cannot type character " + character);
	    	        }
	        	}
	        }
    	}
    }

    private void doType(boolean doPrint, int... keyCodes) {
        doType(doPrint,keyCodes, 0, keyCodes.length);
    }

    private void doType(boolean doPrint, int[] keyCodes, int offset, int length) {
        if (length == 0 || !doPrint) {
        	return;
        }
        robot.keyPress(keyCodes[offset]);
        doType(doPrint,keyCodes, offset + 1, length - 1);
        robot.keyRelease(keyCodes[offset]);
    }
    
    private void doTypeAscii(boolean doPrint, int... keyCodes) {
        if (!doPrint) {
        	return;
        }
    	robot.keyPress(KeyEvent.VK_ALT);
    	for (int keyCode : keyCodes) {
    		robot.keyPress(keyCode);
    		robot.keyRelease(keyCode);
    	}
    	robot.keyRelease(KeyEvent.VK_ALT);
    }

}

