package com.smartoptiontrades.main;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Properties;

public class GetAliceConnectUser {
	
public HashMap<String,String> retrieve () throws ClassNotFoundException, SQLException {
    	
	
		HashMap<String,String> userAliceMap = new HashMap<String,String>();
		
        Properties prop = new GetPropertiesObject().retrieve();
        
        
        
		Class.forName(prop.getProperty("DB_CLASS_NAME"));
		Connection con=DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
		
		Statement stmt=con.createStatement();
		
		//ResultSet rs = stmt.executeQuery("Select userid,access_token,public_token,api_key,api_secret_key from algotrade.user_token_db where platform='ALICE' and userId='154560' and is_day_trading_active = 1 and timestamp > '"+LocalDateTime.now().getYear()+"-"+LocalDateTime.now().getMonthValue()+"-"+LocalDateTime.now().getDayOfMonth()+" 08:00:00';");
		
        ResultSet rs = stmt.executeQuery("Select userid,access_token from algotrade.user_token_db where platform='ALICE' and is_sigma_bnf = 1  and is_active = 1 and is_day_trading_active = 1 and timestamp > '"+LocalDateTime.now().getYear()+"-"+LocalDateTime.now().getMonthValue()+"-"+LocalDateTime.now().getDayOfMonth()+" 08:00:00';");

        while(rs.next()){
        	
            
        	userAliceMap.put(rs.getString(1), rs.getString(2));
        	
        }
        
        con.close();
        
        return userAliceMap;
    }
}
