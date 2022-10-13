package com.smartoptiontrades.main;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.SessionExpiryHook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Properties;

public class GetKiteConnect {

    public KiteConnect retrieve () throws ClassNotFoundException, SQLException {
    	
    	KiteConnect kiteConnect = null;
        Properties prop = new GetPropertiesObject().retrieve();
        
        String appKey = "";
		String accessToken = "";
		String publicToken = "";
        
		Class.forName(prop.getProperty("DB_CLASS_NAME"));
		Connection con=DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
		
		Statement stmt=con.createStatement();
		
        
        ResultSet rs = stmt.executeQuery("Select access_token,public_token,api_key from algotrade.user_token_db where userid='"+prop.getProperty("USER_ID")+"';");
        
        while(rs.next()){
        	accessToken=rs.getString(1);
        	publicToken=rs.getString(2);
        	appKey=rs.getString(3);
        	
        }
    	
        try {
               
                kiteConnect = new KiteConnect(appKey);

                kiteConnect.setUserId(prop.getProperty("USER_ID"));

                kiteConnect.setSessionExpiryHook(new SessionExpiryHook() {
                    @Override
                    public void sessionExpired() {
                        System.out.println(LocalDateTime.now()+" : Session Expired");
                    }
                });
                
                kiteConnect.setAccessToken(accessToken);
                kiteConnect.setPublicToken(publicToken);
                
                      
                                
        }catch (Exception e) {
        	System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Kite Connect Module Block A!!!!!!!!!!!!!!!");
			System.out.println(LocalDateTime.now()+" : "+e.getMessage());
			System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        
        return kiteConnect;
    }
}
