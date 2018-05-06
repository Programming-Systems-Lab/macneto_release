package edu.columbia.cs.psl.macneto.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;

/**
 * Copy some utility functions fsrom inst-mining
 * A better approach is to create a util jar
 * @author mikefhsu
 *
 */
public class LightweightUtils {
	
	private static final Logger logger = LogManager.getLogger(LightweightUtils.class);
	
	private static HashMap<String, Integer> SYSCALLS;
	
	private static HashSet<String> COMPS;
	
	private static HashMap<String, String> nameMap = new HashMap<String, String>();
	
	private static HashMap<String, Integer> nameCounter = new HashMap<String, Integer>();
	
	public static String DELIM = "-";
	
	public static String RE_SLASH = ".";
	
	public static String ANDROID_MODEL = "./api_model/android.json";
	
	public static void init(String androidModel) {
		if (androidModel != null) {
			ANDROID_MODEL = androidModel;
		}
		
		SYSCALLS = InstructionUtils.apiLookup;
		logger.info("Syscalls: " + SYSCALLS.size());
		
		COMPS = InstructionUtils.compLookup;
		logger.info("Comp size: " + COMPS.size());
		/*File sysCall = new File(ANDROID_MODEL);
		if (!sysCall.exists()) {
			logger.info("No sys call file: " + sysCall.getAbsolutePath());
			SYSCALLS = new HashMap<String, Integer>();
		} else {
			TypeToken<HashMap<String, Integer>> token = new TypeToken<HashMap<String, Integer>>(){};
			SYSCALLS = readGsonGeneric(sysCall, token);
		}
		logger.info("Sys call file: " + sysCall.getAbsolutePath());*/
	}
	
