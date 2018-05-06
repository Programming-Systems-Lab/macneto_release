package edu.columbia.cs.psl.macneto.callgraph;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Targets;
import soot.options.Options;

public class CGTest {

	public static void main(String[] args) {
		Options.v().set_whole_program(true);
		Options.v().set_soot_classpath("../de.saschahlusiak.freebloks_68_obfus.jar:../android/android--1/android.jar");
		Options.v().set_verbose(false);
		Options.v().set_allow_phantom_refs(true); // Don't force resolve deeply?

		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().setPhaseOption("cg.spark", "rta:true");
		Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
		// Options.v().set_verbose(true);
		Scene.v().loadNecessaryClasses();

		// SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
		// Options.v().set_main_class(entryPoint.getSignature());
		// Scene.v().setEntryPoints(Collections.singletonList(entryPoint));

		Options.v().set_main_class("de.saschahlusiak.freebloks.game.FreebloksActivity");
		final SootClass c = Scene.v().forceResolve("de.saschahlusiak.freebloks.game.FreebloksActivity", SootClass.BODIES);
		for (SootMethod method : c.getMethods()) {
			if (method.getName().equals("onBackPressed"))
				Scene.v().setEntryPoints(Collections.singletonList(method));
		}

		PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTrans", new SceneTransformer() {

			@Override
			protected void internalTransform(String phaseName, Map<String, String> options) {
				CHATransformer.v().transform();
//				HashMap<String,String> opt =  new HashMap<String,String>();
//				SparkTransformer.v().transform("",opt);
				SootMethod src = c.getMethodByName("onBackPressed");
				CallGraph cg = Scene.v().getCallGraph();

				Iterator<MethodOrMethodContext> targets = new Targets(cg.edgesOutOf(src));
				while (targets.hasNext()) {
					SootMethod tgt = (SootMethod) targets.next();
					System.out.println(src + " may call " + tgt);
				}
			}
		}));

		PackManager.v().runPacks();
//		CallGraph appCallGraph = Scene.v().getCallGraph();
//		appCallGraph.forEach(new Consumer<Edge>() {
//			@Override
//			public void accept(Edge t) {
//				if (t.getSrc().method().getName().equals("onBackPressed"))
//					System.out.println(t.getSrc() + " - " + t.getTgt());
//			}
//		});

	}
}
