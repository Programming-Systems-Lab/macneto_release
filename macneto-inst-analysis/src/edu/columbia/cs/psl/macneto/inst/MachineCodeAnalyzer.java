package edu.columbia.cs.psl.macneto.inst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.ContextManager;
import edu.columbia.cs.psl.macneto.utils.InstructionUtils;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

public class MachineCodeAnalyzer extends MethodVisitor {
	
	private static final Logger logger = LogManager.getLogger(MachineCodeAnalyzer.class);
		
	public static int UPWARD = 0;
	
	public static int DOWNWARD = 1;
	
	public static int BIDIR = 2;
	
	private String owner;
	
	private String myName;
	
	private String myDesc;
	
	private String signature;
	
	private String[] exceptions;
	
	private int access;
	
	//private int modifier;
	
	//private boolean isFinal;
	
	//private boolean isAbstract;
	
	protected String methodKey;
	
	//private String returnKey;
	
	protected double[] rawDist = new double[InstructionUtils.machineWordsLength];
	
	protected ArrayList<Integer> seq = new ArrayList<Integer>();
				
	public MachineCodeAnalyzer(MethodVisitor mv, 
			String owner,
			String myName, 
			String myDesc, 
			String signature, 
			String[] exceptions, 
			int access) {
		super(Opcodes.ASM5, mv);
		this.owner = owner;
		this.myName = myName;
		this.myDesc = myDesc;
		this.signature = signature;
		this.exceptions = exceptions;
		//String[] methodKeys = LightweightUtils.genMethodKey(this.owner, this.myName, this.myDesc);
		//this.methodKey = methodKeys[0];
		//this.returnKey = methodKeys[1];
		this.methodKey = LightweightUtils.genMethodKey(this.owner, this.myName, this.myDesc);
		this.access = access;
	}
		
	@Override
	public void visitInsn(int opcode) {
		this.mv.visitInsn(opcode);
		
		int idx = InstructionUtils.lookupInstIdx(opcode);
		if (idx >= 0) {
			this.rawDist[idx]++;
			this.seq.add(idx);
		}
	}
			
	/*@Override
	public void visitMethodInsn(int opcode, 
			String owner, 
			String name, 
			String desc, 
			boolean itf) {
		this.mv.visitMethodInsn(opcode, owner, name, desc, itf);
		
		//Ignore the call to constructor
		if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
			return ;
		}
	}*/
	
	@Override
	public void visitJumpInsn(int opcode, Label label) {
		this.mv.visitJumpInsn(opcode, label);
		
		int idx = InstructionUtils.lookupInstIdx(opcode);
		if (idx >= 0) {
			this.rawDist[idx]++;
			this.seq.add(idx);
		}
	}
	
	@Override
	public void visitIincInsn(int var, int increment) {
		this.mv.visitIincInsn(var, increment);
		
		int idx = InstructionUtils.lookupInstIdx(Opcodes.IINC);
		this.rawDist[idx]++;
		this.seq.add(idx);
	}
	
	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label...labels) {
		this.mv.visitTableSwitchInsn(min, max, dflt, labels);
		
		int idx = InstructionUtils.lookupInstIdx(Opcodes.TABLESWITCH);
		this.rawDist[idx]++;
		this.seq.add(idx);
	}
	
	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		this.mv.visitLookupSwitchInsn(dflt, keys, labels);
		
		int idx = InstructionUtils.lookupInstIdx(Opcodes.LOOKUPSWITCH);
		this.rawDist[idx]++;
		this.seq.add(idx);
	}
	
	@Override
	public void visitEnd() {		
		//Handle calls here
		CallNode curNode = ContextManager.lookupCalls(this.methodKey);
		if (curNode != null) {
			//Some calls in the lib might not be used...
			//logger.error("Cannot find relevant caller: " + this.methodKey);
			
			//Process the system call here
			HashMap<String, Double> sysCalls = curNode.getSysCalls();
			for (String sysCall: sysCalls.keySet()) {
				String[] parsed = LightweightUtils.extractPkgName(sysCall);
				String pkgName = parsed[0];
				String clazz = parsed[1];
				
				if (pkgName.equals("java.lang")) {
					pkgName = LightweightUtils.analyzeJavaLang(pkgName, clazz);
				}
				
				int apiIdx = InstructionUtils.lookupApiIdx(pkgName);
				if (apiIdx >= 0) {
					this.rawDist[apiIdx]++;
				} else {
					logger.warn("Cannot find sys call: " + sysCall);
					ContextManager.registerGiveupThread();
					//System.exit(-1);
				}
			}
			
			curNode.setMachineDist(this.rawDist);
		}
		
		this.mv.visitEnd();
	}
}