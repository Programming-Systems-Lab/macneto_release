package edu.columbia.cs.psl.macneto.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.db.DocManager;
import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;

public class AllatoriMapAnalyzer extends AbstractMapAnalyzer {
	
	private static final Options options = new Options();
	
	private static Logger logger = LogManager.getLogger(AllatoriMapAnalyzer.class);
	
	private static int TOTAL_METHODS = 0;
	
	public static HashSet<String> SEEN = new HashSet<String>();
	
	static {
		options.addOption("m", true, "map file");
		options.getOption("m").setRequired(true);
		
		options.addOption("o", true, "output dir");
		options.getOption("o").setRequired(true);
		
		options.addOption("ob", true, "obfus db");
		
		sb.append("app,exist,non-exist,non_exist_obfus,in-graph\n");
	}
	
	public static String genArrayArg(Type arg, HashMap<String, String> mapping) {
		int dim = arg.getDimensions();
		Type arrType = arg.getElementType();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < dim; i++) {
			sb.append("[");
		}
		
		String oriType = LightweightUtils.parseSingleArg(arrType);
		String obfusType = mapping.get(oriType);
		if (obfusType == null) {
			obfusType = oriType;
			if (!SEEN.contains(oriType)) {
				//logger.warn("Find no mapping: " + oriType);
				SEEN.add(oriType);
			}
		}
		