	public static void zipMethods(String appName, List<CallNode> calls) {
		try {
			File outputFile = new File("vecs/" + appName + ".zip");
			
			//ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile));
			
			TypeToken<CallNode> token = new TypeToken<CallNode>(){};
			for (CallNode call: calls) {
				String methodKey = call.key;
				String shortKey = genShortKey(methodKey);
				ZipEntry entry = new ZipEntry(shortKey);
				zos.putNextEntry(entry);
				String jsonString = convertTypeToJsonString(call, token);
				zos.write(jsonString.getBytes());
				zos.closeEntry();
			}
			
			zos.close();
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static HashSet<String> readPatches(File pathFile) {
		try {
			HashSet<String> ret = new HashSet<String>();
			BufferedReader br = new BufferedReader(new FileReader(pathFile));
			String buf = null;
			while ((buf = br.readLine()) != null) {
				ret.add(buf);
			}
			
			return ret;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	public static <T> T readGsonGeneric(File f, TypeToken<T> token) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		Gson gson = gb.create();
		
		try {
			JsonReader jr = new JsonReader(new FileReader(f));
			T ret = gson.fromJson(jr, token.getType());
			jr.close();
			return ret;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return null;
	}
	
	public static <T> void writeGsonGeneric(T obj, TypeToken<T> token, String filePath) {
		/*GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		Gson gson = gb.enableComplexMapKeySerialization().create();
		String toWrite = gson.toJson(obj, token.getType());*/
		String toWrite = convertTypeToJsonString(obj, token);
		
		try {
			File f = new File(filePath);
			
			if (!f.exists()) {
				f.createNewFile();
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			bw.write(toWrite);
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static <T> String convertTypeToJsonString(T obj, TypeToken<T> token) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		Gson gson = gb.enableComplexMapKeySerialization().create();
		return gson.toJson(obj, token.getType());
	}
	
	public static <T> T convertJsonStringToType(String jsonString, TypeToken<T> token) {
		GsonBuilder gb = new GsonBuilder();
		gb.setPrettyPrinting();
		Gson gson = gb.create();
		
		try {
			T obj = gson.fromJson(jsonString, token.getType());
			return obj;
		} catch (Exception ex) {
			System.out.println(jsonString);
			logger.error("Error: ", ex);
		}
		return null;
	}
		
	public static String cleanType(String typeString) {
		//return typeString.replace("/", ClassUtils.RE_SLASH).replace(";", "");
		return typeString.replace("/", RE_SLASH);
	}
	
	public static HashMap<Integer, Double> contractMethodDist(double[] methodDist) {
		HashMap<Integer, Double> ret = new HashMap<Integer, Double>();
		for (int i = 0; i < methodDist.length; i++) {
			double val = methodDist[i];
			BigDecimal bd = new BigDecimal(val);
			bd = bd.setScale(2, RoundingMode.HALF_UP);
			double cleanVal = bd.doubleValue();
			if (cleanVal > 0) {
				ret.put(i, cleanVal);
			}
		}
		return ret;
	}
	
	public static String genShortKey(String methodKey) {
		if (nameMap.containsKey(methodKey)) {
			return nameMap.get(methodKey);
		}
		
		String[] splits = methodKey.split(DELIM);
		String prefix = splits[0] + DELIM + splits[1];
		int newCount = -1;
		if (nameCounter.containsKey(prefix)) {
			newCount = nameCounter.get(prefix) + 1;
		} else {
			newCount = 0;
		}
		nameCounter.put(prefix, newCount);
		
		String shortKey = prefix + DELIM + newCount;
		nameMap.put(methodKey, shortKey);
		return shortKey;
	}
	
	/*public static String genQueryKey(String owner, String name, String desc) {
		Type[] args = Type.getArgumentTypes(desc);
		return genQueryKey(owner, name, args.length);
	}*/
	
	public static int paramNum(String desc) {
		Type[] args = Type.getArgumentTypes(desc);
		return args.length;
	}
	
	public static String genQueryKey(String owner, String name, int paramSize) {
		return owner + DELIM + name + DELIM + paramSize;
	}
	
	public static String parseSingleArg(Type arg) {
		if (arg.getSort() == Type.ARRAY) {
			int dim = arg.getDimensions();
			Type arrType = arg.getElementType();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < dim; i++) {
				sb.append("[");
			}
			String arrString = parseType(arrType);
			arrString = cleanType(arrString);
			sb.append(arrString);
			
			return sb.toString();
		} else {
			String argString = parseType(arg);
			argString = cleanType(argString);
			
			return argString;
		}
	}
		
	public static String genMethodKey(String owner, String name, String desc) {		
		Type[] args = Type.getArgumentTypes(desc);
		Type returnType = Type.getReturnType(desc);
		StringBuilder argBuilder = new StringBuilder();
		for (Type arg: args) {
			String argString = parseSingleArg(arg);
			argBuilder.append(argString + "+");
		}
		
		String argString = null;
		if (argBuilder.length() == 0) {
			argString = "()";
		} else {
			argString = "(" + argBuilder.substring(0, argBuilder.length() - 1) + ")";
		}
		
		//methodKey = className + methodName + args
		String methodKey = owner + DELIM + name + DELIM + argString;
		String returnString = parseSingleArg(returnType);
		return methodKey + returnString;
	}
	
	public static String[] extractPkgName(String methodKey) {
		String[] decomp = methodKey.split(DELIM);
		String className = decomp[0];
		try {
			int lastIdx = className.lastIndexOf(".");
			String pkgName = className.substring(0, lastIdx);
			String clazz = className.substring(lastIdx + 1, className.length());
			String[] ret = {pkgName, clazz};
			return ret;
		} catch (Exception ex) {
			logger.warn("No package name found: " + methodKey);
			//logger.warn("", ex);
			//System.exit(-1);
			return new String[]{"", className};
		}
	}
	
	public static String analyzeJavaLang(String pkgName, String className) {
		if (!className.contains("Exception") 
				&& !className.contains("Error") 
				&& !className.contains("Throwable")) {
			if (className.contains("$")) {
				int lastDol = className.lastIndexOf("$");
				className = className.substring(0, lastDol);
			}
			pkgName = pkgName + RE_SLASH + className;
		}
		
		return pkgName;
	}
	
	public static void test(Object[] os) {
		
	}
	
	public static String parseType(Type t) {
		if (t.getSort() == Type.OBJECT) {
			return t.getInternalName();
		} else {
			return t.toString();
		}
	}
	
	public static String parseLibName(File libFile) {
		String absPath = libFile.getAbsolutePath();
		int lastSlash = absPath.lastIndexOf("/");
		if (lastSlash == -1) {
			return absPath;
		} else {
			return absPath.substring(lastSlash + 1, absPath.length());
		}
	}
	
	public static boolean checkAccess(int access, int mask) {
		return ((access & mask) != 0);
	}
	
	public static boolean checkSysCall(String pkgName) {
		pkgName = pkgName.replace("/", ".");
		if (SYSCALLS.containsKey(pkgName)) {
			return true;
		} else {
			//Check comp
			for (String c: COMPS) {
				if (pkgName.startsWith(c)) {
					return true;
				}
			}
			
			return false;
		}
		//return SYSCALLS.containsKey(pkgName);
	}
	
	public static void collectClassesInJar(File jarFile, List<InputStream> container) {
		try {
			JarFile jarInstance = new JarFile(jarFile);
			Enumeration<JarEntry> entries = jarInstance.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String entryName = entry.getName();
				//System.out.println(entryName);
				
				if (entry.isDirectory()) {
					continue ;
				}
				
				if (entryName.endsWith(".class")) {		
					InputStream entryStream = jarInstance.getInputStream(entry);
					container.add(entryStream);
					
					//logger.info("Retrieve class: " + entry.getName());
					
					/*String className = entryName.replace("/", ".");
					className = className.substring(0, className.lastIndexOf("."));
					Class clazz = loader.loadClass(className);
					System.out.println("Class name: " + clazz.getProtectionDomain().toString());*/
				}
			}
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static void collectAndroidJars(File dir, List<File> recorder) {
		if (dir == null) {
			return ;
		}
		
		if (dir.isDirectory()) {
			for (File f: dir.listFiles()) {
				collectAndroidJars(f, recorder);
			}
		} else {
			if (dir.getName().equals("android.jar")) {
				recorder.add(dir);
			}
		}
	}
	
	public static void main(String[] args) {
		init(null);
		Type t = Type.getType("[Ljava/lang/Object;");
		System.out.println(t.getSort() == Type.ARRAY);
		System.out.println(parseSingleArg(t));
	}
}
