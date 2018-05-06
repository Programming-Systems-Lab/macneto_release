package edu.columbia.cs.psl.macneto.pojo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

public class CallLookup {
	
	private static final Logger logger = LogManager.getLogger(CallLookup.class);
	
	private String appName;
	
	private HashMap<String, CallNode> calls = new HashMap<String, CallNode>();
		
	public CallLookup(String appName) {
		this.appName = appName;
	}
	
	public String getAppName() {
		return this.appName;
	}
	
	public void registerCall(CallNode caller, String calleeKey, double weight) {
		//String callerKey = genKey(callerClass, callerMethod, callerDesc);		
		caller.registerCallee(calleeKey, weight);
	}
	
	public CallNode queryOrGenCall(String classLoader, String className, String methodName, String methodDesc) {
		String key = LightweightUtils.genMethodKey(className, methodName, methodDesc);
		if (this.calls.containsKey(key)) {
			return this.calls.get(key);
		}
		
		int paramNum = LightweightUtils.paramNum(methodDesc);
		String queryKey = LightweightUtils.genQueryKey(className, methodName, paramNum);
		CallNode call = CallNode.initCallNode(classLoader, key, queryKey);
		this.calls.put(key, call);
		return call;
	}
	
	public CallNode queryCall(String callKey) {
		return this.calls.get(callKey);
	}
	
	public HashMap<String, CallNode> getCalls() {
		return this.calls;
	}
	
	public static class CallNode {
		
		private static final AtomicInteger indexer = new AtomicInteger();
		
		public int sccId = -1;
		
		private int nodeId;
		
		public String classLoader;
		//private boolean sysCall;
		
		//private String className;
		
		//private String methodName;
		
		//private String desc;
		
		public String key;
		
		public String queryKey;
		
		public HashMap<String, Double> callees = new HashMap<String, Double>();
		
		public HashMap<String, Double> sysCalls = new HashMap<String, Double>();
		
		private double[] machineDist;
		
		private ArrayList<Integer> seq;
		
		public transient double centrality;
		
		public transient int inDegree;
		
		public transient int outDegree;
		
		public static CallNode initCallNode(String classLoader, String methodKey, String queryKey) {
			CallNode node = new CallNode();
			node.setClassLoader(classLoader);
			node.setMethodKey(methodKey);
			node.queryKey = queryKey;
			return node;
		}
		
		private CallNode() {
			this.nodeId = indexer.getAndIncrement();
		}
		
		public int getNodeId() {
			return this.nodeId;
		}
		
		public void setMethodKey(String methodKey) {
			this.key = methodKey;
		}
		
		public String getMethodKey() {
			return this.key;
		}
		
		public void setQueryKey(String queryKey) {
			this.queryKey = queryKey;
		}
		
		public String getQueryKey() {
			return this.queryKey;
		}
		
		public void setClassLoader(String classLoader) {
			this.classLoader = classLoader;
		}
		
		public String getClassLoader() {
			return this.classLoader;
		}
		
		public void registerCallee(String calleeKey, double weight) {
			if (this.callees.containsKey(calleeKey)) {
				double newWeight = this.callees.get(calleeKey) + weight;
				this.callees.put(calleeKey, newWeight);
			} else {
				this.callees.put(calleeKey, weight);
			}
		}
		
		public HashMap<String, Double> getCallees() {
			return this.callees;
		}
		
		public void registerSysCall(String sysCall, double weight) {
			if (this.sysCalls.containsKey(sysCall)) {
				double newWeight = this.sysCalls.get(sysCall) + weight;
				this.sysCalls.put(sysCall, newWeight);
			} else {
				this.sysCalls.put(sysCall, weight);
			}
		}
		
		public HashMap<String, Double> getSysCalls() {
			return this.sysCalls;
		}
		
		public void setMachineDist(double[] dist) {
			this.machineDist = dist;
		}
		
		public double[] getMachineDist() {
			return this.machineDist;
		}
		
		public void setSeq(ArrayList<Integer> seq) {
			this.seq = seq;
		}
		
		public ArrayList<Integer> getSeq() {
			return this.seq;
		}
		
		@Override
		public String toString() {
			return this.key;
		}
		
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof CallNode)) {
				return false;
			}
			
			CallNode node = (CallNode)o;
			return this.toString().equals(node.toString());
		}
		
		@Override
		public int hashCode() {
			return this.toString().hashCode();
		}
	}

}
