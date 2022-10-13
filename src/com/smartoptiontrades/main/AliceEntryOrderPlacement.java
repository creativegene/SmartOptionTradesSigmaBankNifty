package com.smartoptiontrades.main;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AliceEntryOrderPlacement implements Runnable {

    // one instance, reuse
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private String userId,secretToken,instrumentName,productType,instrumentToken,action;
    int quantity;
    double price;
    
    
    AliceEntryOrderPlacement(String userId, String secretToken,String instrumentName, String productType,String instrumentToken,int quantity,String action,double price){
    	
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
    	
    	
    	System.out.println(LocalDateTime.now()+" : Placing "+action+" order for "+instrumentName+" Quantity = "+quantity+" for "+userId);

        HttpPost post = new HttpPost("https://ant.aliceblueonline.com/api/v2/order");
        
        // add request parameter, form parameters
        
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("exchange", "NFO"));
        if(price==0)
        	urlParameters.add(new BasicNameValuePair("order_type", "MARKET"));
        else 
        	urlParameters.add(new BasicNameValuePair("order_type", "LIMIT"));
        urlParameters.add(new BasicNameValuePair("instrument_token", this.instrumentToken));
        urlParameters.add(new BasicNameValuePair("quantity", Integer.toString(this.quantity)));
        urlParameters.add(new BasicNameValuePair("disclosed_quantity", "0"));
        urlParameters.add(new BasicNameValuePair("transaction_type", this.action));
        urlParameters.add(new BasicNameValuePair("price", Double.toString(this.price)));
        urlParameters.add(new BasicNameValuePair("trigger_price", "0"));
        urlParameters.add(new BasicNameValuePair("validity", "DAY"));
        urlParameters.add(new BasicNameValuePair("product", productType));
        urlParameters.add(new BasicNameValuePair("source", "web"));
        urlParameters.add(new BasicNameValuePair("order_tag", "order1"));
        
        
        post.setEntity(new UrlEncodedFormEntity(urlParameters));
        post.addHeader("Authorization", "Bearer "+secretToken);
        post.addHeader("Content-Type", "application/x-www-form-urlencoded");

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {

            System.out.println(EntityUtils.toString(response.getEntity()));
        }

    }

}