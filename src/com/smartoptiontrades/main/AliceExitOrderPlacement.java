package com.smartoptiontrades.main;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AliceExitOrderPlacement implements Runnable {

    // one instance, reuse
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private String userId,secretToken,instrumentName,productType,instrumentToken,action;
    int quantity;
    double price;
    
    
    AliceExitOrderPlacement(String userId, String secretToken,String instrumentName, String productType,String instrumentToken,int quantity,String action,double price){
    	
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
        	System.out.println(LocalDateTime.now()+" : Exception in Exit Order Placement for user "+userId);
        	try {
				new RestTelegramCall().sendUpdate(userId+" - Exception Exit Order Placement");
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
    	
    	quantity = getRemainingQuantity(secretToken, instrumentName);
    	
    	if(quantity > 0) {
    		
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
        
    public int getRemainingQuantity(String secretToken, String instrumentName) {
    	
    	int quantity=0;
    	
    	HttpPost request = new HttpPost("https://a3.aliceblueonline.com/rest/AliceBlueAPIService/api/positionAndHoldings/positionBook");

        JsonObject json = new JsonObject();
        json.addProperty("ret", "DAY");
        
        StringEntity params = null;
		try {
			params = new StringEntity(json.toString());
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        // add request headers
        request.addHeader("Authorization", "Bearer "+userId+" "+secretToken);
        request.addHeader("content-type", "application/json");
        request.setEntity(params);
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {


            HttpEntity entity = response.getEntity();

            if (entity != null) {

            	String result = EntityUtils.toString(entity);
            	
            	//System.out.println(result);
                
                Gson gson = new Gson();
               
                JsonPosition[] jsonPositionArray = gson.fromJson(result, JsonPosition[].class);  

                for(JsonPosition jsonPosition : jsonPositionArray) {
                	
                	if(jsonPosition.getTsym().equalsIgnoreCase(instrumentName)) {
                		System.out.println(userId+"|"+jsonPosition.getTsym()+"|"+jsonPosition.getBqty()+"|"+jsonPosition.getSqty());
                		quantity = Math.abs(Integer.parseInt(jsonPosition.getBqty())-Integer.parseInt(jsonPosition.getSqty()));
                	}
                }

            }
            
        }catch(Exception e) {
        	
        	System.out.println(this.userId+" || Exception "+e.getMessage());
        }
        
        return quantity;
    }

}