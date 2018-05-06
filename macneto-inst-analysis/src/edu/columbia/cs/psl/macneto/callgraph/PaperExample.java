package edu.columbia.cs.psl.macneto.callgraph;

import java.io.BufferedReader;
import java.io.FileReader;

public class PaperExample {
	
	public String readAndSort(String fileName) {
		char[] data = readFile(fileName);
		return sort(data);
	}
	
	public static char[] readFile(String fileName) {
		try {
			BufferedReader br = 
					new BufferedReader(new FileReader(fileName));
			String first = br.readLine();
			return first.toCharArray();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	public static String sort(char[] data) {
		for (int i = 1; i < data.length; i++) {
			int j = i;
			while (j > 0 && data[j - 1] > data[j]) {
				char tmp = data[j];
				data[j] = data[j - 1];
				data[j - 1] = tmp;
				j = j - 1;
			}
		}
		return new String(data);
	}
	
	public static void main(String[] args) {
		System.out.println(sort("cnhgf".toCharArray()));
	}

}
