package edu.columbia.cs.psl.macneto.callgraph;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.Options;

/*import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.examples.drivers.PDFCallGraph;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;*/

public class RTATester {
	
	public static Options options = new Options();
	
	static {
		options.addOption("i", true, "app/lib location");
		options.addOption("o", true, "extracted json location");
	}
	
	public static void main(String[] args) {
		//String jarFile = "/Users/mikefhsu/Desktop/macneto_data/br.nom.strey.maicon.sorting.jar";
		//String jarFile = "/Users/mikefhsu/Desktop/dex2jar-2.0/test/Conversations-1.14.5-free-debug-dex2jar.jar";
		//buildCG(jarFile);
	}
	
	/*public static void buildCG(String jarFile) {
		try {
			File f = new File(CallGraphTestUtil.REGRESSION_EXCLUSIONS);
			System.out.println("Exlusion file: " + f.getAbsolutePath());
			if (!f.exists()) {
				//System.exit(-1);
				System.out.println("No exclusion file: " + f.getAbsolutePath());
			}
			
			AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(jarFile, f);
			IClassHierarchy cha = ClassHierarchy.make(scope);
			System.out.println("CHA: " + cha.getNumberOfClasses());
			
			//AllApplicationEntrypoints entries = new AllApplicationEntrypoints(scope, cha);
			Iterable<Entrypoint> entries = Util.makeMainEntrypoints(scope, cha);
			//System.out.println("Entries: " + entries.);
			
			AnalysisOptions o = new AnalysisOptions(scope, entries);
			//CallGraphBuilder builder = Util.makeRTABuilder(o, new AnalysisCache(), cha, scope);
			CallGraphBuilder builder = Util.makeZeroCFABuilder(o, new AnalysisCache(), cha, scope);
			CallGraph cg = builder.makeCallGraph(o, null);
			System.out.println("Complete making call graphs: " + cg.getNumberOfNodes());
			Graph<CGNode> g = PDFCallGraph.pruneForAppLoader(cg);
			System.out.println("After pruning: " + g.getNumberOfNodes());
			
			Iterator<CGNode> nodes = g.iterator();
			while (nodes.hasNext()) {
				CGNode node = nodes.next();
				if (node.getMethod().isSynthetic()) {
					continue ;
				}
				
				System.out.println("Method: " + node.getMethod());
				Iterator<CallSiteReference> callSites = node.iterateCallSites();
				//System.out.println(node.getIR());
				while (callSites.hasNext()) {
					CallSiteReference site = callSites.next();
					Set<CGNode> targets = cg.getPossibleTargets(node, site);
					
					for (CGNode t: targets) {
						System.out.println("Potential site: " + t.getMethod());
						System.out.println("Class: " + t.getMethod().getDeclaringClass().getName());
						System.out.println("Method: " + t.getMethod().getName() + " " + t.getMethod().getDescriptor()) ;
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}*/

}
