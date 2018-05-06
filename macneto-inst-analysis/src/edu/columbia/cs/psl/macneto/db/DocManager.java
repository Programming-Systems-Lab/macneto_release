package edu.columbia.cs.psl.macneto.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.reflect.TypeToken;

import edu.columbia.cs.psl.macneto.pojo.CallLookup.CallNode;
import edu.columbia.cs.psl.macneto.utils.LightweightUtils;
import edu.columbia.cs.psl.macneto.utils.MethodChopper.CodeRep;

public class DocManager {
	
	private static final Logger logger = LogManager.getLogger(DocManager.class);
	
	private static Connection conn = null;
	
	public synchronized static Connection getConnection() {
		try {
			if (conn == null || conn.isClosed() || !conn.isValid(3)) {
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection("jdbc:sqlite:db/macneto.db");
			}
			return conn;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		//Something is wrong here
		return null;
	}
	
	public static Connection getAnotherConnection(String dbpath) {
		try {
			Class.forName("org.sqlite.JDBC");
			String jdbc = "jdbc:sqlite:" + dbpath;
			Connection conn = DriverManager.getConnection(jdbc);
			return conn;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		return null;
	}
	
	public synchronized static void deleteDB() {
		File dbDir = new File("db");
		if (!dbDir.exists()) {
			dbDir.mkdir();
		}
		
		File dbFile = new File("db/macneto.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}
	
	public synchronized static void initDB() {		
		try {
			Connection conn = getConnection();
			//Statement stmt = conn.createStatement();
			String sql = "CREATE TABLE APP " + "(ID INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ "NAME TEXT NOT NULL, "
					+ "TOTAL_METHOD INTEGER, "
					+ "APP_METHOD INTEGER, "
					+ "TEXT_RICH, "
					+ "DESP TEXT);";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.execute();
			stmt.close();
			logger.info("Creating APP table");
			
			String methodSql = "CREATE TABLE METHOD " + "(ID INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ "A_ID INTEGER NOT NULL, "
					+ "TOPIC INTEGER DEFAULT -1, "
					+ "TOPIC_DIST TEXT, "
					+ "METHODKEY TEXT NOT NULL, "
					+ "M_VEC TEXT NOT NULL, "
					+ "SEQ TEXT NOT NULL, " 
					+ "INDICES TEXT, "
					+ "LOC INTEGER, "
					+ "KEYWORD TEXT, "
					+ "FOREIGN KEY(A_ID) REFERENCES APP(ID));";
			stmt = conn.prepareStatement(methodSql);
			stmt.execute();
			stmt.close();
			logger.info("Creating METHOD table");
			
			String sourceSql = "CREATE TABLE SOURCE " + "(ID INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ "METHODKEY TEXT NOT NULL, "
					+ "INDICES TEXT NOT NULL, "
					+ "LOC INTEGER NOT NULL, "
					+ "KEYWORD TEXT);";
			stmt = conn.prepareStatement(sourceSql);
			stmt.execute();
			stmt.close();
			logger.info("Creating SOURCE table");
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static void insertSource(List<CodeRep> methodIndices) {
		Connection conn = getConnection();
		try {
			conn.setAutoCommit(false);
			
			String sql = "INSERT INTO SOURCE(METHODKEY, INDICES, LOC, KEYWORD) VALUES(?,?,?,?)"; 
			PreparedStatement stmt = conn.prepareStatement(sql);
			TypeToken<int[]> token = new TypeToken<int[]>(){};
			int counter = 0;
			for (CodeRep cr: methodIndices) {
				String methodKey = cr.methodKey;
				int[] indices = cr.indices;
				int loc = cr.loc;
				String indexString = LightweightUtils.convertTypeToJsonString(indices, token);
				
				stmt.setString(1, methodKey);
				stmt.setString(2, indexString);
				stmt.setInt(3, loc);
				stmt.setString(4, cr.keywords);
				
				stmt.addBatch();
				counter++;
				if(counter % 1000 == 0) {
					stmt.executeBatch();
					conn.commit();
				}
			}
			
			if (counter % 1000 != 0) {
				stmt.executeBatch();
			}
			conn.commit();
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (Exception ex) {
				logger.error("Error: ", ex);
			}
		}
	}
	
	public synchronized static int insertApp(String appName) {
		try {
			Connection conn = getConnection();
			Statement stmt = conn.createStatement();
			String sql = "INSERT INTO APP(NAME) VALUES('" + appName + "');";
			stmt.executeUpdate(sql);
			
			String idSql = "SELECT last_insert_rowid() FROM APP";
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(idSql);
			
			while (rs.next()) {
				return rs.getInt(1);
			}
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		//Should not touch here
		return -1;
	}
	
	public synchronized static void removeApp(String appName) {
		try {
			Connection conn = getConnection();
			Statement stmt = conn.createStatement();
			String sql = "DELETE FROM APP WHERE NAME='" + appName + "'";
			stmt.executeUpdate(sql);
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static HashMap<String, IndexLoc> loadSources() {
		try {
			HashMap<String, IndexLoc> recorder = new HashMap<String, IndexLoc>();
			
			Connection conn = getConnection();
			String loadSql = "SELECT * FROM SOURCE";
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(loadSql);
			TypeToken<int[]> indexToken = new TypeToken<int[]>(){};
			while (rs.next()) {
				String queryKey = rs.getString(2);
				String indexString = rs.getString(3);
				int[] indices = LightweightUtils.convertJsonStringToType(indexString, indexToken);
				int loc = rs.getInt(4);
				String keywords = rs.getString(5);
				
				if (recorder.containsKey(queryKey)) {
					/*IndexLoc indexLoc = recorder.get(queryKey);
					ArrayList<Integer> list = indexLoc.indices;
					for (int i = 0; i < indices.length; i++) {
						list.add(indices[i]);
					}
					
					indexLoc.loc += loc;*/
					logger.error("Duplicated method key in source: " + queryKey);
					System.exit(-1);
				} else {
					ArrayList<Integer> list = new ArrayList<Integer>();
					for (int i = 0; i < indices.length; i++) {
						list.add(indices[i]);
					}
					IndexLoc indexLoc = new IndexLoc();
					indexLoc.indices = list;
					indexLoc.loc = loc;
					indexLoc.keywords = keywords;
					recorder.put(queryKey, indexLoc);
				}
			}
			
			return recorder;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		return null;
	}
	
	public synchronized static void insertMethodVecs(int appId, Collection<CallNode> calls) {
		Connection conn = getConnection();
		try {
			//Load all methods from source code for checking
			HashMap<String, IndexLoc> allSources = loadSources();
			
			conn.setAutoCommit(false);
			
			String sql = "INSERT INTO METHOD(A_ID,METHODKEY,M_VEC,SEQ,INDICES,LOC,KEYWORD) VALUES(" + appId + ", ?, ?, ?, ?, ?, ?)";
			PreparedStatement ps = conn.prepareStatement(sql);
			TypeToken<HashMap<Integer, Double>> token = new TypeToken<HashMap<Integer, Double>>(){};
			TypeToken<ArrayList<Integer>> listToken = new TypeToken<ArrayList<Integer>>(){};
			int counter = 0;
			for (CallNode call: calls) {
				if (call.getSeq() == null || call.getSeq().isEmpty()) {
					//logger.info("Empty seq: " + call.getMethodKey());
					continue ;
				}
				counter++;
				
				ps.setString(1, call.key);
				HashMap<Integer, Double> cleanDist = LightweightUtils.contractMethodDist(call.getMachineDist());
				String jsonString = LightweightUtils.convertTypeToJsonString(cleanDist, token);
				ps.setString(2, jsonString);
				
				String seqString = LightweightUtils.convertTypeToJsonString(call.getSeq(), listToken);
				ps.setString(3, seqString);
				
				//Get indices
				String queryKey = call.queryKey;
				IndexLoc indexLoc = allSources.get(queryKey);
				
				if (indexLoc != null && indexLoc.indices.size() != 0) {
					ArrayList<Integer> indices = indexLoc.indices;
					String indexString = LightweightUtils.convertTypeToJsonString(indices, listToken);
					ps.setString(4, indexString);
					
					int loc = indexLoc.loc;
					String keywords = indexLoc.keywords;
					ps.setInt(5, loc);
					ps.setString(6, keywords);
				} else {
					ps.setString(4, null);
					ps.setInt(5, -1);
					ps.setString(6, null);
				}
				
				//ps.setDouble(6, call.centrality);
				//ps.setInt(7, call.inDegree);
				//ps.setInt(8, call.outDegree);
				
				ps.addBatch();
				
				if (counter % 5000 == 0) {
					ps.executeBatch();
					conn.commit();
					logger.info("Commiting " + counter + " vecs");
				}
			}
			
			//residual
			if (counter % 5000 != 0) {
				ps.executeBatch();
				logger.info("Commiting residual " + counter + " vecs");
			}
			conn.commit();
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (Exception ex) {
				logger.error("Error: ", ex);
			}
		}
	}
	
	public static HashSet<String> getAllMethods(String appName, String obdb) {
		try {
			Connection conn = null;
			if (obdb == null) {
				conn = getConnection();
			} else {
				conn = getAnotherConnection(obdb);
			}
			int appId = -1;
			String appSql = "SELECT ID FROM APP WHERE NAME = '" + appName + "'";
			PreparedStatement appStmt = conn.prepareStatement(appSql);
			ResultSet appRs = appStmt.executeQuery();
			while (appRs.next()) {
				appId = appRs.getInt(1);
				break ;
			}
			
			if (appId == -1) {
				logger.info("Find no app: " + appName);
				return null;
			}
			
			String sql = "SELECT * FROM METHOD WHERE A_ID = " + appId;
			PreparedStatement stmt = conn.prepareStatement(sql);
			ResultSet rs = stmt.executeQuery();
			HashSet<String> ret = new HashSet<String>();
			while (rs.next()) {
				String methodKey = rs.getString("METHODKEY");
				ret.add(methodKey);
			}
			
			return ret;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		return null;
	}
	
	public static HashMap<String, Integer> getAllApks() {
		try {
			HashMap<String, Integer> ret = new HashMap<String, Integer>();
			String sql = "SELECT * FROM APP";
			Connection conn = getConnection();
			PreparedStatement stmt = conn.prepareStatement(sql);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				int appId = rs.getInt("ID");
				String appName = rs.getString("NAME");
				ret.put(appName, appId);
			}
			
			return ret;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		return null;
	}
	
	public static HashMap<Integer, HashSet<String>> getInjects(String obfusDB) {
		Connection conn = getAnotherConnection(obfusDB);
		HashMap<Integer, HashSet<String>> ret = new HashMap<Integer, HashSet<String>>();
		try {
			String sql = "SELECT * FROM INJECT";
			PreparedStatement stmt = conn.prepareStatement(sql);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				int apkId = rs.getInt(1);
				String methodKey = rs.getString(2);
				
				if (ret.containsKey(apkId)) {
					ret.get(apkId).add(methodKey);
				} else {
					HashSet<String> methods = new HashSet<String>();
					methods.add(methodKey);
					ret.put(apkId, methods);
				}
			}
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		return ret;
	}
	
	public static void main(String[] args) {
		deleteDB();
		initDB();
	}
	
	public static class IndexLoc {
		public ArrayList<Integer> indices;
		
		public int loc;
		
		public String keywords;
	}

}
