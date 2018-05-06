package edu.columbia.cs.psl.macneto.callgraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xmlpull.v1.XmlPullParserException;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.InstructionUtils;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;
import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Targets;
import soot.options.Options;
import soot.util.Chain;
import soot.util.queue.QueueReader;

public class FlowDroidDriver {
	
	public static final Logger logger = LogManager.getLogger(FlowDroidDriver.class);
	
	public static String androidPlatformPath = "/Users/mikefhsu/android-sdks/platforms";
	
	public static String appPath = "/Users/mikefhsu/ncsws/SootTest/a2dp.Vol_134.apk";
	
	public static final String OBJECT_CLASS = "java.lang.Object";
	
	public static HashMap<String, Integer> androidVersionMap = new HashMap<String, Integer>();
	
	public static boolean isJunit(String className) {
		return className.startsWith("junit") || className.startsWith("org.junit");
	}
	
	public static boolean shouldPass(SootClass clazz) {
		String clazzName = clazz.getName();
		if (clazzName.equals("dummyMainClass")) {
    		return true;
    	}
    	
    	if (clazz.isInterface()) {
    		return true;
    	}
    	
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
    	
    	if (isJunit(clazzName)) {
    		return true;
    	}
    	
    	return false;
	}
	
	public static String[] parseBytecodeSig(String bytecodeSig) {
		bytecodeSig = bytecodeSig.substring(1, bytecodeSig.length() - 1);
    	String[] splits = bytecodeSig.split(": ");
    	String className = splits[0];
    	int idx = splits[1].indexOf("(");
    	String methodName = splits[1].substring(0, idx);
    	String descriptor = splits[1].substring(idx , splits[1].length());
    	//String methodKey = LightweightUtils.genMethodKey(className, methodName, descriptor)[0];
    	
    	return new String[]{className, methodName, descriptor};
	}
	
