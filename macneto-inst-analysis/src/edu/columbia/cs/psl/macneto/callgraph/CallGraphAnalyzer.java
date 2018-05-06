package edu.columbia.cs.psl.macneto.callgraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.AllatoriMapAnalyzer;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

public class CallGraphAnalyzer {
	
	public static final Options options = new Options();
	
	public static final Logger logger = LogManager.getLogger(CallGraphAnalyzer.class);
	
	public static final TypeToken<CallLookup> token = new TypeToken<CallLookup>(){};
	
	static {
		options.addOption("r1", true, "Graph repo1");
		options.getOption("r1").setRequired(true);
		
		options.addOption("r2", true, "Graph repo2");
		options.getOption("r2").setRequired(true);
		
		options.addOption("m", true, "Mappings");
		options.getOption("m").setRequired(true);
	}
	
	public static String parseInitArgs(String args, HashMap<String, String> classMappings) {
		int lIdx = args.indexOf("(");
		int rIdx = args.lastIndexOf(")");
		
		String pureArg = args.substring(lIdx + 1, rIdx);
		if (pureArg.length() == 0) {
			return "()V";
		}
		
		String retType = args.substring(rIdx + 1, args.length());
		
		String[] splits = pureArg.split("\\+");
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for (int i = 0; i < splits.length; i++) {
			String curArg = splits[i];
			String newArg = null;
			if (curArg.startsWith("[")) {
				int lastIdx = curArg.lastIndexOf("[");
				String dataType = curArg.substring(lastIdx + 1, curArg.length());
				if (classMappings.containsKey(dataType)) {
					dataType = classMappings.get(dataType);
				}
				
				//put back [
				newArg = curArg.substring(0, lastIdx + 1) + dataType;
			} else {
				if (classMappings.containsKey(curArg)) {
					newArg = classMappings.get(curArg);
				} else {
					newArg = curArg;
				}
			} 
			
			sb.append(newArg + "+");
		}
		
		//The return has to be V for constructor
		String ret = sb.substring(0, sb.length() - 1) + ")V";
		return ret;
	}
	
