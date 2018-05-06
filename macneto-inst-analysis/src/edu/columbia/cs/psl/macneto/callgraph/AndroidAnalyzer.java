package edu.columbia.cs.psl.macneto.callgraph;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;*/

import edu.columbia.cs.psl.macneto.db.DocManager;
import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

import com.google.gson.reflect.TypeToken;
/*import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.dalvik.test.callGraph.DalvikCallGraphTestBase;
import com.ibm.wala.dalvik.test.util.Util;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.IClassHierarchy;*/

public class AndroidAnalyzer {
		
	public static final String OBJECT_CLASS = "java.lang.Object";
	
	public static final String WALA_ROOT = "com.ibm.wala.FakeRootClass";
	
	public static Options options = new Options();
	
	static {
		options.addOption("i", true, "jar/apk location");
		options.getOption("i").setRequired(true);
		
		options.addOption("o", true, "json location");
		options.getOption("o").setRequired(true);
		
		options.addOption("a", true, "android location");
		options.getOption("a").setRequired(true);
		
		options.addOption("apk", false, "apk/jar");
		options.getOption("apk").setRequired(true);
		
		options.addOption("p", true, "android profile location");
	}
	
	private static final Logger logger = LogManager.getLogger(AndroidAnalyzer.class);
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		CommandLine command = parser.parse(options, args);
		String appLoc = command.getOptionValue("i");
		String outputLoc = command.getOptionValue("o");
		String androidLoc = command.getOptionValue("a");
		boolean isApk = false;
		String androidProfileLoc = command.getOptionValue("p");
		
		File apkDir = new File(appLoc);
		if (!apkDir.exists()) {
			logger.error("Invalid app loc: " + apkDir.getAbsolutePath());
			System.exit(-1);
		}
		
		File androidDir = new File(androidLoc);
		if (!androidDir.exists()) {
			logger.error("Invalid android loc: " + androidDir.getAbsolutePath());
			System.exit(-1);
		}
		
		File outputDir = new File(outputLoc);
		if (!outputDir.exists()) {
			boolean create = outputDir.mkdir();
			if (!create) {
				logger.error("Fail to create output dir: " + outputDir.getAbsolutePath());
				System.exit(-1);
			}
		}
		
		if (command.hasOption("apk")) {
			isApk = true;
		}
		logger.info("Is apk: " + isApk);
		
		if (androidProfileLoc != null) {
			logger.info("Android profile location: " + androidProfileLoc);
		}
		LightweightUtils.init(androidProfileLoc);
		
		logger.info("Android location: " + androidLoc);
		logger.info("App/apk location: " + appLoc);
		logger.info("Output location: " + outputLoc);
		
		HashSet<File> apks = new HashSet<File>();
		if (isApk)
			collectApks(apkDir, apks, ".apk");
		else
			collectApks(apkDir, apks, ".jar");
		logger.info("Total apks: " + apks.size());
		
		int succ = 0;
		for (File apk: apks) {
			String fullName = apk.getAbsolutePath();
			String apkName = extractApkName(fullName);
			CallLookup calls = FlowDroidDriver.buildCallGraph(androidLoc, fullName, apkName, isApk);
			
			if (calls == null) {
				logger.warn("2nd attempts for: " + apkName);
				calls = FlowDroidDriver.buildCallGraph(androidLoc, fullName, apkName, isApk);
			}
			
			if (calls != null) {
				String graphPath = outputDir.getAbsolutePath() + "/" + apkName + ".json";
				logger.info("Output path: " + graphPath);
				TypeToken<CallLookup> token = new TypeToken<CallLookup>(){};
				LightweightUtils.writeGsonGeneric(calls, token, graphPath);
				
				DocManager.insertApp(apkName);
				succ++;
			}
		}
		
		logger.info("Successful callgraphs: " + succ);
	}
	
	public static String extractApkName(String fullName) {
		int idx = fullName.lastIndexOf("/");
		String apkName = fullName.substring(idx + 1, fullName.length() - 4);
		return apkName;
	}
	
	public static boolean isJunit(String className) {
		return className.startsWith("junit") || className.startsWith("org.junit");
	}
	
	public static void collectApks(File apkLoc, HashSet<File> recorder, String ext) {
		if (apkLoc == null) {
			return ;
		}
		
		if (apkLoc.isDirectory()) {
			for (File f: apkLoc.listFiles()) {
				collectApks(f, recorder, ext);
			}
		} else {
			if (apkLoc.getName().endsWith(ext)) {
				recorder.add(apkLoc);
			}
		}
	}
}
