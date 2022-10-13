package com.smartoptiontrades.main;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class GetPropertiesObject {
	
	public Properties retrieve(){
		
		InputStream input = null;
		Properties prop = new Properties();
        String filename = "/niftyalgotrader/TraderBot/reference/smart_option_selling.properties";
        
		try {
			input = new FileInputStream(filename);
		} catch (FileNotFoundException e1) {
		// TODO Auto-generated catch block
			e1.printStackTrace();
			System.out.println("Properties file is not found in the directory");
		}
		
		try {
			prop.load(input);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Properties file does not have the value");
		}
		
		return prop;
		
	}

}