		sb.append(obfusType);
		return sb.toString();
	}
	
	public static String genEquivalentArg(String oriDesc, HashMap<String, String> mapping) {
		//System.out.println("Ori desc: " + oriDesc);
		Type[] args = Type.getArgumentTypes(oriDesc);
		Type returnType = Type.getReturnType(oriDesc);
		StringBuilder argBuilder = new StringBuilder();
		for (Type arg: args) {
			String argString = null;
			if (arg.getSort() == Type.ARRAY) {
				argString = genArrayArg(arg, mapping);
			} else {
				String oriType = LightweightUtils.parseSingleArg(arg);
				String obfusType = mapping.get(oriType);
				if (obfusType == null) {
					obfusType = oriType;
					
					if (!SEEN.contains(oriType)) {
						//logger.warn("Find no mapping: " + oriType);
						SEEN.add(oriType);
					}
				}
				argString = obfusType;
			}
			argBuilder.append(argString + "+");
		}
		
		String argString = null;
		if (argBuilder.length() == 0) {
			argString = "()";
		} else {
			argString = "(" + argBuilder.substring(0, argBuilder.length() - 1) + ")";
		}
		
		String obfusReturn = null;
		if (returnType.getSort() == Type.ARRAY) {
			obfusReturn = genArrayArg(returnType, mapping);
		} else {
			String oriReturn = LightweightUtils.parseSingleArg(returnType);
			obfusReturn = mapping.get(oriReturn);
			
			if (obfusReturn == null) {
				obfusReturn = oriReturn;
				if (!SEEN.contains(oriReturn)) {
					//logger.warn("Find no mapping: " + oriReturn);
					SEEN.add(oriReturn);
				}
			}
		}
		
		return argString + obfusReturn;
	}
		
	public static void processSingleMapFile(File mapFile, File outputDir) {
		try {
			String appName = mapFile.getName().substring(0, mapFile.getName().length() - 4);
			
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(mapFile);
			doc.getDocumentElement().normalize();
			
			Node rootNode = doc.getDocumentElement();
			NodeList classNodes = doc.getElementsByTagName("class");
			//Need two iterations, first build class map
			HashMap<String, String> classMap = new HashMap<String, String>();
			for (int i = 0; i < classNodes.getLength(); i++) {
				Node n = classNodes.item(i);
				
				if (n.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) n;
					String oldClass = e.getAttribute("old");
					String newClass = e.getAttribute("new");
					
					if (shouldPass(oldClass)) {
						continue ;
					}
					classMap.put(oldClass, newClass);
				}
			}
			System.out.println("Total class: " + classMap.size());
			
			//2nd iteration, build method key
			HashMap<String, String> methodsToDump = new HashMap<String, String>();
			for (int i = 0; i < classNodes.getLength(); i++) {
				Node n = classNodes.item(i);
				if (n.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) n;
					String oldClass = e.getAttribute("old");
					
					if (shouldPass(oldClass)) {
						continue ;
					}
					
					//Should not be null
					String newClass = classMap.get(oldClass);
					NodeList methodNodes = e.getElementsByTagName("method");
					for (int j = 0; j < methodNodes.getLength(); j++) {
						Node methodN = methodNodes.item(j);
						
						if (methodN.getNodeType() == Node.ELEMENT_NODE) {
							Element me = (Element) methodN;
							String oldMethod = me.getAttribute("old");
							String[] oldMethodInfo = parseMethod(oldMethod);
							String oldKey = LightweightUtils.genMethodKey(oldClass, oldMethodInfo[0], oldMethodInfo[1]);
							
							String newMethod = me.getAttribute("new");
							String newArg = genEquivalentArg(oldMethodInfo[1], classMap);
							String newKey = newClass + LightweightUtils.DELIM + newMethod + LightweightUtils.DELIM + newArg;
							//System.out.println("m " + oldKey + "->" + newKey);
							
							methodsToDump.put(newKey, oldKey);
						}
					}
				}
			}
			
			String classMapPath = outputDir.getAbsolutePath() + "/" + appName + "_c.csv";
			final BufferedWriter bw = new BufferedWriter(new FileWriter(classMapPath));
			classMap.forEach((o, n)->{
				try {
					bw.write(o + "," + n + "\n");
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
			bw.close();
			
			String methodMapPath = outputDir.getAbsolutePath() + "/" + appName + "_m.csv";
			final BufferedWriter bw2 = new BufferedWriter(new FileWriter(methodMapPath));
			methodsToDump.forEach((n, o)->{
				try {
					bw2.write(n + "," + o + "\n");
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
			bw2.close();
			
			//No need to check, allatori does not include constructors in their mapping files
			logger.info("Analyzing existing methods: " + appName);
			HashSet<String> allMethods = DocManager.getAllMethods(appName, null);
			if (allMethods == null) {
				logger.warn("No apk info in db: " + appName);
				return ;
			}
			
			HashSet<String> allObfus = null;
			if (OBDB != null) {
				allObfus = DocManager.getAllMethods(appName + "_obfus", OBDB);
			}
			
			if (allObfus != null) {
				logger.info("All obfus size: " + allObfus.size());
			} else {
				logger.info("0 obfus");
			}
			
			int non_exist = 0;
			int non_exist_obfus = 0;
			//Check how many methods exist in db
			for (String obfus: methodsToDump.keySet()) {
				String m = methodsToDump.get(obfus);
				if (!allMethods.contains(m)) {
					non_exist++;
					
					if (non_exist <= 10) {
						logger.info("Non-exist: " + m);
					}
				}
				
				if (allObfus != null && !allObfus.contains(obfus)) {
					non_exist_obfus++;
					if (non_exist_obfus <= 10) {
						logger.info("Non-exist obfus: " + obfus);
					}
				}
			}
			logger.info("# Total methods in db for " + appName + " " + allMethods.size());
			logger.info("# Non-exist: " + non_exist);
			logger.info("# Non-exist obfus: " + non_exist_obfus);
			int exist = methodsToDump.size() - non_exist;
			logger.info("# exist: " + exist);
			
			File gFile = new File("./callgraphs/" + appName + ".json");
			TypeToken<CallLookup> tok = new TypeToken<CallLookup>(){};
			CallLookup calls = LightweightUtils.readGsonGeneric(gFile, tok);
			int in_counter = 0;
			for (CallNode node: calls.getCalls().values()) {
				String methodKey = node.getMethodKey();
				String[] ele = methodKey.split("-");
				if (ele[1].equals("<init>")) {
					continue;
				}
				
				if (ele[1].equals("<clinit>")) {
					continue ;
				}
				
				in_counter++;
			}
			logger.info("# in graph: " + in_counter);
			
			sb.append(appName + "," + exist + "," + non_exist + "," + non_exist_obfus + "," + in_counter + "\n");
			
			TOTAL_METHODS += methodsToDump.size();
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static void main(String[] args) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine commands = parser.parse(options, args);
		
		String mapFilePath = commands.getOptionValue("m");
		File mapFile = new File(mapFilePath);
		if (!mapFile.exists()) {
			logger.error("Invalid map directory: " + mapFile.getAbsolutePath());
			System.exit(-1);
		}
		
		List<File> maps = new ArrayList<File>();
		collectMap(mapFile, maps);
		logger.info("Total map files: " + maps.size());
		
		String outputDirPath = commands.getOptionValue("o");
		File outputDir = new File(outputDirPath);
		if (!outputDir.exists()) {
			outputDir.mkdir();
			logger.info("Creating output directory: " + outputDir.getAbsolutePath());
		}
		
		if (commands.hasOption("ob")) {
			OBDB = commands.getOptionValue("ob");
		}
		
		int counter = 0;
		for (File map: maps) {
			processSingleMapFile(map, outputDir);
			counter++;
			
			if (counter % 100 == 0) {
				System.out.println("Procssed #" + counter + " apks");
			}
		}
		
		logger.info("Total methods: " + TOTAL_METHODS);
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("check.csv"));
			bw.write(sb.toString());
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
