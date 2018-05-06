package edu.columbia.cs.psl.macneto.utils;

public class DummyTest {
	
	public static void main(String[] args) {
		//String test = "org.apache.http.conn.util.InetAddressUtils";
		//LightweightUtils.init(null);
		//System.out.println(LightweightUtils.checkSysCall("org.apache.http.conn.util"));
		
		String regex = "com.squareup.okhttp.*";
		System.out.println("com.squareup.okhttp.OkHttpClient-<init>-()".matches(regex));
	}

}
