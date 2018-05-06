package edu.columbia.cs.psl.macneto.utils;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.columbia.cs.psl.macneto.pojo.SCCMethods;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedSparseGraph;

public class StructureAnalyzer {
	
	public static final Logger logger = LogManager.getLogger(StructureAnalyzer.class);
		
	public static void analyzePageRank(List<SCCMethods> methods) {
		DirectedSparseGraph<SCCMethods, Integer> g = new DirectedSparseGraph<SCCMethods, Integer>();
		
		int edgeId = 0;
		for (SCCMethods m: methods) {
			g.addVertex(m);
			
			for (SCCMethods c: m.getCalleeScc().keySet()) {
				g.addEdge(edgeId++, m, c);
			}
			m.setOutDegree(m.getCalleeScc().size());
			
			int inDegree = 0;
			for (SCCMethods test: methods) {
				if (test == m) {
					continue ;
				}
				
				if (test.getCalleeScc().keySet().contains(m)) {
					inDegree++;
				}
			}
			m.setInDegree(inDegree);
		}
		
		PageRank<SCCMethods, Integer> pr = new PageRank<SCCMethods, Integer>(g, 0.15);
		pr.setTolerance(Math.pow(10, -6));
		pr.setMaxIterations(1000);
		pr.evaluate();
		
		for (SCCMethods m: g.getVertices()) {
			double centrality = pr.getVertexScore(m);
			m.setCentrality(centrality);
		}
	}

}