	public static CallLookup buildCallGraph(String androidPlatformPath, 
			String apkPath, 
			String apkName, 
			boolean apkMode) {
		try {
			File platform = new File(androidPlatformPath);
			
			SetupApplication app = new SetupApplication(androidPlatformPath, apkPath);
	        app.calculateSourcesSinksEntrypoints("./SourcesAndSinks.txt");
	        soot.G.reset();
	        
	        /*if (apkMode) {
	        	Options.v().set_src_prec(Options.src_prec_apk);
	        } else {
	        	Options.v().set_src_prec(Options.src_prec_class);
	        }*/
	        //Options.v().set_src_prec(Options.src_prec_class);
	        Options.v().set_src_prec(Options.src_prec_apk);
	        
	        Options.v().set_process_dir(Collections.singletonList(apkPath));
	        Options.v().set_android_jars(androidPlatformPath);
	        Options.v().set_whole_program(true);
	        Options.v().set_allow_phantom_refs(true);
	        Options.v().set_no_bodies_for_excluded(true);
	        Options.v().setPhaseOption("cg.spark", "on");
	        Options.v().setPhaseOption("cg.spark", "rta:true");
	        Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
	        //Options.v().set_force_android_jar(androidPlatformPath + "/android-22/android.jar");
	        //Options.v().set_output_format(Options.output_format_class);
	        //Options.v().set_output_dir("./test_soot_output");
	        
	        //Field apkField = Scene.v().getClass().getDeclaredField("androidAPIVersion");
	        //apkField.setAccessible(true);
	        //apkField.setInt(Scene.v(), 22);
	        //Scene.v().extendSootClassPath(androidPlatformPath + "/android-22/android.jar");
	        Scene.v().loadNecessaryClasses();
	        
	        SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
	        Options.v().set_main_class(entryPoint.getSignature());
	        Scene.v().setEntryPoints(Collections.singletonList(entryPoint));
	        /*Chain<SootClass> aClasses = Scene.v().getApplicationClasses();
	        logger.info("Application classes: " + aClasses.size());
	        aClasses.forEach(a->{
	        	logger.info(a);
	        });*/
	        
	        /*Set<String> bClasses = Scene.v().getBasicClasses();
	        logger.info("Basic classes: " + bClasses.size());
	        bClasses.forEach(b->{
	        	System.out.println(b);
	        });
	        System.out.println("Class path: " + Scene.v().getSootClassPath());*/
	        //logger.info("Show all classes: " + Scene.v().getClasses().size());
	        //logger.info("Show lib classes: " + Scene.v().getLibraryClasses().size());
	        
	        LightweightUtils.init(null);
	        CallLookup lookup = new CallLookup(apkName);
	        
	        PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTrans", new SceneTransformer() {

				@Override
				protected void internalTransform(String phaseName, Map<String, String> options) {
					CHATransformer.v().transform();
					CallGraph appCallGraph = Scene.v().getCallGraph();
					
					logger.info("Call graphs: " + apkName + " " + appCallGraph.size());
					Iterator<Edge> callIT = appCallGraph.iterator();
			        while (callIT.hasNext()) {
			        	Edge e = callIT.next();
			        	SootMethod srcMethod = e.getSrc().method();
			        	SootClass srcClass = srcMethod.getDeclaringClass();
			        	String srcPkgName = srcClass.getPackageName();
			        	
			        	if (shouldPass(srcClass)) {
			        		continue ;
			        	}
			        	
			        	if (LightweightUtils.checkSysCall(srcPkgName)) {
			        		continue ;
			        	}
			        	
			        	String srcSig = srcMethod.getBytecodeSignature();
			        	String[] parsed = parseBytecodeSig(srcSig);
			        	//String className = parsed[0];
			        	//String methodName = parsed[1];
			        	String descriptor = parsed[2];
			        	CallNode caller = lookup.queryOrGenCall("App", srcClass.getName(), srcMethod.getName(), descriptor);
			        	
			        	if (caller.getMethodKey().contains("'")) {
			        		logger.error("Suspicious class: " + caller.getMethodKey());
			        		logger.error("Check class method names: " + srcClass.getName() + " " + srcMethod.getName());
			        		System.exit(-1);
			        	}
			        	
			        	SootMethod destMethod = e.tgt();
			        	SootClass destClass = destMethod.getDeclaringClass();
			        	String destPkg = destClass.getPackageName();
			        	if (destPkg == null) {
			        		destPkg = "";
			        	}
			        	
			        	if (shouldPass(destClass)) {
			        		continue ;
			        	}
			        	
			        	String[] parsedCallee = parseBytecodeSig(destMethod.getBytecodeSignature());
			        	//String calleeClassName = parsedCallee[0];
			        	//String calleeMethodName = parsedCallee[1];
			        	String calleeDescriptor = parsedCallee[2];
			        	String calleeKey = LightweightUtils.genMethodKey(destClass.getName(), destMethod.getName(), calleeDescriptor);
			        	
			        	//Recursive
			        	if (caller.key.equals(calleeKey)) {
			        		continue ;
			        	}
			        	
			        	if (LightweightUtils.checkSysCall(destPkg)) {
							//Well, object apis are usually not useful...
							if (destClass.getName().equals(OBJECT_CLASS)) {
								continue ;
							} else {
								caller.registerSysCall(calleeKey, 1.0);
							}
						} else {
							CallNode callee = lookup.queryOrGenCall("App", destClass.getName(), destMethod.getName(), calleeDescriptor);
							lookup.registerCall(caller, callee.key, 1.0);
						}
			        }
				}
			}));

	        PackManager.v().runPacks();
	        int checkVersion = Scene.v().getAndroidAPIVersion();
	        logger.info("APK Android version: " + checkVersion);
	        
	        return lookup;
		} catch (Exception ex) {
			logger.error("Fail to generate call graph: " + apkPath);
			logger.error("Error: ", ex);
		}
		
		return null;
	}
	
	public static void main(String[] args) throws IOException, XmlPullParserException {
		String androidFramework = args[0];
		String apkPath = args[1];
		//String androidFramework = "/Users/mikefhsu/Library/Android/sdk/platforms";
		//String apkPath = "/Users/mikefhsu/Desktop/macneto_obfus/buf/test2.apk";
		String apkName = "test";
		
		System.out.println("Android framework: " + androidFramework);
		System.out.println("Apk path: " + apkPath);
		boolean apkMode = false;
		if (apkPath.endsWith(".apk")) {
			apkMode = true;
		}
		System.out.println("APK mode: " + apkMode);
		CallLookup calls = FlowDroidDriver.buildCallGraph(androidFramework, apkPath, apkName, apkMode);
		System.out.println("Nodes: " + calls.getCalls().size());
		
		String graphpath = "./test.json";
		System.out.println("Test graph path: " + (new File(graphpath).getAbsolutePath()));
		TypeToken<CallLookup> token = new TypeToken<CallLookup>(){};
		LightweightUtils.writeGsonGeneric(calls, token, graphpath);
	}

}
