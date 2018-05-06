package edu.columbia.cs.psl.macneto.utils;

import java.io.File;
import java.util.ArrayList;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;

public class SanityChecker {
	
	public static ArrayList<File> collectGraphs(File dir) {
		ArrayList<File> ret = new ArrayList<File>();
		for (File f: dir.listFiles()) {
			if (f.getName().endsWith(".json")) {
				ret.add(f);
			}
		}
		return ret;
	}
	
	public static void main(String[] args) {
		File repo = new File("/Users/mikefhsu/Desktop/models/callgraphs");
		ArrayList<File> graphs = collectGraphs(repo);
		System.out.println("Total graphs: " + graphs.size());
		TypeToken<CallLookup> tok = new TypeToken<CallLookup>(){};
		int counter = 0;
		for (File g: graphs) {
			CallLookup lookup = LightweightUtils.readGsonGeneric(g, tok);
			
			for (CallNode c: lookup.getCalls().values()) {
				if (c.callees.size() == 0 && c.sysCalls.size() == 0) {
					System.out.println("Current apk: " + lookup.getAppName());
					System.out.println("Raw node: " + c.getMethodKey());
				}
			}
			counter++;
			if (counter % 100 == 0) {
				System.out.println("Process #" + counter + " graphs");
			}
		}
	}

}
