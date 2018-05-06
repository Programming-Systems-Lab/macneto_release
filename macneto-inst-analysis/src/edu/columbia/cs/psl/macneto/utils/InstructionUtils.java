package edu.columbia.cs.psl.macneto.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.inst.AndroidProfiler;
import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;

public class InstructionUtils {
	
	private static final Logger logger = LogManager.getLogger(InstructionUtils.class);
	
	//pos: opcode, val: its idx
	public static int[] instLookup = new int[256];
	
	//key: api, val: idx
	public static HashMap<String, Integer> apiLookup = new HashMap<String, Integer>();
	
	//key: idx, val: machine word
	public static HashMap<Integer, String> revLookup = new HashMap<Integer, String>();
	
	public static HashSet<String> compLookup = new HashSet<String>();
	
	//public static HashSet<Integer> arrayWords = new HashSet<Integer>();
	
	//public static HashSet<Integer> opWords = new HashSet<Integer>();
	
	//public static HashSet<Integer> compWords = new HashSet<Integer>();
	
	//public static HashSet<Integer> branchWords = new HashSet<Integer>();
	
	public static int machineWordsLength;
	
	static {
		for (int i = 0; i < instLookup.length; i++) {
			instLookup[i] = -1;
		}
		
		File instModel = new File(AndroidProfiler.ANDROID_MODEL_PATH + "/inst.csv");
		if (!instModel.exists()) {
			logger.warn("Invalid inst model: " + instModel.getAbsolutePath());
		}
		
		int maxInstId = -1;
		try {
			BufferedReader br = new BufferedReader(new FileReader(instModel));
			//Header
			br.readLine();
			String buf = null;
			while ((buf = br.readLine()) != null) {
				String[] info = buf.split(",");
				int op = Integer.valueOf(info[0]);
				String inst = info[1];
				int id = Integer.valueOf(info[2]);
				String cat = info[3];
				
				instLookup[op] = id;
				revLookup.put(id, cat);
				
				if (id > maxInstId) {
					maxInstId = id;
				}
				
				/*if (cat.equals("ARR")) {
					arrayWords.add(op);
				} else if (cat.equals("OP")) {
					opWords.add(op);
				} else if (cat.equals("COMP")) {
					compWords.add(op);
				} else if (cat.equals("BR")) {
					branchWords.add(op);
				} else {
					logger.error("Suspicious cat: " + cat);
				}*/
			}
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		logger.info("# inst: " + (maxInstId + 1));
		//logger.info("Lookup size: " + revLookup.size());
		
		//api starts from 0
		maxInstId++;
		File apiModel = new File(AndroidProfiler.ANDROID_MODEL_PATH + "/android.json");
		int maxIdx = maxInstId;
		if (apiModel.exists()) {
			//Load api map
			TypeToken<HashMap<String, Integer>> token = new TypeToken<HashMap<String, Integer>>(){};
			apiLookup = LightweightUtils.readGsonGeneric(apiModel, token);
			
			for (String api: apiLookup.keySet()) {
				int ori = apiLookup.get(api);
				int newIdx = ori + maxInstId;
				apiLookup.put(api, newIdx);
				revLookup.put(newIdx, api);
				
				if (newIdx > maxIdx) {
					maxIdx = newIdx;
				}
			}
		} else {
			logger.warn("Invalid api model: " + apiModel.getAbsolutePath());
		}
		logger.info("# android api: " + apiLookup.size());
		//logger.info("Lookup size: " + revLookup.size());
		
		File compApis = new File(AndroidProfiler.ANDROID_MODEL_PATH + "/comp.txt");
		if (compApis.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(compApis));
				String buf = null;
				
				maxIdx++;
				while ((buf = br.readLine()) != null) {
					int newIdx = maxIdx++;
					apiLookup.put(buf, newIdx);
					revLookup.put(newIdx, buf);
					compLookup.add(buf);
				}
				logger.info("# compensate api: " + compLookup.size());
			} catch (Exception ex) {
				logger.error("Error: ", ex);
			}
		} else {
			logger.warn("Invalid comp api: " + compApis.getAbsolutePath());
		}
		logger.info("# android api with comp api: " + apiLookup.size());
		//logger.info("Lookup size: " + revLookup.size());
		
		if (revLookup.size() != maxInstId + apiLookup.size()) {
			logger.error("Invalid machine word initialization");
			logger.error("Total machine word: " + revLookup.size());
			logger.error("# inst: " + maxInstId);
			logger.error("# api: " + apiLookup.size());
			System.exit(-1);
		}
		
		machineWordsLength = revLookup.size();
		logger.info("# machine word: " + machineWordsLength);
	}
	
	public static int lookupInstIdx(int opcode) {
		return instLookup[opcode];
	}
	
	public static int lookupApiIdx(String api) {
		if (apiLookup.containsKey(api)) {
			return apiLookup.get(api);
		} else {
			for (String c: compLookup) {
				if (api.startsWith(c)) {
					return apiLookup.get(c);
				}
			}
			return -1;
		}
	}
	
	public static void main(String[] args) {
		System.out.println("Machie word length: " + machineWordsLength);
		TypeToken<HashMap<Integer,String>> token = new TypeToken<HashMap<Integer, String>>(){};
		String filePath = "api_model/machine_word.json";
		LightweightUtils.writeGsonGeneric(revLookup, token, filePath);
	}
}
