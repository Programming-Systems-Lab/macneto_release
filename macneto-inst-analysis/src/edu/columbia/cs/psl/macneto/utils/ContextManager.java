package edu.columbia.cs.psl.macneto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import edu.columbia.cs.psl.macneto.pojo.CallLookup;
import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;

public class ContextManager {
	
	private static final AtomicInteger nextId = new AtomicInteger(0);
	
	private static final ThreadLocal<Integer> threadId = new ThreadLocal<Integer>() {
		@Override
		public Integer initialValue() {
			return nextId.getAndIncrement();
		}
	};
	
	//public static CallLookup callLookup;
	
	private static HashMap<Integer, CallLookup> callLookups = new HashMap<Integer, CallLookup>();
	
	//public static HashSet<String> synthetics = new HashSet<String>();
	
	private static HashMap<Integer, HashSet<String>> synthetics = new HashMap<Integer, HashSet<String>>();
	
	private static HashSet<Integer> giveUps = new HashSet<Integer>();
	
	private static HashMap<Integer, HashSet<String>> injected = null;
	
	public synchronized static void registerCallLookup(CallLookup lookup) {
		int curThread = threadId.get();
		callLookups.put(curThread, lookup);
	}
	
	public static CallLookup getCallLookup() {
		int curThread = threadId.get();
		return callLookups.get(curThread);
	}
	
	public synchronized static CallNode lookupCalls(String methodKey) {
		int curThread = threadId.get();
		CallLookup lookup = callLookups.get(curThread);
		return lookup.queryCall(methodKey);
	}
	
	public synchronized static void registerSynthetic(String methodKey) {
		int curThread = threadId.get();
		if (!synthetics.containsKey(curThread)) {
			HashSet<String> synthetic = new HashSet<String>();
			synthetics.put(curThread, synthetic);
		}
		HashSet<String> synthetic = synthetics.get(curThread);
		synthetic.add(methodKey);
	}
	
	public static HashSet<String> getSynthetic() {
		int curThread = threadId.get();
		return synthetics.get(curThread);
	}
	
	public static boolean isSynthetic(String methodKey) {
		int curThread = threadId.get();
		HashSet<String> synthetic = synthetics.get(curThread);
		return synthetic.contains(methodKey);
	}
	
	public synchronized static void registerGiveupThread() {
		int curThread = threadId.get();
		giveUps.add(curThread);
	}
	
	public static boolean isGiveup() {
		int curThread = threadId.get();
		if (giveUps.contains(curThread)) {
			giveUps.remove(curThread);
			return true;
		} else {
			return false;
		}
	}
	
	public static void registerInjected(HashMap<Integer, HashSet<String>> toRegister) {
		injected = toRegister;
	}
	
	public static boolean isInjected(int apkId, String methodKey) {
		if (injected == null) {
			return false;
		}
		
		if (!injected.containsKey(apkId)) {
			return false;
		}
		
		return injected.get(apkId).contains(methodKey);
	}
}
