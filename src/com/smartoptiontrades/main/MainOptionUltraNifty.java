package com.smartoptiontrades.main;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainOptionUltraNifty {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		
		ExecutorService executor = Executors.newFixedThreadPool(2);

		executor.execute(new OptionUltraNFCE());
		executor.execute(new OptionUltraNFPE());
		
	}

}
