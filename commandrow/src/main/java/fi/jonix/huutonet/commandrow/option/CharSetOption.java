package fi.jonix.huutonet.commandrow.option;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import fi.jonix.huutonet.tools.UserInputHelper;

public class CharSetOption {

	static String[] charsets = { "ISO-8859-1", "Cp437", "Cp850", "Cp1252", "UTF-8" };

	static public String getEncoding() {
		Console console = System.console();
		for(int i=0; i<charsets.length; i++){
			try {
				CharSetOption.print("Ääkköset pitäisi näkyä - åäöÅÄÖ", charsets[i], i+1);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		Integer index = UserInputHelper.getOneNumber("Enter encoding: ",1,charsets.length,false,false,
				new BufferedReader(new InputStreamReader(System.in)));
		System.out.println("Using charset " + charsets[index-1]);
		return charsets[index-1];
	}
	
	static private void print(String text, String charset, int index) throws Exception {
		PrintWriter out = null;
		try {
			out = new PrintWriter(new OutputStreamWriter(System.out, charset));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		out.print(index + ".) ");
		out.print(charset);
		for(int i=0; i<10-charset.length(); i++){
			out.print(" ");
		}
		out.println(" -> " + text);
		out.flush();
	}
	
}
