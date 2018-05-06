package edu.columbia.cs.psl.macneto.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.db.DocManager;
import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import proguard.obfuscate.MappingProcessor;
import proguard.obfuscate.MappingReader;

/**
 * The process mapping methods refer to https://github.com/facebook/proguard/blob/master/src/proguard/obfuscate/MappingProcessor.java
 * @author mikefhsu
 *
 */
public class ProguardMapAnalyzer extends AbstractMapAnalyzer {
	
	private static final Options options = new Options();
	
	private static final Logger logger = LogManager.getLogger(ProguardMapAnalyzer.class);
	
	private static final StringBuilder sb = new StringBuilder();
	
	private static String OBDB = null;
	
	private static int TOTAL_METHODS = 0;
	
	static {
		options.addOption("m", true, "map file");
		options.getOption("m").setRequired(true);
		
		options.addOption("o", true, "output dir");
		options.getOption("o").setRequired(true);
		
		options.addOption("ob", true, "obfus db");
		
		//options.addOption("or", true, "original call graph dir");
		//options.getOption("or").setRequired(true);
		
		//options.addOption("ob", true, "obfusctaed call graph dir");
		//options.getOption("ob").setRequired(true);
		sb.append("app,exist,non-exist,in-db\n");
	}
	
	public static void tmp(File mapFile) throws Exception {
		//Need two round to pass, first round find out class mapping
				//second round find out method mapping
				BufferedReader br = new BufferedReader(new FileReader(mapFile));
				HashMap<String, String> classMapping = new HashMap<String, String>();
				String buf = null;
				while ((buf = br.readLine()) != null) {
					if (buf.matches("^(?! +).*")) {
						//System.out.println("Class: " + buf);
						String[] classInfo = buf.split(" -> ");
						String oriClass = classInfo[0];
						String newClass = classInfo[1].substring(0, classInfo[1].length() - 1);
						//System.out.println("Ori class: " + oriClass);
						//System.out.println("New class: " + newClass);
						
						classMapping.put(oriClass, newClass);
					}
				}
				System.out.println("Total class mapping: " + classMapping.size());
				
				br = new BufferedReader(new FileReader(mapFile));
				String curClass = null;
				String curNewClass = null;
				buf = null;
				while ((buf = br.readLine()) != null) {
					if (buf.matches("^(?! +).*")) {
						//System.out.println("Class: " + buf);
						String[] classInfo = buf.split(" -> ");
						curClass = classInfo[0];
						curNewClass = classMapping.containsKey(curClass)?classMapping.get(curClass):curClass;
						
					} else if (buf.matches(".+[(].*[)].*")){
						buf = buf.trim();
						System.out.println("Method: " + buf);
						
						String[] info = buf.split(" -> ");
						
						String oriString = info[0];
						String oriName = oriString.split(" ")[1];
						if (oriName.contains(":")) {
							int firstIdx = oriName.indexOf(":");
							oriName = oriName.substring(0, firstIdx);
						}
						//Check parameter number
						int firstIdx = oriName.indexOf("(");
						int lastIdx = oriName.lastIndexOf(")");
						String argString = oriName.substring(firstIdx + 1, lastIdx);
						int argNum = 0;
						
						StringBuilder oriSb = new StringBuilder();
						StringBuilder obfusSb = new StringBuilder();
						if (argString.length() > 0) {
							String[] argNames = argString.split(",");
							for (String s: argNames) {
								String oriArg = null;
								String obfusArg = isArray(s, classMapping);
								if (obfusArg == null) {
									obfusArg = isPrim(s);
									if (obfusArg == null) {
										if (classMapping.containsKey(s)) {
											obfusArg = classMapping.get(s);
										} else {
											obfusArg = s;
										}
										oriArg = s;
									} else {
										oriArg = obfusArg;
									}
								} else {
									oriArg = isArray(s, null);
								}
								
								oriSb.append(oriArg + "+");
								obfusSb.append(obfusArg + "+");
							} 
							argNum = argString.split(",").length;
						}
						
						oriName = oriName.substring(0, firstIdx);
						String newName = info[1];
						
						String oriArgString = oriSb.length() > 0? oriSb.substring(0, oriSb.length() - 1): "";
						String newArgString = obfusSb.length() > 0? obfusSb.substring(0, obfusSb.length() - 1): "";
						
						oriName = curClass + "-" + oriName + "-" + "(" + oriArgString + ")";
						newName = curNewClass + "-" + newName + "-" + "(" + newArgString + ")";
						System.out.println("Ori name: " + oriName + " " + argNum);
						System.out.println("New name: " + newName);
					} else {
						//System.out.println("Field: " + buf);
					}
				}
	}
	
