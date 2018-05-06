package edu.columbia.cs.psl.macneto.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class SecondOutside {
	int secondInt;
}

enum myEnum {
	a, b, c;
}

/**
 * Test123
 * @author mikefhsu
 *
 */
public class TestClass {
	
	public int instanceInt;
	
	public static double staticDouble;
	
	static {
		staticDouble = 18.0;
	}
	
	/**
	 * Test constructor
	 */
	public TestClass() {
		
	}
	
	public TestClass(int i) {
		this();
		this.instanceInt = i;
	}
	
	/**
	 * A java doc for instance method
	 * @param a
	 */
	public void instanceMethod(String a) {
		System.out.println(a);
	}
	
	public static float staticMethod() {
		float a = 8f + 9f;
		return a;
	}
	
	@Override
	public String toString() {
		return "abc";
	}
	
	public static class FirstInner {
		int firstInnerInt = 5;
		
		public String firstInnerMethod(String s) {
			return s + "abc";
		}
		
		public class SecondInnerClass{
			
			Object[] objArray;
			
			public void complexMethod(ArrayList<FirstInner> collection) {
				//Test 123
				Comparator<FirstInner> comp = new Comparator<FirstInner>(){
					public int compare(FirstInner i, FirstInner j) {
						if (i.firstInnerInt > j.firstInnerInt) {
							return 1;
						} else if (i.firstInnerInt < j.firstInnerInt) {
							return -1;
						} else {
							return 0;
						}
					}
				};
				
				Collections.sort(collection, comp);
			}	
		}
	}

}
