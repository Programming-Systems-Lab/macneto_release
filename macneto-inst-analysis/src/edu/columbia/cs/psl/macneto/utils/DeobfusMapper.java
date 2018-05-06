package edu.columbia.cs.psl.macneto.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import proguard.obfuscate.MappingProcessor;
import proguard.obfuscate.MappingReader;

public class DeobfusMapper {
	
	private static final Logger logger = LogManager.getLogger(DeobfusMapper.class);
	
	public static final Options options = new Options();
	
	static {
		options.addOption("c", true, "Call graph location");
		options.getOption("c").setRequired(true);
		
		options.addOption("g", true, "Ground truth location");
		options.getOption("g").setRequired(true);
	}
		
	public static void collectCallGraphs(File callRepo, ArrayList<CallLookup> calls) {
		TypeToken<CallLookup> callToken = new TypeToken<CallLookup>(){};
		for (File f: callRepo.listFiles()) {
			if (f.isFile()) {
				String name = f.getName();
				if (name.endsWith("_obfus.json")) {
					continue ;
				} else if (name.endsWith(".json")) {
					CallLookup call = LightweightUtils.readGsonGeneric(f, callToken);
					calls.add(call);
				}
			}
		}
	}
	
	public static HashSet<String> collectMethodAsQueries(ArrayList<CallLookup> calls) {
		HashSet<String> allMethods = new HashSet<String>();
		for (CallLookup call: calls) {
			for (String c: call.getCalls().keySet()) {
				CallNode node = call.getCalls().get(c);
				String queryKey = node.queryKey;
				if (allMethods.contains(queryKey)) {
					if (!c.startsWith("android.support")) {
						System.out.println("Dup method: " + c);
					}
				} else {
					allMethods.add(queryKey);
				}
			}
			//allMethods.addAll(call.getCalls().keySet());
		}
		
		return allMethods;
	}
	
	public static HashMap<String, String> parseTruth(File truthFile, HashSet<String> oriMethods) {
		try {
			HashMap<String, String> mappings = new HashMap<String, String>();
			
			MappingReader reader = new MappingReader(truthFile);
			MappingProcessor processor = new MappingProcessor() {
				
				public String curClassName = null;
				
				public String obfusClassName = null;

				@Override
				public boolean processClassMapping(String className, String newClassName) {
					// TODO Auto-generated method stub
					//System.out.println("Class mapiing: " + className + " " + newClassName);
					if (className.startsWith("android.support")) {
						return false;
					} else {
						this.curClassName = className;
						this.obfusClassName = newClassName;
						return true;
					}
				}

				@Override
				public void processFieldMapping(String arg0, String arg1, String arg2, String arg3, String arg4) {
					// TODO Auto-generated method stub
					//System.out.println("Field mapping: " + arg0 + " " + arg1 + " " + arg2 + " " + arg3 + " " + arg4);
				}

				@Override
				public void processMethodMapping(String className, 
						int start, 
						int last, 
						String methodReturnType, 
						String methodName, 
						String methodArguments,
						String newClassName, 
						int newStart, 
						int newEnd, 
						String newMethodName) {
					// TODO Auto-generated method stub
					//System.out.println("Ori: " + className + "-" + methodName + "-" + methodArguments + "-" + methodReturnType);
					//System.out.println("New: " + newClassName + "-" + newMethodName);
					System.out.println("Original: " + className + " " + methodName);
					System.out.println("Obfus: " + newClassName + " " + newMethodName);
					System.out.println("Current class: " + this.curClassName);
					System.out.println("Obfus class: " + this.obfusClassName);
					int argSize = 0;
					if (methodArguments.length() != 0) {
						argSize = methodArguments.split(",").length;
					}
					String curKey = LightweightUtils.genQueryKey(this.curClassName, methodName, argSize);
					String obfusKey = LightweightUtils.genQueryKey(this.obfusClassName, methodName, argSize);
					
					//System.out.println("Cur key: " + curKey);
					//System.out.println("Obfus key: " + obfusKey);
					if (oriMethods.contains(curKey)) {
						mappings.put(curKey, obfusKey);
					} else {
						System.out.println("Not in call graph: " + curKey);
					}
				}
				
			};
			reader.pump(processor);
			
			return mappings;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		return null;
	}
	
	public static void main(String[] args) {
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine command = parser.parse(options, args);
			
			String callLoc = command.getOptionValue("c");
			File callDir = new File(callLoc);
			if (!callDir.exists()) {
				logger.error("Invalid callgraph loc: " + callDir.getAbsolutePath());
				System.exit(-1);
			}
			
			String truthLoc = command.getOptionValue("g");
			File truthFile = new File(truthLoc);
			if (!truthFile.exists()) {
				logger.error("Invalid truth file: " + truthFile.getAbsolutePath());
				System.exit(-1);
			}
			
			ArrayList<CallLookup> recorder = new ArrayList<CallLookup>();
			collectCallGraphs(callDir, recorder);
			logger.info("Call graphs (original) size: " + recorder.size());
			
			HashSet<String> allMethods = collectMethodAsQueries(recorder);
			logger.info("Method size: " + allMethods.size());
			
			File f = new File(truthLoc);
			HashMap<String, String> mappings = parseTruth(f, allMethods);
			logger.info("Find mappings: " + mappings.size());
			
			TypeToken<HashMap<String, String>> mapToken = new TypeToken<HashMap<String, String>>(){};
			String path = "maps/mapping.json";
			LightweightUtils.writeGsonGeneric(mappings, mapToken, path);
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}

}
