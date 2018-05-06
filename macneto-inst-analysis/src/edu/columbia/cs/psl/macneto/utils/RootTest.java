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

public class RootTest {
	
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
	}
	
	public static void main(String[] args) {
		try {
			/*File f = new File("/Users/mikefhsu/ncsws/macneto/macneto-inst-analysis/src/edu/columbia/cs/psl/macneto/utils/TestClass.java");
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
			}*/
			File rootDir = new File("/Users/mikefhsu/Desktop/test_analysis");
			ArrayList<File> javas = new ArrayList<File>();
			collectJavaFiles(rootDir, javas);
			
			for (File f: javas) {
				parseJavaFile(f);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
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
					System.out.println("Multiple root node: " + rootNodes.size() + " " + javaFile.getAbsolutePath());
				
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
				int start = construct.getBegin().line;
				int end = construct.getEnd().line;
				if (end - start + 1 >= 10)
					System.out.println("Constructor: " + methodKey + " " + start + " " + end);
				
				for (Node cc: construct.getChildrenNodes()) {
					if (cc instanceof ClassOrInterfaceDeclaration) {
						ClassOrInterfaceDeclaration innerClazz = (ClassOrInterfaceDeclaration)cc;
						String innerName = myFullName + "$" + innerClazz.getName();
						//System.out.println("Inner class in constructor: " + innerName);
						traverseParsingTree(cc, innerName);
					}
				}
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
		String bodyString = body.toString();
		
		String pureComment = null;
		if (constructor.hasComment()) {
			sb.append(constructor.getComment());
			pureComment = constructor.getComment().getContent().replace("*", "").trim();
		}
		
		sb.append(name + " ");
		sb.append(bodyString);
		
		int loc = lineNumber(bodyString);
		CodeData cd = CodeData.genCodeData(sb.toString(), pureComment, loc);
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
		
		String pureComment = null;
		if (method.hasComment()) {
			sb.append(method.getComment());
			pureComment = method.getComment().getContent().replace("*", "").trim();
		}
		
		sb.append(name + " ");
		sb.append(bodyString);
		
		int loc = lineNumber(bodyString);
		CodeData cd = CodeData.genCodeData(sb.toString(), pureComment, loc);
		return cd;
	}
		
	public static CodeData composeStaticConstructor(InitializerDeclaration clinit) {
		StringBuilder sb = new StringBuilder();
		BlockStmt body = clinit.getBlock();
		String bodyString = body.toString();
		
		String pureComment = null;
		if (clinit.hasComment()) {
			sb.append(clinit.getComment());
			pureComment = clinit.getComment().getContent().replace("*", "").trim();
		}
		
		sb.append("static ");
		sb.append(bodyString);
		int loc = lineNumber(bodyString);
		CodeData cd = CodeData.genCodeData(sb.toString(), pureComment, loc);
		return cd;
	}
	
	public static int lineNumber(String body) {
		Matcher m = Pattern.compile("\r\n|\r|\n").matcher(body);
		int lines = 1;
		while (m.find()) {
		    lines ++;
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
		
		String comment = null;
		
		public int loc;
		
		public static CodeData genCodeData(String code, String comment, int loc) {
			CodeData cd = new CodeData();
			cd.code = code;
			cd.comment = comment;
			cd.loc = loc;
			return cd;
		}
		
		@Override
		public int hashCode() {
			int result = 17;
			result = 31 * result + code.hashCode();
			result = 31 * result + loc;
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
		
		public String comment;
		
		public static CodeRep genCodeRep(String methodKey, int[] indices, int loc, String comment) {
			CodeRep cr = new CodeRep();
			cr.methodKey = methodKey;
			cr.indices = indices;
			cr.loc = loc;
			cr.comment = comment;
			
			return cr;
		}
	}
}

