package edu.columbia.cs.psl.macneto.inst;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.Map.Entry;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.db.DocManager;
import edu.columbia.cs.psl.macneto.inst.MacnetoClassMiner;
import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.pojo.SCCMethods;
import edu.columbia.cs.psl.macneto.utils.ContextManager;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;
import edu.columbia.cs.psl.macneto.utils.TarjanSCC;

public class MachineCodeDriver {
	
	private static final Logger logger = LogManager.getLogger(MachineCodeDriver.class);
	
	private static Options options = new Options();
	
	private static String CALLBASE = "callgraphs";
	
	static {
		options.addOption("c", true, "Codebase location");
		options.getOption("c").setRequired(true);
		
		options.addOption("call", true, "Callgraph base");
		options.addOption("j", true, "Injecte methods");
	}
	
	public static boolean processApk(String codebase, String apkName, int apkId) {
		try {
			//Load call graphs
			//String appName = AndroidAnalyzer.extractApkName(codebase.getAbsolutePath());
			String jarPath = codebase + "/" + apkName + ".jar";
			File apkLoc = new File(jarPath);
			if (!apkLoc.exists()) {
				logger.error("Invalid apk: " + apkLoc.getAbsolutePath());
				return false;
			}
			logger.info("Apk path: " + apkLoc.getAbsolutePath());
			
			TypeToken<CallLookup> token = new TypeToken<CallLookup>(){};
			File callFile = new File(CALLBASE + "/" + apkName + ".json");
			
			if (!callFile.exists()) {
				logger.warn("Cannot load call graph: " + callFile.getAbsolutePath());
				return false;
			}
			
			CallLookup calls = LightweightUtils.readGsonGeneric(callFile, token);
			logger.info("Loading app call graph: " + calls.getCalls().size());
			//ContextManager.callLookup = calls;
			ContextManager.registerCallLookup(calls);
			
			List<InputStream> classStreamContainer = new ArrayList<InputStream>();
			LightweightUtils.collectClassesInJar(apkLoc, classStreamContainer);
			logger.info("Total class: " + classStreamContainer.size());
			
			classStreamContainer.forEach(clazz->{
				try {
					ClassReader cr = new ClassReader(clazz);
					ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
					MacnetoClassMiner staticAnalyzer = new MacnetoClassMiner(cw);
					cr.accept(staticAnalyzer, ClassReader.EXPAND_FRAMES);
				} catch (Exception ex) {
					logger.error("Error: ", ex);
				}
			});
			
			if (ContextManager.isGiveup()) {
				logger.warn("Giveup apk: " + apkLoc.getAbsolutePath());
				DocManager.removeApp(apkName);
				return false;
			}
			
			//Detect cycles in the call graph
			CallLookup callLookup = ContextManager.getCallLookup();
			logger.info("Total method: " + callLookup.getCalls().size());
			for (CallNode node: callLookup.getCalls().values()) {
				for (String k: node.getCallees().keySet()) {
					if (callLookup.queryCall(k) == null 
							|| callLookup.queryCall(k).getMachineDist() == null) {
						logger.error("Unable to process apk: " + apkName);
						logger.error("Unable to locate callee for caller: " + node.getMethodKey());
						logger.error("Suspicious callee: " + k);
						DocManager.removeApp(apkName);
						return false;
					}
				}
			}
			
			/*TarjanSCC sccAnalyzer = new TarjanSCC(callLookup.getCalls());
			List<SCCMethods> sccMethods = sccAnalyzer.analyzeSCC(apkId);
			logger.info("Total method scc: " + sccMethods.size());
			
			//Fix the instruction distribution
			walkSCCGraph(sccMethods);
			ArrayList<CallNode> cleanMethods = cleanMethods(sccMethods);
			
			DocManager.insertMethodVecs(apkId, cleanMethods);*/
			DocManager.insertMethodVecs(apkId, callLookup.getCalls().values());
			return true;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		return false;
	}
	
	public static void main(String[] args) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine command = parser.parse(options, args);
		
		String localCodeBase = command.getOptionValue("c");		
		File codebase = new File(localCodeBase);
		if (!codebase.exists()) {
			logger.error("Invalid codebase");
			System.exit(-1);
		}
		String codebasePath = codebase.getAbsolutePath();
		logger.info("Confirm codebase: " + codebasePath);
		
		HashMap<Integer, HashSet<String>> injects = null;
		if (command.hasOption("j")) {
			String diffPath = command.getOptionValue("j");
			File diffFile = new File(diffPath);
			if (!diffFile.exists()) {
				logger.warn("No diff db: " + diffFile.getAbsolutePath());
			} else {
				logger.info("Confirm diff db: " + diffFile.getAbsolutePath());
				injects = DocManager.getInjects(diffFile.getAbsolutePath());
			}
		}
		ContextManager.registerInjected(injects);
		
		if (command.hasOption("call")) {
			CALLBASE = command.getOptionValue("call");
			logger.info("Using customized callbase: " + CALLBASE);
		}
		
		LightweightUtils.init(null);
		//Get all valid apks (having call graphs from db)
		HashMap<String, Integer> apps = DocManager.getAllApks();
		logger.info("Total apks to process: " + apps.size());
		
		int cpuNum = Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool(cpuNum);
		List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
		for (Entry<String, Integer> e: apps.entrySet()) {
			String name = e.getKey();
			int id = e.getValue();
			
			Callable<Boolean> worker = new Callable<Boolean>() {

				@Override
				public Boolean call() throws Exception {
					boolean result = processApk(codebasePath, name, id);
					return result;
				}
				
			};
			
			Future<Boolean> result = es.submit(worker);
			results.add(result);
		}
		
		try {
			int counter = 0;
			for (Future<Boolean> r: results) {
				boolean result = r.get();
				
				if (result) {
					counter++;
				}
			}
			logger.info("Total successful apks: " + counter);
			
			es.shutdown();
			es.awaitTermination(60, TimeUnit.SECONDS);
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		} finally {
			if (!es.isTerminated()) {
				logger.warn("Initiate immediate shutdown process");
			}
			es.shutdownNow();
		}		
	}
	
