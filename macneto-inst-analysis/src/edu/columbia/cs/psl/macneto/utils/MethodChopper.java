package edu.columbia.cs.psl.macneto.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.stmt.BlockStmt;

import edu.columbia.cs.psl.macneto.db.DocManager;

public class MethodChopper {
	
	private static final Logger logger = LogManager.getLogger(MethodChopper.class);
	
	private static Options options = new Options();
	
	//Key methodKey, val code
	//private static HashMap<String, List<String>> recorder = new HashMap<String, List<String>>();
	private static ConcurrentHashMap<String, List<CodeData>> recorder = new ConcurrentHashMap<String, List<CodeData>>();
	
	public static int testCounter = 0;
	
	static {
		options.addOption("c", true, "Codebase");
		options.getOption("c").setRequired(true);
		
		options.addOption("o", true, "Output path");
		options.getOption("o").setRequired(true);
		
		options.addOption("w", false, "Write source code or not");
	}
	
	/*public static void main(String[] args) {
		try {
			File f = new File("/Users/mikefhsu/ncsws/macneto/macneto-inst-analysis/src/edu/columbia/cs/psl/macneto/utils/TestClass.java");
			parseJavaFile(f);
			
			for (String key: recorder.keySet()) {
				System.out.println("Method key: " + key);
				
				List<CodeData> cds = recorder.get(key);
				System.out.println("Code: ");
				for (CodeData cd: cds) {
					System.out.println("Comment: " + cd.comment);
					System.out.println("LOC: " + cd.loc);
					System.out.println(cd.code);
				}
			}
			
			FileInputStream fi = new FileInputStream("/Users/mikefhsu/ncsws/macneto/macneto-inst-analysis/src/edu/columbia/cs/psl/macneto/utils/TestClass.java");
			CompilationUnit cu = JavaParser.parse(fi);
			for (Node child: cu.getChildrenNodes()) {
				//System.out.println(child.getClass());
				//System.out.println(child);
				
				if (child instanceof ClassOrInterfaceDeclaration) {
					ClassOrInterfaceDeclaration classNode = (ClassOrInterfaceDeclaration)child;
					System.out.println("Class node: " + classNode.getName());
					//System.out.println(classNode.toString());
					System.out.println(classNode.getComment().getContent());
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}*/
		
