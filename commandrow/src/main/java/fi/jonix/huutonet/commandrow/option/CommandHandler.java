package fi.jonix.huutonet.commandrow.option;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import fi.jonix.huutonet.tools.UserInputHelper;

public abstract class CommandHandler {

	List<Option> options = new ArrayList<Option>();
	List<String> arguments = new ArrayList<String>();

	protected abstract void handleCommand();

	protected abstract void initializeOptionList();

	public void work(String[] args) {
		this.initializeArguments(args);
		this.initializeOptionList();
		this.makeQuestions();
		this.handleCommand();
	}

	protected void initializeArguments(String[] args) {
		if (args.length > 1) {
			for (int i = 1; i < args.length; i++) {
				arguments.add(args[i]);
			}
		}
	}
	
	protected void makeQuestions() {
		Console console = System.console();
		if (console == null) {
			System.err.println("sales: unable to obtain console");
			return;
		}
		for (Option option : options) {
			if (option.getAlternatives() != null && option.getAlternatives().size() > 1) {
				System.out.println(option.getDescription() + ":");
			}
			while (option.getValue() == null || (option.isRequired() && option.getValue().trim().length() == 0)) {
				if (option.getAlternatives() == null) {
					//if there are no alternatives, then read a string from the user
					String value = null;
					while (value == null) {
						value = console.readLine(option.getQuestion() + ": ");
						if (!option.isRequired() || (value != null && !value.trim().equals(""))) {
							break;
						}
					}
					option.setValue(value);
				} else if (option.getAlternatives().size() == 1) {
					//choose the only alternative automatically if there is just one alternative
					System.out.println(option.getDescription() + ": option " + option.getAlternatives().get(0) + " chosen automatically.");
					option.setValue(option.getAlternatives().get(0));
				} else {
					for (int i = 0; i < option.getAlternatives().size(); i++) {
						System.out.println((i+1) + ".) " + option.getAlternatives().get(i));
					}
					Integer index = UserInputHelper.getOneNumber(option.getQuestion(),1,option.getAlternatives().size(),
							!option.isRequired(),false,new BufferedReader(new InputStreamReader(System.in)));
					if (index == null) {
						break;
					}
					System.out.println("Option " + option.getAlternatives().get(index-1) + " chosen.");
					option.setValue(option.getAlternatives().get(index-1));
				}
			}
		}
	}

	protected boolean hasValue(String optionName) {
		for (Option option : options) {
			if (option.getName().equals(optionName)) {
				if (option.getValue() == null
						|| option.getValue().length() == 0)
					return false;
				else
					return true;
			}
		}
		return false;
	}

	protected String getValue(String optionName) {
		for (Option option : options) {
			if (option.getName().equals(optionName)) {
				return option.getValue();
			}
		}
		return null;
	}

}
