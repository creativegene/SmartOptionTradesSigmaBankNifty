package com.smartoptiontrades.main;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDateTime;

public class AliceCancelLimitOrder implements Runnable {

    // one instance, reuse
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private String userId,secretToken,instrumentName;
    
    AliceCancelLimitOrder(String userId,String secretToken,String instrumentName){
    	
    	this.userId=userId;
    	this.secretToken=secretToken;
    	this.instrumentName=instrumentName;
    	
    }

    public void run() {

    		try {
				searchBracketOrdertoCancel(secretToken,instrumentName);
			} catch (Exception e) {
				System.out.println(LocalDateTime.now()+" : Error in Bracket Order with error "+e.getMessage());
				e.printStackTrace();
			}
            
         
    }


    private void searchBracketOrdertoCancel(String secretToken,String instrumentName) throws Exception {

        HttpGet request = new HttpGet("https://ant.aliceblueonline.com/api/v2/order");

        // add request headers
        request.addHeader("Authorization", "Bearer "+secretToken);

        try (CloseableHttpResponse response = httpClient.execute(request)) {

            HttpEntity entity = response.getEntity();
            Header headers = entity.getContentType();

            if (entity != null) {
            	
                String result = EntityUtils.toString(entity);
                
                JsonElement jelement = new JsonParser().parse(result);
                JsonObject  jobject = jelement.getAsJsonObject();
                jobject = jobject.getAsJsonObject("data");
                JsonArray jarray = jobject.getAsJsonArray("pending_orders");
                for(JsonElement job:jarray) {
                	JsonObject  jo = job.getAsJsonObject();
                	if(jo.get("trading_symbol").getAsString().equalsIgnoreCase(instrumentName) && jo.get("product").getAsString().equalsIgnoreCase("MIS") && jo.get("order_type").getAsString().equalsIgnoreCase("LIMIT")){
                		
                		System.out.println(jo.get("client_id").getAsString()+"-"+jo.get("oms_order_id").getAsString()+"-"+jo.get("leg_order_indicator").getAsString()+"-"+jo.get("order_type").getAsString());
                		if(jo.get("leg_order_indicator").getAsString().equalsIgnoreCase(""))
                			sendCancelOrder(secretToken,jo.get("oms_order_id").getAsString());
                		else
                			sendCancelOrder(secretToken,jo.get("leg_order_indicator").getAsString());
                	}
                	
                }
            }
            
            

        }

    }
    
    private void sendCancelOrder(String secretToken,String orderId) throws Exception {

        HttpDelete request = new HttpDelete("https://ant.aliceblueonline.com/api/v2/order?oms_order_id="+orderId+"&order_status=open");

        // add request headers
        request.addHeader("Authorization", "Bearer "+secretToken);
        //request.addHeader(HttpHeaders.USER_AGENT, "Googlebot");

        try (CloseableHttpResponse response = httpClient.execute(request)) {


            HttpEntity entity = response.getEntity();
            Header headers = entity.getContentType();

            if (entity != null) {
                // return it as a String
                String result = EntityUtils.toString(entity);
                System.out.println(result);
            }

        }

    }

    

}