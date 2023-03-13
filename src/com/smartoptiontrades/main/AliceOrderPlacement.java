package com.smartoptiontrades.main;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AliceOrderPlacement implements Runnable {

    // one instance, reuse
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private String userId,secretToken,instrumentName,productType,instrumentToken,action;
    int quantity;
    double price;
    
    
    AliceOrderPlacement(String userId, String secretToken,String instrumentName, String productType,String instrumentToken,int quantity,String action,double price){
    	
    	this.userId=userId;
    	this.secretToken=secretToken;
    	this.instrumentName=instrumentName;
    	this.productType=productType;
    	this.instrumentToken=instrumentToken;
    	this.quantity=quantity;
    	this.action=action;
    	this.price=price;
    }

    public void run() {
    	
        try {

            //System.out.println("Placing Bracket Order for ");
            placeLimitOrder(secretToken);
            
        } catch (Exception e) {
			// TODO Auto-generated catch block
        	System.out.println(LocalDateTime.now()+" : Exception in Entry Order Placement for user "+userId);
        	try {
				new RestTelegramCall().sendUpdate(userId+" - Exception Entry Order Placement");
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			//e.printStackTrace();
		} finally {
            	try {
					httpClient.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			
        }
    }


    private void placeLimitOrder(String secretToken) throws Exception {
    	
    	Properties prop = new GetPropertiesObject().retrieve();
    	
    	Class.forName(prop.getProperty("DB_CLASS_NAME"));
		Connection con = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));

    	Statement stmt = con.createStatement();
    	
    	ResultSet rs = stmt.executeQuery("Select sigma_bnf_lot from user_token_db where userid ='"+this.userId+"';");
    	
    	while(rs.next()) {
	    	
			this.quantity = this.quantity * Integer.parseInt(rs.getString(1));
					
		}
    	
    	con.close();    	
    	
    	System.out.println(LocalDateTime.now()+" : Placing "+action+" order for "+instrumentName+" Quantity = "+quantity+" for "+userId);
    	
    	HttpPost request = new HttpPost("https://a3.aliceblueonline.com/rest/AliceBlueAPIService/api/placeOrder/executePlaceOrder");
        
        JsonObject json = new JsonObject();
        json.addProperty("complexty", "regular");
        json.addProperty("discqty", "0");
        json.addProperty("exch", "NFO");
        json.addProperty("pCode", productType);
        if(price>0)
        	json.addProperty("prctyp", "L");
        else
        	json.addProperty("prctyp", "MKT");
        json.addProperty("price", price);
        json.addProperty("qty", quantity);
        json.addProperty("ret", "DAY");
        json.addProperty("symbol_id", instrumentToken);
        json.addProperty("trading_symbol", instrumentName);
        json.addProperty("transtype",action);
        json.addProperty("trigPrice","0.0");
        json.addProperty("orderTag","order1");
                
        //System.out.println("["+json.toString()+"]");
        
        StringEntity params = new StringEntity("["+json.toString()+"]");
        request.addHeader("Authorization", "Bearer "+userId+" "+secretToken);
        request.addHeader("content-type", "application/json");
        request.setEntity(params);
        HttpResponse response = httpClient.execute(request);
        
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        System.out.println(responseString);
         
    }

}