	public static void main(String[] args) {
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine command = parser.parse(options, args);
			
			String codebasePath = command.getOptionValue("c");
			String outputPath = command.getOptionValue("o");
			boolean shouldWrite = command.hasOption("w");
			
			File codebase = new File(codebasePath);
			if (!codebase.exists()) {
				logger.error("Invalid codebase path: " + codebase.getAbsolutePath());
				System.exit(-1);
			}
			
			File output = new File(outputPath);
			if (!output.exists()) {
				logger.info("Creating output directory: " + output.getAbsolutePath());
				output.mkdir();
			}
			logger.info("Write source code: " + shouldWrite);
			
			ArrayList<File> javas = new ArrayList<File>();
			collectJavaFiles(codebase, javas);
			System.out.println("Total java files: " + javas.size());
			
			int counter = 0;
			for (File f: javas) {
				try {
					parseJavaFile(f);
					counter++;
					
					if (counter % 1000 == 0) {
						System.out.println("Parse #" + counter + " source files");
					}
				} catch (Exception ex) {
					logger.error("Parsing error: ", ex);
					logger.error("File: " + f.getAbsolutePath());
				}
			}
			
			writeJavaMethods(output, shouldWrite);
			logger.info("Possible test class: " + testCounter);
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
		
	public static void writeJavaMethods(File outDir, boolean shouldWrite) {
		try {
			logger.info("Total source methods: " + recorder.size());
			int counter = 0;
			//HashMap<String, int[]> writeToDb = new HashMap<String, int[]>();
			List<CodeRep> writeToDb = new ArrayList<CodeRep>();
			for (String methodKey: recorder.keySet()) {
				//System.out.println("Method: " + methodKey);
				//List<String> codeList = recorder.get(methodKey);
				List<CodeData> codeList = recorder.get(methodKey);
				int[] indices = new int[codeList.size()];
				int loc = 0;
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < indices.length; i++) {
					int codeIdx = counter;
					CodeData cd = codeList.get(i);
					
					if (shouldWrite) {
						String code = cd.code;
						String codePath = outDir.getAbsolutePath() + "/" + codeIdx + ".txt";
						
						byte[] codeBytes = code.getBytes();
						FileOutputStream fos = new FileOutputStream(codePath);
						fos.write(codeBytes);
						fos.close();
					}
					
					indices[i] = counter++;
					loc += cd.loc;
					
					if (cd.keywords != null) {
						sb.append(cd.keywords + " ");
					}
				}
				int avgLoc = (int)Math.round((double)loc/indices.length);
				
				String totalKeywords = null;
				if (sb.length() > 0) {
					totalKeywords = sb.toString().substring(0, sb.length() - 1);
				}
				
				CodeRep cr = CodeRep.genCodeRep(methodKey, indices, avgLoc, totalKeywords);
				writeToDb.add(cr);
				//writeToDb.put(methodKey, indices);
				
				if (counter % 10000 == 0) {
					System.out.println("Write #" + counter + " methods");
				}
			}
			
			logger.info("Total methods: " + counter);
			DocManager.insertSource(writeToDb);
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static void collectJavaFiles(File file, ArrayList<File> javas) {
		if (file.isFile()) {
			String name = file.getName().toLowerCase();;
			if (name.endsWith(".java")) {
				if (name.startsWith("test")) {
					testCounter++;
				} else {
					javas.add(file);
				}
			}
		} else if (file.isDirectory()){
			for (File f: file.listFiles()) {
				collectJavaFiles(f, javas);
			}
		}
	}
	
	public static void parseJavaFile(File javaFile) {		
		try {
			//FileInputStream in = new FileInputStream("/Users/mikefhsu/ncsws/macneto/macneto-inst-analysis/src/edu/columbia/cs/psl/macneto/utils/TestClass.java");
			FileInputStream in = new FileInputStream(javaFile);
			CompilationUnit cu = JavaParser.parse(in);
			
			//Identify package name and the root class node
			PackageDeclaration pkgNode = null;
			//ClassOrInterfaceDeclaration rootNode = null;
			ArrayList<ClassOrInterfaceDeclaration> rootNodes = new ArrayList<ClassOrInterfaceDeclaration>();
			ArrayList<EnumDeclaration> enumNodes = new ArrayList<EnumDeclaration>();
			for (Node n: cu.getChildrenNodes()) {
				//System.out.println(n.getClass());
				if (n instanceof PackageDeclaration) {
					if (pkgNode != null) {
						logger.error("Duplicated pkg name, please check: " + n);
						logger.error("Existing pkg: " + pkgNode);
						System.exit(-1);
					}
					
					pkgNode = (PackageDeclaration) n;
				} else if (n instanceof ClassOrInterfaceDeclaration) {
					/*if (rootNode != null) {
						logger.error("Duplicated root class: " + n);
						logger.error("Existing root class: " + rootNode);
						logger.error("File: " + javaFile.getAbsolutePath());
						System.exit(-1);
					}*/
					
					ClassOrInterfaceDeclaration rootNode = (ClassOrInterfaceDeclaration)n;
					rootNodes.add(rootNode);
				} else if (n instanceof EnumDeclaration) {
					EnumDeclaration enumNode = (EnumDeclaration)n;
					enumNodes.add(enumNode);
				}
			}
			
			if (rootNodes.size() == 0 && enumNodes.size() == 0) {
				logger.warn("No root and num node: " + javaFile.getAbsolutePath());
				//System.exit(-1);
			}
			
			if (rootNodes.size() > 0) {
				if (rootNodes.size() > 1) 
					logger.info("Multiple root node: " + rootNodes.size() + " " + javaFile.getAbsolutePath());
				
				for (ClassOrInterfaceDeclaration rootNode: rootNodes) {
					String rootClassName = null;
					if (pkgNode == null) {
						rootClassName = rootNode.getName();
					} else {
						rootClassName = pkgNode.getPackageName() + "." + rootNode.getName();
					}
					
					//System.out.println("Analyzing root class: " + rootClassName);
					traverseParsingTree(rootNode, rootClassName);
				}
			}
			
			
			if (enumNodes.size() > 0) {
				logger.info("Total enum node: " + enumNodes.size() + " " + javaFile.getAbsolutePath());
				for (EnumDeclaration enumNode: enumNodes) {
					String enumName = null;
					if (pkgNode == null) {
						enumName = enumNode.getName();
					} else {
						enumName = pkgNode.getPackageName() + "." + enumNode.getName();
					}
					
					//System.out.println("Analyzing enum: " + enumName);
					traverseParsingTree(enumNode, enumName);
				}
			}
			
		} catch (Exception ex) {
			logger.error("Error: ", ex);
			logger.error("Java file: " + javaFile.getAbsolutePath());
		}
	}
	
	public static void traverseParsingTree(Node n, String myFullName) {
		//System.out.println("Traversing: " + myFullName);
		List<Node> children = n.getChildrenNodes();
		for (Node c: children) {
			if (c instanceof ConstructorDeclaration) {
				ConstructorDeclaration construct = (ConstructorDeclaration)c;
				//String fullName = myFullName + "-" + "<init>";
				//String methodKey = fullName + "-" + construct.getParameters().size();
				String methodKey = LightweightUtils.genQueryKey(myFullName, "<init>", construct.getParameters().size());
				//int start = construct.getBegin().line;
				//int end = construct.getEnd().line;
				CodeData cd = composeConstructor(construct);
				if (cd == null) {
					continue ;
				}
				
				if (recorder.containsKey(methodKey)) {
					List<CodeData> cds = recorder.get(methodKey);
					if (!cds.contains(cd)) {
						recorder.get(methodKey).add(cd);
					}
				} else {
					List<CodeData> codeList = new ArrayList<CodeData>();
					codeList.add(cd);
					recorder.put(methodKey, codeList);
				}
				
				for (Node cc: construct.getChildrenNodes()) {
					if (cc instanceof ClassOrInterfaceDeclaration) {
						ClassOrInterfaceDeclaration innerClazz = (ClassOrInterfaceDeclaration)cc;
						String innerName = myFullName + "$" + innerClazz.getName();
						//System.out.println("Inner class in constructor: " + innerName);
						traverseParsingTree(cc, innerName);
					}
				}
			} else if (c instanceof InitializerDeclaration) {
				InitializerDeclaration clinit = (InitializerDeclaration)c;
				//int start = clinit.getBegin().line;
				//int end = clinit.getEnd().line;
				String methodKey = LightweightUtils.genQueryKey(myFullName, "<clinit>", 0);
				
				if (!recorder.containsKey(methodKey)) {
					List<CodeData> codeList = new ArrayList<CodeData>();
					
					CodeData cd = composeStaticConstructor(clinit);
					if (cd == null) {
						continue ;
					}
					
					//System.out.println("clinit");
					//System.out.println(cd.code);
					codeList.add(cd);
					recorder.put(methodKey, codeList);
				} else {
					//This means some 3rd party methods are included by several apks
				}
			} else if (c instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration)c;
				//int start = method.getBegin().line;
				//int end = method.getEnd().line;
				//String fullName = myFullName + "-" + method.getName();
				//String methodKey = fullName + "-" + method.getParameters().size();
				String methodKey = LightweightUtils.genQueryKey(myFullName, method.getName(), method.getParameters().size());
				if (recorder.containsKey(methodKey)) {
					CodeData cd = composeMethod(method);
					if (cd == null) {
						continue ;
					}
					
					List<CodeData> cds = recorder.get(methodKey);
					if (!cds.contains(cd)) {
						recorder.get(methodKey).add(cd);
					} /*else {
						System.out.println("Check cd: " + cd);
						System.out.println("Existing: ");
						for (CodeData e: recorder.get(methodKey)) {
							System.out.println(e);
						}
						System.exit(0);
					}*/
				} else {
					CodeData cd = composeMethod(method);
					if (cd == null) {
						continue ;
					}
					
					List<CodeData> codeList = new ArrayList<CodeData>();
					//codeList.add(method.toString());
					codeList.add(cd);
					recorder.put(methodKey, codeList);
				}
				//System.out.println("Method: " + method);
				
				for (Node cc: method.getChildrenNodes()) {
					if (cc instanceof ClassOrInterfaceDeclaration) {
						ClassOrInterfaceDeclaration innerClazz = (ClassOrInterfaceDeclaration)cc;
						String innerName = myFullName + "$" + innerClazz.getName();
						//System.out.println("Inner class in method: " + innerName);
						traverseParsingTree(cc, innerName);
					} /*else if (cc instanceof BlockStmt){
						System.out.println("Block stmt");
						BlockStmt bs = (BlockStmt)cc;
						for (Node bn: bs.getChildrenNodes()) {
							System.out.println("B-Childe: " + bn);
							System.out.println(bn.getClass());
						}
					} else {
						System.out.println("Child node in method: " + cc.getClass());
						System.out.println("Child node in method: " + cc);
					}*/
				}
			} else if (c instanceof EnumDeclaration) {
				EnumDeclaration enumNode = (EnumDeclaration)c;
				String fullName = myFullName + "$" + enumNode.getName();
				//System.out.println("Inner enum in class: " + fullName);
				traverseParsingTree(enumNode, fullName);
			} else if (c instanceof ClassOrInterfaceDeclaration) {
				ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration)c;
				String fullName = myFullName + "$" + clazz.getName();
				//System.out.println("Inner class in class: " + fullName);
				traverseParsingTree(clazz, fullName);
			}
		}
	}
	
