package edu.columbia.cs.psl.macneto.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.android.dx.dex.file.DexFile;
import com.android.dx.io.DexBuffer;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;
import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.DexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler;
//import com.googlecode.dex2jar.reader.DexFileReader;
//import com.googlecode.dex2jar.v3.Dex2jar;
//import com.googlecode.dex2jar.v3.DexExceptionHandlerImpl;

public class ApkHelper {
	
	private static final Logger logger = LogManager.getLogger(ApkHelper.class);
	
	public static final Options options = new Options();
	
	static {
		options.addOption("a", true, "apk directory");
		options.addOption("o", true, "output directory");
	}
	
	public static void collectApks(File apkDir, ArrayList<File> apks) {
		if (apkDir.isFile()) {
			if (apkDir.getName().endsWith(".apk"))
				apks.add(apkDir);
		} else {
			for (File f: apkDir.listFiles()) {
				collectApks(f, apks);
			}
		}
	}
	
	public static File copyStream(ZipInputStream zis, String name, String extension) {
		try {
			File tmpFile = File.createTempFile(name, extension);
			tmpFile.deleteOnExit();
			
			byte[] buf = new byte[1024];
			FileOutputStream outStream = new FileOutputStream(tmpFile);
			int len = 0;
			while ((len = zis.read(buf)) != -1) {
				outStream.write(buf, 0, len);
			}
			return tmpFile;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		return null;
	}
	
	public static byte[] convertInputStream(InputStream is) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int len = -1;
			while ((len = is.read(buf)) != -1) {
				bos.write(buf, 0, len);
			}
			byte[] ret = bos.toByteArray();
			bos.close();
			return ret;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
		
		return null;
	}
	
