package edu.columbia.cs.psl.macneto.pojo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.InstructionUtils;

public class SCCMethods {
	
	private static final Logger logger = LogManager.getLogger(SCCMethods.class);
	
	private int sccId;
	
	private Set<CallNode> methods;
	
	private Set<String> methodStrings = new HashSet<String>();
		
	private HashMap<String, Double> callees = new HashMap<String, Double>();
	
	private HashMap<SCCMethods, Double> calleeSccs = new HashMap<SCCMethods, Double>();
	
	private double[] repDist;
	
	private double centrality;
	
	private int inDegree;
	
	private int outDegree;
	
	public transient boolean summarized = false;
	
	public SCCMethods(Set<CallNode> methods, int sccId) {
		this.methods = methods;
		this.sccId = sccId;
		this.methods.forEach(m->{this.methodStrings.add(m.key);});
		
		if (methods.size() == 1) {
			CallNode method = methods.iterator().next();
			method.sccId = sccId;
			this.callees.putAll(method.getCallees());
			
			if (method.getMachineDist() == null) {
				//System.out.println("ERROR, empty machine dist: " + method.key);
				logger.error("ERROR, empty machine dist: " + method.key);
				System.out.println("Callees: " + method.getCallees());
				System.out.println("Syscalls: " + method.getSysCalls());
				System.exit(-1);
			}
			
			this.repDist = Arrays.copyOf(method.getMachineDist(), method.getMachineDist().length);
		} else {
			this.repDist = new double[InstructionUtils.machineWordsLength];
			int methodNum = this.methods.size();
			methods.forEach(m->{
				m.sccId = sccId;
				
				m.getCallees().forEach((c, w)->{
					if (this.callees.containsKey(c)) {
						double newWeight = this.callees.get(c) + w;
						this.callees.put(c, newWeight);
					} else {
						this.callees.put(c, w);
					}
				});
				
				double[] machineDist = m.getMachineDist();
				for (int i = 0; i < this.repDist.length; i++) {
					this.repDist[i] += machineDist[i];
				}
				
				for (int i = 0; i < this.repDist.length; i++) {
					this.repDist[i] /= methodNum;
				}
			});
		}
	}
		
	public Set<CallNode> getMethodsInSCC() {
		return this.methods;
	}
	
	public Set<String> getMethodStrings() {
		return this.methodStrings;
	}
	
	public HashMap<String, Double> getCallees() {
		return this.callees;
	}
	
	public double[] getRepDist() {
		return this.repDist;
	}
	
	public void setCentrality(double centrality) {
		this.centrality = centrality;
	}
	
	public double getCentrality() {
		return this.centrality;
	}
	
	public void setInDegree(int inDegree) {
		this.inDegree = inDegree;
	}
	
	public int getInDegree() {
		return this.inDegree;
	}
	
	public void setOutDegree(int outDegree) {
		this.outDegree = outDegree;
	}
	
	public int getOutDegree() {
		return this.outDegree;
	}
	
	public void addCalleeScc(SCCMethods calleeScc, double weight) {
		if (this.calleeSccs.containsKey(calleeScc)) {
			double newWeight = this.calleeSccs.get(calleeScc) + weight;
			this.calleeSccs.put(calleeScc, newWeight);
		} else {
			this.calleeSccs.put(calleeScc, weight);
		}
	}
	
	public HashMap<SCCMethods, Double> getCalleeScc() {
		return this.calleeSccs;
	}
		
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SCCMethods)) {
			return false;
		}
		
		SCCMethods tmp = (SCCMethods)o;
		return this.sccId == tmp.sccId;
	}
	
	@Override
	public int hashCode() {
		return this.sccId;
	}
	
	@Override
	public String toString() {
		return (new TreeSet<String>(this.methodStrings)).toString();
	}

}