	public static CodeData composeConstructor(ConstructorDeclaration constructor) {
		StringBuilder sb = new StringBuilder();
		String name = constructor.getDeclarationAsString();
		BlockStmt body = constructor.getBlock();
		if (body == null) {
			return null;
		}
		String bodyString = body.toString();
		
		String cleanComment = null;
		if (constructor.hasComment()) {
			sb.append(constructor.getComment());
			
			//pureComment = constructor.getComment().getContent().replace("*", "").trim();
			cleanComment = TextAnalyzer.cleanComments(constructor.getComment().getContent());
			//cleanComment = TextAnalyzer.extractKeywords(cleanComment);
		}
		
		sb.append(name + " ");
		sb.append(bodyString);
		
		int loc = lineNumber(bodyString);
		String decamal = TextAnalyzer.decamelize(constructor.getName());
		String allContents = decamal;
		if (cleanComment != null) {
			allContents = allContents + " " + cleanComment;
		}
		
		String keywords = TextAnalyzer.extractKeywords(allContents);
		
		CodeData cd = CodeData.genCodeData(sb.toString(), keywords, loc);
		return cd;
	}
	
	public static CodeData composeMethod(MethodDeclaration method) {
		StringBuilder sb = new StringBuilder();
		String name = method.getDeclarationAsString();
		BlockStmt body = method.getBody();
		if (body == null) {
			return null;
		}
		String bodyString = body.toString();
		
		//String commentKeywords = null;
		String cleanComments = null;
		if (method.hasComment()) {
			sb.append(method.getComment());
			
			cleanComments = TextAnalyzer.cleanComments(method.getComment().getContent());
			//pureComment = method.getComment().getContent().replace("*", "").trim();
			//commentKeywords = TextAnalyzer.extractKeywords(method.getComment().getContent(), true);
		}
		
		sb.append(name + " ");
		sb.append(bodyString);
		
		int loc = lineNumber(bodyString);
		//extracting keywords from method name and comments
		String decamal = TextAnalyzer.decamelize(method.getName());
		String allContents = decamal;
		if (cleanComments != null) {
			allContents = allContents + " " + cleanComments;
		}
		
		String keywords = TextAnalyzer.extractKeywords(allContents);
		/*if (commentKeywords != null) {
			nameKeywords += (" " + commentKeywords);
		}*/
		
		CodeData cd = CodeData.genCodeData(sb.toString(), keywords, loc);
		return cd;
	}
		
