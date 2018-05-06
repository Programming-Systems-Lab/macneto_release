package edu.columbia.cs.psl.macneto.inst;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

public class AndroidProfiler {
	
	public static final String ANDROID_MODEL_PATH = "./api_model";
	
	private static final Logger logger = LogManager.getLogger(AndroidProfiler.class);
	
	private static Options options = new Options();
	
	static {
		options.addOption("a", true, "Android platform location");
		options.getOption("a").setRequired(true);
	}
	
	public static void main(String[] args) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine commands = parser.parse(options, args);
		String androidPath = commands.getOptionValue("a");
		
		File outputDir = new File(ANDROID_MODEL_PATH);
		if (!outputDir.exists()) {
			outputDir.mkdir();
		}
		
		//String androidPath = "/Users/mikefhsu/android-sdks/platforms/android-23/android.jar";
		
		File androidPlatform = new File(androidPath);
		if (!androidPlatform.exists()) {
			logger.error("Invalid android platform path: " + androidPlatform.getAbsolutePath());
			System.exit(-1);
		}
		
		List<File> androidJars = new ArrayList<File>();
		LightweightUtils.collectAndroidJars(androidPlatform, androidJars);
		if (androidJars.size() == 0) {
			logger.info("Find no android jar to analyze");
			System.exit(0);
		}
		logger.info("Total android jars for analysis: " + androidJars.size());
		
		try {			
			List<InputStream> classStreamContainer = new ArrayList<InputStream>();
			for (File androidJar: androidJars)
				LightweightUtils.collectClassesInJar(androidJar, classStreamContainer);
			
			logger.info("Total class: " + classStreamContainer.size());
			
			final TreeSet<String> androidPkgs = new TreeSet<String>();
			classStreamContainer.forEach(clazz->{
				try {
					ClassReader cr = new ClassReader(clazz);
					ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
					ClassVisitor analyzer = new ClassVisitor(Opcodes.ASM5, cw) {
						
						@Override
						public void visit(int version, 
								int access, 
								String name, 
								String signature, 
								String superName, 
								String[] interfaces) {
							this.cv.visit(version, access, name, signature, superName, interfaces);
							boolean isInterface = LightweightUtils.checkAccess(access, Opcodes.ACC_INTERFACE);
							
							if (!isInterface && !name.startsWith("junit")) {
								int lastIdx = name.lastIndexOf("/");
								String pkgName = name.substring(0, lastIdx);
								String className = name.substring(lastIdx + 1, name.length());
								pkgName = LightweightUtils.cleanType(pkgName);
								if (pkgName.equals("java.lang")) {
									pkgName = LightweightUtils.analyzeJavaLang(pkgName, className);
								}
								androidPkgs.add(pkgName);
							}
							
						}
					};
					cr.accept(analyzer, ClassReader.EXPAND_FRAMES);
				} catch (Exception ex) {
					logger.error("Error: ", ex);
				}
			});
			
			logger.info("Valid pkgs: " + androidPkgs.size());
			HashMap<String, Integer> androidLookup = new HashMap<String, Integer>();
			int idx = 0;
			for (String p: androidPkgs) {
				androidLookup.put(p, idx++);
			}
			
			TypeToken<HashMap<String, Integer>> token = new TypeToken<HashMap<String, Integer>>(){};
			String outputPath = ANDROID_MODEL_PATH + "/android.json";
			LightweightUtils.writeGsonGeneric(androidLookup, token, outputPath);
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}

}
