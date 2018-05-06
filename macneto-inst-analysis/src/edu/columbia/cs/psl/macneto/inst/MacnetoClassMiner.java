package edu.columbia.cs.psl.macneto.inst;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.macneto.utils.ContextManager;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;

public class MacnetoClassMiner extends ClassVisitor {
	
	private static final Logger logger = LogManager.getLogger(MacnetoClassMiner.class);
	
	private String owner;
	
	private String superName;
	
	private boolean isInterface = false;
		
	private boolean shouldInstrument = true;
	
	public MacnetoClassMiner(ClassVisitor cv) {
		super(Opcodes.ASM5, cv);
	}
	
	@Override
	public void visit(int version, 
			int access, 
			String name, 
			String signature, 
			String superName, 
			String[] interfaces) {
		this.cv.visit(version, access, name, signature, superName, interfaces);
		
		this.owner = LightweightUtils.cleanType(name);
		this.superName = LightweightUtils.cleanType(superName);
		//this.owner = name;
		//this.superName = superName;
		this.isInterface = LightweightUtils.checkAccess(access, Opcodes.ACC_INTERFACE);
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = this.cv.visitMethod(access, name, desc, signature, exceptions);
		
		if (!this.isInterface) {
			boolean isAbstract = LightweightUtils.checkAccess(access, Opcodes.ACC_ABSTRACT);
			boolean isSynthetic = LightweightUtils.checkAccess(access, Opcodes.ACC_SYNTHETIC);
			
			if (isSynthetic) {
				String methodKey = LightweightUtils.genMethodKey(this.owner, name, desc);
				ContextManager.registerSynthetic(methodKey);
			}
			
			if (!isAbstract) {				
				/*return new MachineCodeAnalyzer(mv, 
						this.owner, 
						name, 
						desc, 
						signature, 
						exceptions, 
						access);*/
				
				return new SeqOpAnalyzer(mv, 
						this.owner, 
						name, 
						desc, 
						signature, 
						exceptions, 
						access);
			} else {
				return mv;
			}
			//return new MethodDistributioner(mv, isAbstract, this.classInfo, name, desc, signature, exceptions);
		} else {
			return mv;
		}
	}
	
	/*@Override
	public void visitEnd() {
		this.classInfo.bridges.forEach(bInfo->{
			if (GlobalRecorder.getNonNativeMethod(bInfo.get_id()) == null) {
				int methodId = GlobalRecorder.retrieveMethodId();
				bInfo.setId(methodId);
				GlobalRecorder.registerMethod(bInfo);
				this.classInfo.registerClassifiedMethod(bInfo.getModifier(), bInfo.get_id());
			}
		});
	}*/
}
