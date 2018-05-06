package edu.columbia.cs.psl.macneto.utils;

import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

public class LuceneTester {
	
	/**
	 * 
	 * @param i
	 * @param j
	 * @return
	 */
	public int add(int i, int j) {
		return i + j;
	}
	
	/**
	 * test123
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String text = "/**\n" +
				"* this is a base64 algorithm\n" +
				"* @param  args\n" +
				"* @throws Exception\n" + 
				"*/";
		
		text = text.replaceAll("@param.*", "").replaceAll("@throws.*", "").replaceAll("@return.*", "");
		Analyzer analyzer = new StandardAnalyzer();
		//Analyzer analyzer = new StopAnalyzer();
		TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(text));
		CharTermAttribute cattr = tokenStream.addAttribute(CharTermAttribute.class);
		tokenStream.reset();
		while (tokenStream.incrementToken()) {
			System.out.println(cattr.toString());
		}
		tokenStream.end();
		tokenStream.close();
		analyzer.close();
		
		//StandardAnalyzer standard = new StandardAnalyzer();
		//TokenStream ts1 = standard.tokenStream(null, new StringReader(text));
		//ts1 = new SynonymFilter(ts1);
	}

}