	public static CodeData composeStaticConstructor(InitializerDeclaration clinit) {
		StringBuilder sb = new StringBuilder();
		BlockStmt body = clinit.getBlock();
		String bodyString = body.toString();
		
		String cleanComment = null;
		if (clinit.hasComment()) {
			sb.append(clinit.getComment());
			
			//pureComment = clinit.getComment().getContent().replace("*", "").trim();
			cleanComment = TextAnalyzer.cleanComments(clinit.getComment().getContent());
			cleanComment = TextAnalyzer.extractKeywords(cleanComment);
		}
		
		sb.append("static ");
		sb.append(bodyString);
		int loc = lineNumber(bodyString);
		CodeData cd = CodeData.genCodeData(sb.toString(), cleanComment, loc);
		return cd;
	}
	
	public static int lineNumber(String body) {
		Matcher m = Pattern.compile("\r\n|\r|\n").matcher(body);
		int lines = 1;
		while (m.find()) {
		    lines++;
		}
		
		return lines;
	}
	
	public static void analyzeInnerClass(ClassOrInterfaceDeclaration klass, String cumuName) {
		if (klass.isInterface()) {
			return ;
		}
		
		String myName = cumuName + "." + klass.getName();
		System.out.println("My name: " + myName);
		for (BodyDeclaration member: klass.getMembers()) {
			if (member instanceof MethodDeclaration) {
				System.out.println("Method: " + member);
			} else if (member instanceof ConstructorDeclaration) {
				System.out.println("Constructor: " + member);
			} else if (member instanceof ClassOrInterfaceDeclaration) {
				System.out.println("Inner class: " + member);
			}
		}
	}
	
	public static class CodeData {
		
		public String code;
		
		String keywords = null;
		
		public int loc;
		
		public static CodeData genCodeData(String code, String keywords, int loc) {
			CodeData cd = new CodeData();
			cd.code = code;
			cd.keywords = keywords;
			cd.loc = loc;
			return cd;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Code: " + code + "\n");
			sb.append("LOC: " + loc + "\n");
			return sb.toString();
		}
		
		@Override
		public int hashCode() {
			int result = 17;
			result = 31 * result + this.code.hashCode();
			result = 31 * result + this.loc;
			//comment does not matter
			
			return result;
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof CodeData)) {
				return false;
			}
			
			CodeData cd = (CodeData)o;
			if (!cd.code.equals(this.code)) {
				return false;
			}
			
			if (cd.loc != this.loc) {
				return false;
			}
			
			return true;
		}
	}
	
	public static class CodeRep {
		public String methodKey;
		
		public int[] indices;
		
		public int loc;
		
		public String keywords;
		
		public static CodeRep genCodeRep(String methodKey, int[] indices, int loc, String keywords) {
			CodeRep cr = new CodeRep();
			cr.methodKey = methodKey;
			cr.indices = indices;
			cr.loc = loc;
			cr.keywords = keywords;
			
			return cr;
		}
	}
}
