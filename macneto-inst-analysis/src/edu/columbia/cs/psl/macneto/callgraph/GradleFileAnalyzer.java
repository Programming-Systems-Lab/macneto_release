package edu.columbia.cs.psl.macneto.callgraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class GradleFileAnalyzer {
	
	public static String MIN_SDK_KEY = "minSdkVersion";
	
	public static String TARGET_SDK_KEY = "targetSdkVersion";

	public static void main(String[] args) {
		try {
			File gradleFile = new File("/Users/mikefhsu/Desktop/pldi_apks/Conversations/build.gradle");
			BufferedReader br = new BufferedReader(new FileReader(gradleFile));
			String buf = "";
			while ((buf = br.readLine()) != null) {
				//System.out.println(buf);
				buf = buf.trim();
				if (buf.startsWith(MIN_SDK_KEY)) {
					System.out.println(buf);
				} else if(buf.startsWith(TARGET_SDK_KEY)) {
					System.out.println(buf);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