	public static HashMap<String, String> methodMappings(File mapDir, String apkName) {
		try {
			String mapFilePath = mapDir.getAbsolutePath() + "/" + apkName + "_m.csv";
			File mapFile = new File(mapFilePath);
			
			if (!mapFile.exists()) {
				logger.warn("Invalid method map path: " + mapDir.getAbsolutePath());
				return null;
			}
			
			HashMap<String, String> mapping = new HashMap<String, String>();
			BufferedReader br = new BufferedReader(new FileReader(mapFile));
			String buf = null;
			while ((buf = br.readLine()) != null) {
				String[] info = buf.split(",");
				mapping.put(info[1], info[0]);
			}
			
			return mapping;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		return null;
	}
	
	public static HashMap<String, String> classMappings(File mapDir, String apkName) {
		try {
			String mapFilePath = mapDir.getAbsolutePath() + "/" + apkName + "_c.csv";
			File mapFile = new File(mapFilePath);
			
			if (!mapFile.exists()) {
				logger.warn("Invalid class map path: " + mapFile.getAbsolutePath());
				return null;
			}
			
			HashMap<String, String> mapping = new HashMap<String, String>();
			BufferedReader br = new BufferedReader(new FileReader(mapFile));
			String buf = null;
			while ((buf = br.readLine()) != null) {
				String[] info = buf.split(",");
				mapping.put(info[0], info[1]);
			}
			
			return mapping;
		} catch (Exception ex) {
			logger.error("Error: " ,ex);
		}
		
		return null;
	}
	
	public static void collectGraphs(File repo, HashMap<String, CallLookup> graphs) {
		if (repo.isFile()) {
			if (repo.getName().endsWith(".json")) {
				CallLookup graph = LightweightUtils.readGsonGeneric(repo, token);
				String name = repo.getName().substring(0, repo.getName().length() - 5);
				graphs.put(name, graph);
			}
		} else {
			for (File f: repo.listFiles()) {
				collectGraphs(f, graphs);
			}
		}
	}
	
	public static String handleInit(String className, String args, HashMap<String, String> classMappings) {
		String corrClass = classMappings.get(className);
		if (corrClass == null) {
			logger.error("Cannot find mapping class: " + className);
			return null;
		}
		
		String parsedArgs = parseInitArgs(args, classMappings);
		//logger.info("Args check: " + args + "->" + parsedArgs);
		String corrKey = corrClass + LightweightUtils.DELIM + "<init>" + LightweightUtils.DELIM + parsedArgs;
		return corrKey;
	}
	
	public static void simpleCompare(CallLookup g1, 
			CallLookup g2, 
			HashMap<String, String> methodMappings, 
			HashMap<String, String> classMappings) {
		HashMap<String, CallNode> calls1 = g1.getCalls();
		HashMap<String, CallNode> calls2 = g2.getCalls();
		logger.info("Analyzing " + g1.getAppName());
		int counter = 0;
		for (String k1: calls1.keySet()) {
			CallNode c1 = calls1.get(k1);
			
			//Get corresponding node from g2
			String corrKey = methodMappings.get(k1);
			if (corrKey == null) {
				//can be constructor
				String[] parsed = k1.split("-");
				String className = parsed[0];
				String methodName = parsed[1];
				String args = parsed[2];
				
				if (methodName.equals("<init>")) {
					corrKey = handleInit(className, args, classMappings);
					if (corrKey == null) {
						continue ;
					}
					
					/*String corrClass = classMappings.get(className);
					if (corrClass == null) {
						logger.error("Cannot find mapping class: " + className);
						continue ;
					}
					
					String parsedArgs = parseInitArgs(args, classMappings);
					//logger.info("Args check: " + args + "->" + parsedArgs);
					corrKey = corrClass + LightweightUtils.DELIM + "<init>" + LightweightUtils.DELIM + parsedArgs;*/
				} else {
					logger.error("Cannot find mapping key: " + k1);
					continue ;
				}
			}
			
			CallNode c2 = calls2.get(corrKey);
			if (c2 == null) {
				logger.error("Cannot find corredponding node: " + k1 + "->" + corrKey);
				continue ;
				//System.exit(-1);
			}
			
			if (c1.getCallees().size() != c2.getCallees().size()) {
				logger.error("Un-eqivalent callee nums: " + c1.key + "->" + c2.key);
			}
			
			if (c1.getSysCalls().size() != c2.getSysCalls().size()) {
				logger.error("Un-equivalent sys-call nums: " + c1.key + "->" + c2.key);
			}
			
			if (c1.getCallees().size() == c2.getCallees().size() && c1.getSysCalls().size() == c2.getSysCalls().size()) {
				counter++;
			}
		}
		logger.info("Total nodes: " + calls1.size());
		logger.info("Total equivalent nodes: " + counter);
	}

	public static void main(String[] args) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine commands = parser.parse(options, args);
		
		String repo1Path = commands.getOptionValue("r1");
		File repo1 = new File(repo1Path);
		if (!repo1.exists()) {
			logger.error("Invalid repo1: " + repo1.getAbsolutePath());
			System.exit(-1);
		}
		
		String repo2Path = commands.getOptionValue("r2");
		File repo2 = new File(repo2Path);
		if (!repo2.exists()) {
			logger.error("Invalid repo2: " + repo2.getAbsolutePath());
			System.exit(-1);
		}
		
		String mapPath = commands.getOptionValue("m");
		File mapFile = new File(mapPath);
		if (!mapFile.exists()) {
			logger.error("Invalid map file: " + mapFile.getAbsolutePath());
			System.exit(-1);
		}
		
		HashMap<String, CallLookup> graphs1 = new HashMap<String, CallLookup>();
		collectGraphs(repo1, graphs1);
		logger.info("# graphs1: " + graphs1.size());
		
		HashMap<String, CallLookup> graphs2 = new HashMap<String, CallLookup>();
		collectGraphs(repo2, graphs2);
		logger.info("# graphs2: " + graphs2.size());
		
		for (String apkKey: graphs1.keySet()) {
			CallLookup lookup1 = graphs1.get(apkKey);
			
			String apkKey2 = apkKey + "_obfus";
			CallLookup lookup2 = graphs2.get(apkKey2);
			
			HashMap<String, String> methodMappings = methodMappings(mapFile, apkKey);
			HashMap<String, String> classMappings = classMappings(mapFile, apkKey);
			simpleCompare(lookup1, lookup2, methodMappings, classMappings);
		}
	}
	
}
