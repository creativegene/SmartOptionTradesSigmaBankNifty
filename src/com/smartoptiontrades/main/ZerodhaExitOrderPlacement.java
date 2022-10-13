package com.smartoptiontrades.main;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.json.JSONException;

import com.neovisionaries.ws.client.WebSocketException;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.InputException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;

public class ZerodhaExitOrderPlacement implements Runnable{
	
	String instrument;
	int quantity;
	String action;
	String orderType;
	KiteConnect kiteConnectUser;
	double triggerPrice;
	boolean retry;
	
	public ZerodhaExitOrderPlacement(String instrument,int quantity,String action,String orderType,double triggerPrice,KiteConnect kiteCon){
		this.instrument=instrument;
		this.quantity=quantity;
		this.action=action;
		this.orderType=orderType;
		this.kiteConnectUser=kiteCon;
		this.triggerPrice=triggerPrice;
		this.retry=true;
	}
	
	public ZerodhaExitOrderPlacement(String instrument,int quantity,String action,KiteConnect kiteCon,boolean recursive){
		this.instrument=instrument;
		this.quantity=quantity;
		this.action=action;
		this.kiteConnectUser=kiteCon;
		this.retry=recursive;
	}
	
	public void run(){
		
		Boolean tradeon = false,placeOrder=true;
		Order order=null;
		String orderId="";
		Properties prop = new GetPropertiesObject().retrieve();
		
		System.out.println(LocalDateTime.now()+" : Placing "+action+" order for "+instrument+" for "+kiteConnectUser.getUserId());
		
			
		try {
				
				int buyQty=0,sellQty=0;
				
				for(Order odr:kiteConnectUser.getOrders()){
					
					if(odr.tradingSymbol.equalsIgnoreCase(instrument)){
						if(odr.product.equalsIgnoreCase(orderType) && odr.transactionType.equalsIgnoreCase("BUY") && odr.status.equalsIgnoreCase("COMPLETE")){
							buyQty = buyQty+Integer.parseInt(odr.quantity);
						}
						if(odr.product.equalsIgnoreCase(orderType) && odr.transactionType.equalsIgnoreCase("SELL") && odr.status.equalsIgnoreCase("COMPLETE")){
							sellQty = sellQty+Integer.parseInt(odr.quantity);
						}
					}
				}
				
				if(buyQty!=sellQty && sellQty>buyQty && action.equalsIgnoreCase("BUY")){
					
				    OrderParams orderParams = new OrderParams();
				    /*
				    if(sellQty-buyQty >= quantity)
				    	orderParams.quantity = quantity;
				    else
				    	orderParams.quantity = sellQty-buyQty;
				    */
				    
				    orderParams.quantity = sellQty-buyQty;
				    orderParams.orderType = Constants.ORDER_TYPE_MARKET;
				    orderParams.tradingsymbol = instrument;
				    if(orderType.equalsIgnoreCase("MIS"))
				    	orderParams.product = Constants.PRODUCT_MIS;
				    else if(orderType.equalsIgnoreCase("NRML"))
				    	orderParams.product = Constants.PRODUCT_NRML;
				    orderParams.exchange = Constants.EXCHANGE_NFO;
				    orderParams.transactionType = Constants.TRANSACTION_TYPE_BUY;
				    orderParams.validity = Constants.VALIDITY_DAY;
				    orderParams.price = 0.0;
				    orderParams.triggerPrice = 0.0;
				    orderParams.tag = "myTag"; //tag is optional and it cannot be more than 8 characters and only alphanumeric is allowed
				    
				    System.out.println("Quantity = "+quantity);
				    
					order = kiteConnectUser.placeOrder(orderParams, Constants.VARIETY_REGULAR);
					
					orderId=order.orderId;
					
					try {
			    		
				    	Class.forName(prop.getProperty("DB_CLASS_NAME"));
						Connection con = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
				    	
				    	Statement stmt = con.createStatement();
						
						stmt.executeUpdate("Insert into algotrade.order_detail (userId,orderId,timeStamp) values ('"+kiteConnectUser.getUserId()+"',"+order.orderId+",sysdate());");
						
						con.close();
						//System.out.println("Ending SQL Call "+LocalDateTime.now());
						//System.out.println(value);
							
					} catch (SQLException | ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Stratgey LTPValue Retrieve Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						
					}
				
				}else if(buyQty!=sellQty && buyQty>sellQty && action.equalsIgnoreCase("SELL")){
					
					OrderParams orderParams = new OrderParams();
				    /*
					if(buyQty-sellQty >= quantity)
						orderParams.quantity = quantity;
					else
						orderParams.quantity = buyQty-sellQty;
					*/
					
					orderParams.quantity = buyQty-sellQty;
				    orderParams.orderType = Constants.ORDER_TYPE_MARKET;
				    orderParams.tradingsymbol = instrument;
				    if(orderType.equalsIgnoreCase("MIS"))
				    	orderParams.product = Constants.PRODUCT_MIS;
				    else if(orderType.equalsIgnoreCase("NRML"))
				    	orderParams.product = Constants.PRODUCT_NRML;
				    orderParams.exchange = Constants.EXCHANGE_NFO;
				    orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
				    orderParams.validity = Constants.VALIDITY_DAY;
				    orderParams.price = 0.0;
				    orderParams.triggerPrice = 0.0;
				    orderParams.tag = "myTag"; //tag is optional and it cannot be more than 8 characters and only alphanumeric is allowed
				    
				    try {
				    	order = kiteConnectUser.placeOrder(orderParams, Constants.VARIETY_REGULAR);
				    }catch(InputException e) {
				    	System.out.println("Zerodha Exit : Input Exception "+e.message);
				    }
					
					orderId=order.orderId;
					
					try {
			    		
				    	Class.forName(prop.getProperty("DB_CLASS_NAME"));
						Connection con = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
				    	
				    	Statement stmt = con.createStatement();
						
						stmt.executeUpdate("Insert into algotrade.order_detail (userId,orderId,timeStamp) values ('"+kiteConnectUser.getUserId()+"',"+order.orderId+",sysdate());");
						
						con.close();
						//System.out.println("Ending SQL Call "+LocalDateTime.now());
						//System.out.println(value);
							
					} catch (SQLException | ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Stratgey LTPValue Retrieve Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						
					}
				}
				
				else{
					
					System.out.println(LocalDateTime.now()+" : "+kiteConnectUser.getUserId().toUpperCase()+" "+" Squared Off "+instrument+" order already");
					new RestTelegramCall().sendUpdate(kiteConnectUser.getUserId().toUpperCase()+" "+" Squared Off "+instrument+" order already");
					
				}
				
				
				
				if(!orderId.isEmpty()){
					
					retry=false;
					
				}else{
					
					
				}
				
				//Thread.sleep(2000);
				
		}catch(Exception|KiteException e){
			//System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!! Exit Order Placement Exception Block A2!!!!!!!!!!!!!!!");
			try {
				new RestTelegramCall().sendUpdate(kiteConnectUser.getUserId().toUpperCase()+" "+" Exit Order Placement "+instrument+" in ERROR");
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			System.out.println(LocalDateTime.now()+" : Error for user "+kiteConnectUser.getUserId()+" "+e.getMessage());
			
			
		} 
		
	}
	
}
