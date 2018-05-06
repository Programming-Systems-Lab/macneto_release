package edu.columbia.cs.psl.macneto.utils;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public abstract class AbstractMapAnalyzer {
	
	public static StringBuilder sb = new StringBuilder();
	
	public static String OBDB = null;
	
	public static void collectMap(File mapFile, List<File> recorder) {
		if (mapFile.isFile() && mapFile.getName().endsWith(".map")) {
			recorder.add(mapFile);
		} else if (mapFile.isDirectory()) {
			for (File f: mapFile.listFiles()) {
				collectMap(f, recorder);
			}
		}
	}
	
	public static String isPrim(String argName) {
		if (argName.equals("byte")) {
			return "B";
		} else if (argName.equals("char")) {
			return "C";
		} else if (argName.equals("double")) {
			return "D";
		} else if (argName.equals("float")) {
			return "F";
		} else if (argName.equals("int")) {
			return "I";
		} else if (argName.equals("long")) {
			return "J";
		} else if (argName.equals("short")) {
			return "S";
		} else if (argName.equals("boolean")) {
			return "Z";
		} else {
			return null;
		}
	}
	
	public static String isArray(String argName, HashMap<String, String> mapping) {
		if (!argName.contains("[")) {
			return null;
		}
		
		int first = argName.indexOf("[");
		String className = argName.substring(0, first);
		StringBuilder sb = new StringBuilder();
		for (int i = first; i < argName.length(); i++) {
			if (argName.charAt(i) == '[') {
				sb.append('[');
			}
		}
		
		String primName = isPrim(className);
		if (primName != null) {
			sb.append(primName);
		} else {
			if (mapping == null) {
				sb.append(className);
			} else {
				if (mapping.containsKey(className)) {
					String newName = mapping.get(className);
					sb.append(newName);
				} else {
					sb.append(className);
				}
			}
		}
		
		return sb.toString();
	}
	
	public static boolean shouldPass(String clazzName) {
		if (clazzName.startsWith("android.support")) {
    		return true;
    	}
    	
    	if (clazzName.startsWith("android.util.Log")) {
    		return true;
    	}
    	
    	if (clazzName.startsWith("org.apache.commons.logging")) {
    		return true;
    	}
    	
    	if (clazzName.startsWith("org.slf4j")) {
    		return true;
    	}
    	
    	if (clazzName.startsWith("junit") || clazzName.startsWith("org.junit")) {
    		return true;
    	}
    	
    	return false;
	}
	
	public static String[] parseMethod(String methodSig) {
		int idx = methodSig.indexOf("(");
		String methodName = methodSig.substring(0, idx);
		String methodDesc = methodSig.substring(idx, methodSig.length());
		
		return new String[]{methodName, methodDesc};
	}

}
