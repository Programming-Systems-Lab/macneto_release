package edu.columbia.cs.psl.macneto.inst;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.MethodVisitor;

import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.ContextManager;
import edu.columbia.cs.psl.macneto.utils.InstructionUtils;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

public class SeqOpAnalyzer extends MachineCodeAnalyzer {
	
	private static final Logger logger = LogManager.getLogger(SeqOpAnalyzer.class);
	
	private CallNode curNode = null;

	public SeqOpAnalyzer(MethodVisitor mv, String owner, String myName, String myDesc, String signature,
			String[] exceptions, int access) {
		super(mv, owner, myName, myDesc, signature, exceptions, access);
		this.curNode = ContextManager.lookupCalls(this.methodKey);
	}
	
	@Override
	public void visitMethodInsn(int opcode,
            String owner,
            String name,
            String desc,
            boolean itf) {
		owner = owner.replace("/", ".");
		String calleeKey = LightweightUtils.genMethodKey(owner, name, desc);
		if (this.curNode != null && this.curNode.sysCalls.containsKey(calleeKey)) {
			String[] pkgInfo = LightweightUtils.extractPkgName(calleeKey);
			String pkgName = pkgInfo[0];
			String clazz = pkgInfo[1];
			
			int apiIdx = InstructionUtils.lookupApiIdx(pkgName);
			//logger.info("Captuer sys call: " + calleeKey);
			if (apiIdx >= 0) {
				this.rawDist[apiIdx]++;
				this.seq.add(apiIdx);
			} else {
				logger.warn("Cannot find sys call: " + calleeKey);
				ContextManager.registerGiveupThread();
				//System.exit(-1);
			}
		}
	}
	
	@Override
	public void visitEnd() {
		if (this.curNode != null) {
			this.curNode.setMachineDist(this.rawDist);
			this.curNode.setSeq(this.seq);
		}
		this.mv.visitEnd();
	}

}
