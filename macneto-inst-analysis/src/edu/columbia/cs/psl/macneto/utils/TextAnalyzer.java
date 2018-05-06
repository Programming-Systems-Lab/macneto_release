package edu.columbia.cs.psl.macneto.utils;

import java.io.StringReader;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class TextAnalyzer {
	
	private static Logger logger = LogManager.getLogger(TextAnalyzer.class);
	
	protected static final Pattern DECAMELIZE_EXP = Pattern.compile("([A-Z]+)");
    
    protected static final Pattern ENCAMELIZE_EXP = Pattern.compile("_+(.)");

    public static String decamelize(String s) {
    	s = s.replaceAll("[^A-Z0-9a-z]", "");
    	Matcher m = DECAMELIZE_EXP.matcher(s);
    	return m.replaceAll(" $1").toLowerCase();
    }

    public static String encamelize(String s) {
    	s = s.toLowerCase();
    	Matcher m = ENCAMELIZE_EXP.matcher(s);

    	StringBuilder sb = new StringBuilder();
    	int last = 0;
    	while (m.find()) {
    		sb.append(s.substring(last, m.start()));
    		sb.append(m.group(1).toUpperCase());
    		last = m.end();
    	}
    	sb.append(s.substring(last));
    	return sb.toString();
    }
    
    public static String cleanComments(String comments) {
    	return comments.replaceAll("(non-Javadoc)", "").replaceAll("@.*", "");
    }
        
    public static String extractKeywords(String text) {
    	try {
    		/*if (isComment) {
    			//text = text.replaceAll("@param.*", "").replaceAll("@throws.*", "").replaceAll("@return.*", "");
    			text = text.replaceAll("(non-Javadoc)", "").replaceAll("@.*", "");
    		}*/
    		
    		Analyzer analyzer = new StandardAnalyzer();
    		//Analyzer analyzer = new StopAnalyzer();
    		//Analyzer analyzer = new EnglishAnalyzer();
    		//TokenStream tokenStream = new PorterStemFilter(analyzer.tokenStream(null, new StringReader(text)));
    		TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(text));
    		CharTermAttribute cattr = tokenStream.addAttribute(CharTermAttribute.class);
    		tokenStream.reset();
    		
    		StringBuilder sb = new StringBuilder();
    		while (tokenStream.incrementToken()) {
    			//System.out.println(cattr.toString());
    			//record.add(cattr.toString());
    			String term = cattr.toString();
    			sb.append(term + " ");
    		}
    		tokenStream.end();
    		tokenStream.close();
    		analyzer.close();
    		
    		if (sb.length() > 0) {
    			return sb.substring(0, sb.length() - 1);
    		}
    	} catch (Exception ex) {
    		logger.error("Error: ", ex);
    	}
    	return null;
    }
    
    /**
     * 
     * @param args
     */
    public static void main(String[] args) {
    	System.out.println("_idDog" + " " + decamelize("_idDog"));
    	System.out.println("set_up" + " " + decamelize("set_up"));
    	System.out.println("ID_DOG" + " " + encamelize("ID_DOG"));
    	System.out.println("idABCDog" + " " + decamelize("idABCDog"));
    	System.out.println("ID_A_B_C_DOG" + " " + encamelize("ID_A_B_C_DOG"));
    	System.out.println("ID_A__B___C_DOG" + " " + encamelize("ID_A__B___C_DOG"));
    	System.out.println("underROC" + " " + decamelize("underROC"));
    	/*String s = "I         love this the game	loves cake cakes universal base64 love";
    	System.out.println(extractKeywords(s, false));
    	System.out.println(extractKeywords("@see android.app.Activity#onCreate(android.os.Bundle)", true));
    	System.out.println(extractKeywords("@param i\n it is an integer", true));*/
    }

}
