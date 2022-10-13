package com.smartoptiontrades.main;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;

public class ZerodhaCancelLimitOrder implements Runnable{
	
	String instrument;
	String action;
	KiteConnect kiteConnectUser;
	
	
	
	public ZerodhaCancelLimitOrder(String instrument,KiteConnect kiteCon){
		this.instrument=instrument;
		//this.action=transactionType;
		this.kiteConnectUser=kiteCon;
		
	}
	
	
	
	public void run(){
		
		Order order=null;		
			
		try {
			    
		        List<Order> orders = kiteConnectUser.getOrders();
		        
		        for(int i = 0; i< orders.size(); i++){
		        	
		        	if((orders.get(i).status.equalsIgnoreCase("OPEN")||orders.get(i).status.equalsIgnoreCase("TRIGGER PENDING")) && orders.get(i).tradingSymbol.equalsIgnoreCase(instrument)) {
		        		//System.out.println(orders.get(i).tradingSymbol+" "+orders.get(i).status+" "+orders.get(i).orderId+" "+orders.get(i).parentOrderId+" "+orders.get(i).orderType+" "+orders.get(i).averagePrice+" "+orders.get(i).exchangeTimestamp);
		        		order = kiteConnectUser.cancelOrder(orders.get(i).orderId,Constants.VARIETY_REGULAR);
		                System.out.println("Exiting Order for "+instrument+" "+order.orderId+" for "+kiteConnectUser.getUserId());
		                
		        	}
		        	
		        }
		     	
		}catch(Exception|KiteException e){
			e.printStackTrace();
			System.out.println(LocalDateTime.now()+" : Error in Cancel Limit Order for user "+kiteConnectUser.getUserId()+" "+e.getMessage());
			
		} 
		
	}
}