	public static int[] processApk(File apk, String outputDir) {
		try {
			//ZipInputStream zis = new ZipInputStream(new FileInputStream(apk));
			//ZipEntry ze = zis.getNextEntry();
			HashMap<String, byte[]> maniMap = new HashMap<String, byte[]>();
			//List<DexBuffer> dexList = new ArrayList<DexBuffer>();
			HashMap<String, DexFileReader> dexMap = new HashMap<String, DexFileReader>();
			ZipFile zipFile = new ZipFile(apk);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				String fileName = ze.getName();
				if (fileName.endsWith("AndroidManifest.xml")) {
					InputStream maniInputStream = zipFile.getInputStream(ze);
					byte[] maniBytes = convertInputStream(maniInputStream);
					maniMap.put(fileName, maniBytes);
				} else if (fileName.endsWith(".dex")) {
					InputStream dexStream = zipFile.getInputStream(ze);
					byte[] dexBytes = convertInputStream(dexStream);
					DexFileReader dexReader = new DexFileReader(dexBytes);
					dexMap.put(fileName, dexReader);
					//DexBuffer dexBuf = new DexBuffer(dexBytes);
					//dexList.add(dexBuf);
				}
			}
			
			/*ByteArrayOutputStream apkData = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			InputStream apkStream = new FileInputStream(apk);
			int len = 0;
			while ((len = apkStream.read(buf)) != -1) {
				apkData.write(buf, 0, len);
			}
			byte[] apkBytes = apkData.toByteArray();*/
			
			String outputJar = outputDir + "/" + apk.getName().substring(0, apk.getName().length() - 4) + ".jar";
			logger.info("Creating jar: " + outputJar);
			File jarFile = new File(outputJar);
			Path jarPath = jarFile.toPath();
			FileOutputStream fos = new FileOutputStream(outputJar);
			//ZipOutputStream zos = new ZipOutputStream(fos);
			
			//byte[] manifest = null;
			if (maniMap.size() > 1) {
				logger.info("Multi-fest: " + apk.getName());
				logger.info("Files: " + maniMap.keySet());
			}
			
			if (dexMap.size() > 1) {
				logger.info("Multi-dex: " + apk.getName());
				logger.info("Files: " + dexMap.keySet());
			}
			
			try {
				//DexFileReader reader = new DexFileReader(dex);
				//DexExceptionHandlerImpl handler = new DexExceptionHandlerImpl().skipDebug(true);
				//Dex2jar.from(reader).withExceptionHandler(handler).reUseReg(false).topoLogicalSort(false || false).skipDebug(true).optimizeSynchronized(false).printIR(false).verbose(false).to(jarFile);
				//BaseDexFileReader reader = MultiDexFileReader.open(Files.readAllBytes(apk.toPath()));
				//BaseDexFileReader reader = MultiDexFileReader.open(apkBytes);
				BaseDexFileReader reader = new MultiDexFileReader(dexMap.values());
				BaksmaliBaseDexExceptionHandler handler = new BaksmaliBaseDexExceptionHandler();
				Dex2jar.from(reader).withExceptionHandler(handler).reUseReg(false).topoLogicalSort(false || false).skipDebug(true).optimizeSynchronized(false).printIR(false).noCode(false).to(jarPath);
			} catch (Exception ex) {
				logger.error("Fail to convert apk: " + apk.getAbsolutePath());
				logger.error("Error: ", ex);
			}
			
			//Pick the shortest name, because it is right under directory
			/*int length = Integer.MAX_VALUE;
			for (String f: maniMap.keySet()) {
				byte[] tmp = maniMap.get(f);
				if (f.length() < length) {
					manifest = tmp;
				}
			}*/
			
			//ZipEntry zipFest = new ZipEntry("AndroidManifest.xml");
			//zos.putNextEntry(zipFest);
			//byte[] maniData = Files.readAllBytes(manifest.toPath());
			//zos.write(manifest, 0, manifest.length);
			
			//ByteArrayOutputStream dexData = new ByteArrayOutputStream();
			/*dexMap.forEach((fname, dex)->{
				try {
					//DexFileReader reader = new DexFileReader(dex);
					//DexExceptionHandlerImpl handler = new DexExceptionHandlerImpl().skipDebug(true);
					//Dex2jar.from(reader).withExceptionHandler(handler).reUseReg(false).topoLogicalSort(false || false).skipDebug(true).optimizeSynchronized(false).printIR(false).verbose(false).to(jarFile);
					BaseDexFileReader reader = MultiDexFileReader.open(dex);
					BaksmaliBaseDexExceptionHandler handler = new BaksmaliBaseDexExceptionHandler();
					Dex2jar.from(reader).withExceptionHandler(handler).reUseReg(false).topoLogicalSort(false || false).skipDebug(true).optimizeSynchronized(false).printIR(false).noCode(false).to(jarPath);
				} catch (Exception ex) {
					logger.error("Error: ", ex);
				}
			});*/
			
			//Merge multi dex
			/*DexBuffer dexFile = null;
			if (dexList.size() > 1) {
				DexBuffer merge = new DexMerger(dexList.get(0), dexList.get(1), CollisionPolicy.KEEP_FIRST).merge();
				for (int i = 2; i < dexList.size(); i++) {
					merge = new DexMerger(merge, dexList.get(i), CollisionPolicy.KEEP_FIRST).merge(); 
				}
				//merge.s(dexOut);
			} else {
				dexFile = dexList.get(0);
			}*/
			
			//ZipEntry zipDex = new ZipEntry("classes.dex");
			//zos.putNextEntry(zipDex);
			//zos.write(dexData, 0, dexData.length);
			
			//zos.closeEntry();
			//zos.close();
			
			int[] ret = {maniMap.size(), dexMap.size()};
			
			return ret;
		} catch (Exception ex) {
			logger.error("Error: ", ex);
			ex.printStackTrace();
		}
		return null;
	}
	
	public static String checkApks(ArrayList<File> apks, String outputDir) {
		final StringBuilder sb = new StringBuilder();
		int succ = 0;
		int counter = 0;
		for (File apk: apks) {
			counter++;
			if (counter % 100 == 0) {
				System.out.println("Process #" + counter + " apk");
			}
			
			int[] ret = processApk(apk, outputDir);
			if (ret == null) {
				logger.error("Fail to process apk: " + apk.getAbsolutePath());
				continue ;
			}
			succ++;
			sb.append(apk.getName()).append(",").append(ret[0]).append(",").append(ret[1]).append("\n");
		}
		
		System.out.println("Successful apk: " + succ);
		return sb.toString();
	}
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		CommandLine command = parser.parse(options, args);
		
		String apksPath = command.getOptionValue("a");
		File apkDir = new File(apksPath);
		if (!apkDir.exists()) {
			logger.error("Invalid APK directory: " + apkDir.getAbsolutePath());
			System.exit(-1);
		}
		
		String outputPath = command.getOptionValue("o");
		File outputDir = new File(outputPath);
		if (!outputDir.exists()) {
			logger.info("Creating output APK directory: " + outputDir.getAbsolutePath());
			outputDir.mkdir();
		}
		
		ArrayList<File> apks = new ArrayList<File>();
		collectApks(apkDir, apks);
		System.out.println("Total apks: " + apks.size());
		
		//Check AndroidManifext.xml, classes.dex
		StringBuilder sb = new StringBuilder();
		sb.append("APK,hasManifest,#Dex\n");
		String results = checkApks(apks, outputDir.getAbsolutePath());
		sb.append(results);
		
		try {
			File reportFile = new File("apk_debug.csv");
			BufferedWriter bw = new BufferedWriter(new FileWriter(reportFile));
			bw.write(sb.toString());
			bw.flush();
			bw.close();
			logger.info("Report: " + reportFile.getAbsolutePath());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