	public static void processSingleMap(File mapFile, File outputDir) {
		String appName = mapFile.getName().substring(0, mapFile.getName().length() - 4);
		
		//HashMap<String, String> classMapping = new HashMap<String, String>();
		HashMap<String, String> revClassMapping = new HashMap<String, String>();
		HashMap<String, String> methodsToDump = new HashMap<String, String>();
		HashMap<String, String> revMethodsToDump = new HashMap<String, String>();
		
		try {
			MappingReader reader = new MappingReader(mapFile);
			MappingProcessor processor = new MappingProcessor() {

				@Override
				public boolean processClassMapping(String className, String newClassName) {
					// Obfuscated class name -> original class name.
					//classMap.put(newClassName, className);
					
					//classMapping.put(newClassName, className);
					revClassMapping.put(className, newClassName);
					/*if (!className.startsWith("android"))
						System.out.println("Obfus class-Ori class: " + newClassName + " " + className);*/
					return true;
				}

				@Override
				public void processFieldMapping(String className,
						String fieldType, String fieldName, String newClassName, String newFieldName) {
					return ;
				}

				@Override
				public void processMethodMapping(String className,
	                    int    firstLineNumber,
	                    int    lastLineNumber,
	                    String methodReturnType,
	                    String methodName,
	                    String methodArguments,
	                    String newClassName,
	                    int    newFirstLineNumber,
	                    int    newLastLineNumber,
	                    String newMethodName) {
					return ;
				}
				
			};
			
			reader.pump(processor);
			
			//System.out.println("Check com.jcraft.jzlib.ZStream: " + revClassMapping.get("com.jcraft.jzlib.ZStream"));
			
			MappingReader methodReader = new MappingReader(mapFile);
			MappingProcessor methodprocessor = new MappingProcessor() {
				private final Map classMethodMap = new HashMap();

				@Override
				public boolean processClassMapping(String className, String newClassName) {
					return true;
				}

				@Override
				public void processFieldMapping(String className,
						String fieldType, String fieldName, String newClassName, String newFieldName) {
					return ;
				}

				@Override
				public void processMethodMapping(String className,
	                    int    firstLineNumber,
	                    int    lastLineNumber,
	                    String methodReturnType,
	                    String methodName,
	                    String methodArguments,
	                    String newClassName,
	                    int    newFirstLineNumber,
	                    int    newLastLineNumber,
	                    String newMethodName) {
					// TODO Auto-generated method stub
					// Original class name -> obfuscated method names.
			        Map methodMap = (Map)classMethodMap.get(newClassName);
			        if (methodMap == null)
			        {
			            methodMap = new HashMap();
			            classMethodMap.put(newClassName, methodMap);
			        }

			        // Obfuscated method name -> methods.
			        Set methodList = (Set)methodMap.get(newMethodName);
			        if (methodList == null)
			        {
			            methodList = new LinkedHashSet();
			            methodMap.put(newMethodName, methodList);
			        }

			        // Add the method information.
			        methodList.add(new MethodInfo(newFirstLineNumber,
			                                      newLastLineNumber,
			                                      className,
			                                      firstLineNumber,
			                                      lastLineNumber,
			                                      methodReturnType,
			                                      methodName,
			                                      methodArguments));
			        
			        if (!className.startsWith("android")) {
			        	StringBuilder oriSb = new StringBuilder();
						StringBuilder obfusSb = new StringBuilder();
						int argNum = 0;
						if (methodArguments.length() > 0) {
							String[] argNames = methodArguments.split(",");
							for (String s: argNames) {
								String oriArg = null;
								String obfusArg = isArray(s, revClassMapping);
								if (obfusArg == null) {
									obfusArg = isPrim(s);
									if (obfusArg == null) {
										if (revClassMapping.containsKey(s)) {
											obfusArg = revClassMapping.get(s);
										} else {
											obfusArg = s;
										}
										oriArg = s;
									} else {
										oriArg = obfusArg;
									}
								} else {
									oriArg = isArray(s, null);
								}
								
								if (oriArg == null || obfusArg == null) {
									System.out.println("Suspicious argument: " + s);
									System.exit(-1);
								}
								
								oriSb.append(oriArg + "+");
								obfusSb.append(obfusArg + "+");
							} 
							argNum = methodArguments.split(",").length;
						}
						
						String obfusReturn = null;
						String oriReturn = null;
						if (methodReturnType.equals("void")) {
							obfusReturn = "V";
							oriReturn = "V";
						} else {
							obfusReturn = isArray(methodReturnType, revClassMapping);
							oriReturn = null;
							if (obfusReturn == null) {
								obfusReturn = isPrim(methodReturnType);
								if (obfusReturn == null) {
									if (revClassMapping.containsKey(methodReturnType)) {
										obfusReturn = revClassMapping.get(methodReturnType);
									} else {
										obfusReturn = methodReturnType;
									}
									oriReturn = methodReturnType;
								} else {
									oriReturn = obfusReturn;
								}
							} else {
								oriReturn = isArray(methodReturnType, null);
							}
						}
						
						if (obfusReturn == null || oriReturn == null) {
							System.out.println("Suspicious return: " + methodReturnType);
							System.exit(-1);
						}
						
						String expected = revClassMapping.get(className);
						//System.out.println("New-old: " + newClassName + " " + className);
						String oriArgString = oriSb.length() > 0? oriSb.substring(0, oriSb.length() - 1): "";
						String newArgString = obfusSb.length() > 0? obfusSb.substring(0, obfusSb.length() - 1): "";
						String oriComplete = className + "-" + methodName + "-(" + oriArgString + ")" + oriReturn;
						String newComplete = expected + "-" + newMethodName + "-(" + newArgString + ")" + obfusReturn;
						//System.out.println("Obfus method-Ori method: " + oriComplete + "->" + newComplete);
						methodsToDump.put(newComplete, oriComplete);
						revMethodsToDump.put(oriComplete, newComplete);
			        }
				}
				
			};
			
			methodReader.pump(methodprocessor);
			
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
			
			//logger.info("Analyzing existing methods: " + appName);
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
			int exist_obfus = methodsToDump.size() - non_exist_obfus;
			
			File gFile = new File("./callgraphs/" + appName + ".json");
			TypeToken<CallLookup> tok = new TypeToken<CallLookup>(){};
			CallLookup calls = LightweightUtils.readGsonGeneric(gFile, tok);
			logger.info("# in graph: " + calls.getCalls().size());
			
			sb.append(appName + "," + exist + "," + non_exist + "," + non_exist_obfus + "," + calls.getCalls().size() + "\n");
			
			TOTAL_METHODS += exist;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		CommandLine commands = parser.parse(options, args);
		
		String mapFilePath = commands.getOptionValue("m");
		File mapFile = new File(mapFilePath);
		if (!mapFile.exists()) {
			logger.error("Invalid map directory: " + mapFile.getAbsolutePath());
			System.exit(-1);
		}
		
		if (commands.hasOption("ob")) {
			OBDB = commands.getOptionValue("ob");
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
		
		for (File map: maps) {
			System.out.println("Processing: " + map.getAbsolutePath());
			processSingleMap(map, outputDir);
		}
		
		logger.info("Total methods: " + TOTAL_METHODS);
		//Create obfuscated callgraphs
		//processObfuscatedCallGraphs(orCallGraph, obCallGraph, appName + "_obfus", revMethodsToDump);
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("check.csv"));
			bw.write(sb.toString());
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	/**
     * Copy from retracer
     * Information about the original version and the obfuscated version of
     * a field (without the obfuscated class name or field name).
     */
    public static class FieldInfo
    {
        private final String originalClassName;
        private final String originalType;
        private final String originalName;


        /**
         * Creates a new FieldInfo with the given properties.
         */
        private FieldInfo(String originalClassName,
                          String originalType,
                          String originalName)
        {
            this.originalClassName = originalClassName;
            this.originalType      = originalType;
            this.originalName      = originalName;
        }


        /**
         * Returns whether the given type matches the original type of this field.
         * The given type may be a null wildcard.
         */
        private boolean matches(String originalType)
        {
            return
                originalType == null || originalType.equals(this.originalType);
        }
    }


    /**
     * Copy from retracer
     * Information about the original version and the obfuscated version of
     * a method (without the obfuscated class name or method name).
     */
    public static class MethodInfo
    {
        private final int    obfuscatedFirstLineNumber;
        private final int    obfuscatedLastLineNumber;
        private final String originalClassName;
        private final int    originalFirstLineNumber;
        private final int    originalLastLineNumber;
        private final String originalType;
        private final String originalName;
        private final String originalArguments;


        /**
         * Creates a new MethodInfo with the given properties.
         */
        private MethodInfo(int    obfuscatedFirstLineNumber,
                           int    obfuscatedLastLineNumber,
                           String originalClassName,
                           int    originalFirstLineNumber,
                           int    originalLastLineNumber,
                           String originalType,
                           String originalName,
                           String originalArguments)
        {
            this.obfuscatedFirstLineNumber = obfuscatedFirstLineNumber;
            this.obfuscatedLastLineNumber  = obfuscatedLastLineNumber;
            this.originalType              = originalType;
            this.originalArguments         = originalArguments;
            this.originalClassName         = originalClassName;
            this.originalName              = originalName;
            this.originalFirstLineNumber   = originalFirstLineNumber;
            this.originalLastLineNumber    = originalLastLineNumber;
        }


        /**
         * Returns whether the given properties match the properties of this
         * method. The given properties may be null wildcards.
         */
        private boolean matches(int    obfuscatedLineNumber,
                                String originalType,
                                String originalArguments)
        {
            return
                (obfuscatedLineNumber == 0 ? obfuscatedLastLineNumber == 0 :
                     obfuscatedFirstLineNumber <= obfuscatedLineNumber && obfuscatedLineNumber <= obfuscatedLastLineNumber) &&
                (originalType         == null || originalType.equals(this.originalType))                                    &&
                (originalArguments    == null || originalArguments.equals(this.originalArguments));
        }
    }

}
