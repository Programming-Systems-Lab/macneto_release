package edu.columbia.cs.psl.macneto.db;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AppInserter {
	
	public static void collectNames(File f, List<String> recorder) {
		if (f.isFile() && f.getName().endsWith(".json")) {
			recorder.add(f.getName().substring(0, f.getName().length() - 5));
		} else if (f.isDirectory()) {
			for (File c: f.listFiles()) {
				collectNames(c, recorder);
			}
		}
	}
	
	public static void main(String[] args) {
		String callgraphs = args[0];
		File callgraphsFile = new File(callgraphs);
		if (!callgraphsFile.exists()) {
			System.err.println("Invalid graph repo: " + callgraphsFile.getAbsolutePath());
			System.exit(-1);
		}
		
		List<String> recorder = new ArrayList<String>();
		collectNames(callgraphsFile, recorder);
		System.out.println("Total files: " + recorder.size());
		for (int i = 0; i < recorder.size(); i++) {
			DocManager.insertApp(recorder.get(i));
		}
		System.out.println("Insertion done");
	}

}
