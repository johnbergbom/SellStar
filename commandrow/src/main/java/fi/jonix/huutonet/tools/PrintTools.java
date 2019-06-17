package fi.jonix.huutonet.tools;

import java.util.Calendar;

public class PrintTools {
	
	public static String getFormattedTimeDifference(Calendar start, Calendar end) {
		long miliseconds = end.getTimeInMillis() - start.getTimeInMillis();
		int hours = (int) miliseconds / (1000 * 60 * 60);
		int minutes = (int) miliseconds / (1000 * 60) - hours * 60;
		int seconds = (int)miliseconds / 1000 - minutes * 60 - hours * 60 * 60;
		String time = "";
		if(hours > 0){
			return time += hours + "h " + minutes + "min " + seconds + "s";
		} else if (minutes > 0)
			return time += minutes + "min " + seconds + "s";
		else {
			return seconds + "s";
		}
	}
	
	public static String getFormattedAverageTime(Calendar start, Calendar end, int count) {
		long miliseconds;
		if(count == 0)
			miliseconds = 0;
		else
			miliseconds = (end.getTimeInMillis() - start.getTimeInMillis()) / count;
		int hours = (int) miliseconds / (1000 * 60 * 60);
		int minutes = (int) miliseconds / (1000 * 60) - hours * 60;
		int seconds = (int)miliseconds / 1000 - minutes * 60 - hours * 60 * 60;
		String time = "";
		if(hours > 0){
			return time += hours + "h " + minutes + "min " + seconds + "s";
		} else if (minutes > 0)
			return time += minutes + "min " + seconds + "s";
		else {
			return seconds + "s";
		}
	}
	
}
