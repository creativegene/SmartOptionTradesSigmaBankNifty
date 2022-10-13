package com.smartoptiontrades.main;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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

public class ZerodhaEntryOrderPlacement implements Runnable{
	
	String instrument;
	int quantity;
	String action;
	KiteConnect kiteConnectUser;
	String orderType;
	double price;
	boolean retry;
	
	public ZerodhaEntryOrderPlacement(String instrument,int quantity,String action, String orderType,double price,KiteConnect kiteCon){
		this.instrument=instrument;
		this.quantity=quantity;
		this.action=action;
		this.kiteConnectUser=kiteCon;
		this.price=price;
		this.retry=true;
		this.orderType=orderType;
	}
	
	public ZerodhaEntryOrderPlacement(String instrument,int quantity,String action, String orderType,KiteConnect kiteCon, boolean recursive){
		this.instrument=instrument;
		this.quantity=quantity;
		this.action=action;
		this.kiteConnectUser=kiteCon;
		this.retry=recursive;
		this.orderType=orderType;
	}
	
	public void run(){
		
		Boolean tradeon = false,placeOrder=true;
		Order order=null;
		String orderId="";
		Properties prop = new GetPropertiesObject().retrieve();
		
		System.out.println(LocalDateTime.now()+" : Placing "+action+" order for "+instrument+" Quantity = "+quantity+" for "+kiteConnectUser.getUserId());
					
		try {
			
				Class.forName(prop.getProperty("DB_CLASS_NAME"));
				Connection con = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
		
		    	Statement stmt = con.createStatement();
		    	
		    	/*
		    	ResultSet rs = stmt.executeQuery("Select gamma_lot from user_token_db where userid ='"+kiteConnectUser.getUserId()+"';");
		    	
		    	while(rs.next()) {
		        	
					if(!prop.getProperty("FORCE_LIMIT_QTY").equalsIgnoreCase("Y")) {
			    	
						quantity = quantity * Integer.parseInt(rs.getString(1));
				
					}
				}
		    	*/
	    	
			    OrderParams orderParams = new OrderParams();
			    orderParams.quantity = quantity;
			    if(price>0) {
			    	orderParams.orderType = Constants.ORDER_TYPE_LIMIT;
			    }else {
			    	orderParams.orderType = Constants.ORDER_TYPE_MARKET;
			    }
			    orderParams.tradingsymbol = instrument;
			    
			    if(orderType.equalsIgnoreCase("MIS"))
			    	orderParams.product = Constants.PRODUCT_MIS;
			    else if(orderType.equalsIgnoreCase("NRML"))
			    	orderParams.product = Constants.PRODUCT_NRML;
			    
			    orderParams.exchange = Constants.EXCHANGE_NFO;
			    
			    if(action.equalsIgnoreCase("BUY"))
			    	orderParams.transactionType = Constants.TRANSACTION_TYPE_BUY;
			    else if(action.equalsIgnoreCase("SELL"))
			    	orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
			    
			    orderParams.validity = Constants.VALIDITY_DAY;
			    orderParams.price = price;
			    orderParams.triggerPrice = 0.0;
			    orderParams.tag = "myTag"; //tag is optional and it cannot be more than 8 characters and only alphanumeric is allowed
			    
			    //System.out.println(orderParams.toString());
			    try {
			    	order = kiteConnectUser.placeOrder(orderParams, Constants.VARIETY_REGULAR);
			    }catch(InputException e) {
			    	System.out.println("Zerodha Entry : Input Exception "+e.message);
			    }
				
				
				orderId=order.orderId;
				
				try {
					
					stmt.executeUpdate("Insert into algotrade.order_detail (userId,orderId,timeStamp) values ('"+kiteConnectUser.getUserId()+"',"+order.orderId+",sysdate());");
					
					con.close();
						
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Stratgey LTPValue Retrieve Block!!!!!!!!!!!!!!!");
					System.out.println(LocalDateTime.now()+" : "+e.getMessage());
					System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
					
				}
					
				
				if(!orderId.isEmpty()){
					retry=false;
					
				}else{
					
					System.out.println(LocalDateTime.now()+" : "+kiteConnectUser.getUserId().toUpperCase()+" "+"Order for "+instrument+" Failed");
					new RestTelegramCall().sendUpdate(kiteConnectUser.getUserId().toUpperCase()+" "+order.tradingSymbol+" Order Id NULL - Failed");
					
				}
				
		}catch(Exception|KiteException e){
			
			try {
				new RestTelegramCall().sendUpdate(kiteConnectUser.getUserId().toUpperCase()+" "+" Entry Order Placement "+instrument+" in ERROR");
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			System.out.println(LocalDateTime.now()+" : Error for user "+kiteConnectUser.getUserId()+" "+e.getMessage());

		} 
		
	}
	
}