	public static ArrayList<CallNode> cleanMethods(List<SCCMethods> sccMethods) {
		ArrayList<CallNode> ret = new ArrayList<CallNode>();
		sccMethods.forEach(sccMethod->{
			double[] repDist = sccMethod.getRepDist();
			sccMethod.getMethodsInSCC().forEach(callNode->{
				//Each method in scc share the same machine dist
				//Nullify callee info, don't need it anymore
				callNode.setMachineDist(repDist);
				callNode.centrality = sccMethod.getCentrality();
				callNode.inDegree = sccMethod.getInDegree();
				callNode.outDegree = sccMethod.getOutDegree();
				callNode.callees = null;
				ret.add(callNode);
			});
		});
		
		return ret;
	}
	
	public static void walkSCCGraph(List<SCCMethods> sccMethods) {
		if (sccMethods == null || sccMethods.size() == 0) {
			return ;
		}
		
		for (SCCMethods sccMethod: sccMethods) {
			_summarizeSCCMethod(sccMethod);
		}
	}
	
	/**
	 * Need to synchronized with GraphDiffer
	 * @param vecMap
	 * @return
	 */
	public static String convertToSig(HashMap<Integer, Double> vecMap) {
		if (vecMap.size() == 0) {
			return null;
		}
		
		TreeSet<Integer> sorted = new TreeSet<Integer>(vecMap.keySet());
		StringBuilder sig = new StringBuilder();
		for (Integer s: sorted) {
			double freq = vecMap.get(s);
			int round = (int)Math.round(freq);
			sig.append(s).append(":").append(round).append(",");
		}
		
		return sig.substring(0, sig.length() - 1);
	}
	
	private static void _summarizeSCCMethod(SCCMethods sccMethod) {
		double[] curDist = sccMethod.getRepDist();
		HashMap<SCCMethods, Double> calleeSccs = sccMethod.getCalleeScc();
		calleeSccs.forEach((callee, weight)->{
			if (!callee.summarized) {
				System.out.println("Analyze caller: " + sccMethod.getMethodStrings());
				System.out.println("Summarized callee: " + callee.getMethodStrings());
				_summarizeSCCMethod(callee);
			}
			
			double[] calleeRepo = callee.getRepDist();
			for (int i = 0; i < calleeRepo.length; i++) {
				curDist[i] += calleeRepo[i] * weight;
			}
		});
		sccMethod.summarized = true;
	}
}
