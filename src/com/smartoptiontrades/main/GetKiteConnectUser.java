package com.smartoptiontrades.main;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.SessionExpiryHook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Properties;

public class GetKiteConnectUser {
	
public HashMap<String,KiteConnect> retrieve () throws ClassNotFoundException, SQLException {
    	
    	KiteConnect kiteConnect = null;
        Properties prop = new GetPropertiesObject().retrieve();
        HashMap<String,KiteConnect> userKiteMap = new HashMap<String,KiteConnect>();
        
		Class.forName(prop.getProperty("DB_CLASS_NAME"));
		Connection con=DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
		
		Statement stmt=con.createStatement();
		
		ResultSet rs = stmt.executeQuery("Select userid,access_token,public_token,api_key,api_secret_key from algotrade.user_token_db where platform='ZERODHA' and userId='KUG028';");
		
        //ResultSet rs = stmt.executeQuery("Select userid,access_token,public_token,api_key,api_secret_key from algotrade.user_token_db where platform='ZERODHA' and is_gamma = 1 and is_active = 1 and is_day_trading_active = 1 and timestamp > '"+LocalDateTime.now().getYear()+"-"+LocalDateTime.now().getMonthValue()+"-"+LocalDateTime.now().getDayOfMonth()+" 08:00:00';");

        while(rs.next()){
        	try{
        		String userId = rs.getString(1);
        		
	        	kiteConnect = new KiteConnect(rs.getString(4));
	
	            kiteConnect.setUserId(userId);
	            
	            kiteConnect.setSessionExpiryHook(new SessionExpiryHook() {
	                @Override
	                public void sessionExpired() {
	                    System.out.println(LocalDateTime.now()+" : User "+userId+" Session Expired");
	                }
	            });
	            
	            kiteConnect.setAccessToken(rs.getString(2));
	            kiteConnect.setPublicToken(rs.getString(3));
	            
        	}catch (Exception e) {
            	System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Kite Connect Module Block A!!!!!!!!!!!!!!!");
    			System.out.println(LocalDateTime.now()+" : "+e.getMessage());
    			System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
            
            userKiteMap.put(rs.getString(1), kiteConnect);
        	
        }
        
        con.close();
        
        return userKiteMap;
    }
}
