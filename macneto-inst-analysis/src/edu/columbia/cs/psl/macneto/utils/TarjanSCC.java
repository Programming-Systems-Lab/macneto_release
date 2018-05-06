package edu.columbia.cs.psl.macneto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.pojo.SCCMethods;

/**
 * Inspired by http://algs4.cs.princeton.edu/42digraph/TarjanSCC.java.html
 * @author mikefhsu
 *
 */
public class TarjanSCC {
	
	private static final Logger logger = LogManager.getLogger(TarjanSCC.class);
	
	private static int counter = 0;

    //private boolean[] marked;        // marked[v] = has v been visited?
    //private int[] id;                // id[v] = id of strong component containing v
    //private int[] low;               // low[v] = low number of v
    private HashSet<Integer> marked = new HashSet<Integer>();
    private HashMap<Integer, Integer> id = new HashMap<Integer, Integer>();
    private HashMap<Integer, Integer> low = new HashMap<Integer, Integer>();
    private int pre;                 // preorder number counter
    private int count;               // number of strongly-connected components
    private Stack<Integer> stack;
    //private DirectedGraph g;
    private Map<String, CallNode> g;
    private int maxId;


    /**
     * Computes the strong components of the digraph <tt>G</tt>.
     * @param G the digraph
     */
    public TarjanSCC(Map<String, CallNode> g) {
    	this.g = g;
        //this.marked = new boolean[g.getNodeCount()];
        this.stack = new Stack<Integer>();
        //this.id = new int[g.getNodeCount()]; 
        //this.low = new int[g.getNodeCount()];
        /*for (int v = 0; v < g.getNodeCount(); v++) {
            if (!marked[v]) dfs(g, v);
        }*/
        
        for (CallNode n: g.values()) {
        	int nId = n.getNodeId();
        	this.id.put(nId, 0);
        	this.low.put(nId, 0);
        	
        	if (nId > this.maxId) {
        		this.maxId = nId;
        	}
        }
        
        for (CallNode n: g.values()) {
        	if (!marked.contains(n.getNodeId())) {
        		dfs(n);
        	}
        }
        
    }

    private void dfs(CallNode curMethod) {
    	counter++;
    	int v = curMethod.getNodeId();
    	
        //marked[v] = true;
        this.marked.add(v);
        
        //low[v] = pre++;
        this.low.put(v, pre++);
        
        //int min = low[v];
        int min = this.low.get(v);
        
        this.stack.push(v);
        
        if (curMethod.key == null) {
        	System.out.println("Null key: " + curMethod);
        	System.out.println(ContextManager.getSynthetic());
        	System.exit(-1);
        }
        
        if (counter % 1000 == 0) {
        	System.out.println("Process #" + counter + " method: " + curMethod.key);
        }
        
        for (String calleeKey: curMethod.getCallees().keySet()) {
        	CallNode callee = ContextManager.lookupCalls(calleeKey);
        	int w = callee.getNodeId();
        	
        	if (!this.marked.contains(w)) {
    			dfs(callee);
    		}
    		
    		if (this.low.get(w).intValue() < min) {
    			min = this.low.get(w).intValue();
    		}
        }
        
    	if (min < this.low.get(v).intValue()) {
    		low.put(v, min);
    		return ;
    	}
        
        int w;
        do {
            w = stack.pop();
            //id[w] = count;
            //low[w] = g.getNodeCount();
            this.id.put(w, this.count);
            this.low.put(w, maxId);
        } while (w != v);
        this.count++;
    }


    /**
     * Returns the number of strong components.
     * @return the number of strong components
     */
    public int count() {
        return this.count;
    }


    /**
     * Are vertices <tt>v</tt> and <tt>w</tt> in the same strong component?
     * @param v one vertex
     * @param w the other vertex
     * @return <tt>true</tt> if vertices <tt>v</tt> and <tt>w</tt> are in the same
     *     strong component, and <tt>false</tt> otherwise
     */
    public boolean stronglyConnected(int v, int w) {
        //return id[v] == id[w];
        return this.id.get(v).intValue() == this.id.get(w).intValue();
    }

    /**
     * Returns the component id of the strong component containing vertex <tt>v</tt>.
     * @param v the vertex
     * @return the component id of the strong component containing vertex <tt>v</tt>
     */
    public int id(int v) {
        //return id[v];
        return this.id.get(v);
    }
    
    public List<SCCMethods> analyzeSCC(int apkId) {
        // number of connected components
        int m = this.count();

        // compute list of vertices in each strong component
        //Queue<Integer>[] components = (Queue<Integer>[]) new Queue[m];
        //List<MethodInfo>[] components = (ArrayList<MethodInfo>[]) new ArrayList[m];
        Set<CallNode>[] components = (HashSet<CallNode>[]) new HashSet[m];
        for (int i = 0; i < m; i++) {
            components[i] = new HashSet<CallNode>();
        }
        
        for (CallNode method: this.g.values()) {
        	if (ContextManager.isInjected(apkId, method.getMethodKey())) {
        		//System.out.println("Injected: " + method.getMethodKey());
        		continue ;
        	}
        	
        	int v = method.getNodeId();
        	components[this.id(v)].add(method);
        }
        
        List<SCCMethods> ret = new ArrayList<SCCMethods>();
        Map<String, SCCMethods> sccCache = new HashMap<String, SCCMethods>();
        for (int i = 0; i < components.length; i++) {
        	Set<CallNode> methods = components[i];
        	
        	if (methods.size() == 0) {
        		continue ;
        	}
        	
        	SCCMethods scc = new SCCMethods(methods, i);
        	ret.add(scc);
        	
        	scc.getMethodStrings().forEach((method)->{
        		sccCache.put(method, scc);
        	});
        }
        
        //Construct edges between scc, replace method key with scc key
        ret.forEach(scc->{
        	Set<String> members = scc.getMethodStrings();
        	scc.getCallees().forEach((callee, weight)->{
        		if (!members.contains(callee)) {
        			if (!ContextManager.isInjected(apkId, callee)) {
        				SCCMethods calleeScc = sccCache.get(callee);
                		
                		if (calleeScc == null) {
                			logger.error("Find no child SCC: " + callee);
                		} else {
                			scc.addCalleeScc(calleeScc, weight);
                		}
        			}
        		}
        	});
        });
        
        //StructureAnalyzer.analyzePageRank(ret);
        
        return ret;
    }
}
