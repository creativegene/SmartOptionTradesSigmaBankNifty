
package com.smartoptiontrades.main;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.personal.HeikinAshi;
import org.ta4j.core.personal.MoneyFlowIndex;
import org.ta4j.core.personal.SuperTrend;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Quote;


public class OptionSigmaBNFPE_Buy implements Runnable{

	public void run() {
		
		boolean startPEModule = true;
		Properties prop = new GetPropertiesObject().retrieve();
		
		int quantity=Integer.parseInt(prop.getProperty("SIGMA_BNF_QTY"));
		int interval=Integer.parseInt(prop.getProperty("SIGMA_BNF_INTERVAL"));
		
		double target = Integer.parseInt(prop.getProperty("SIGMA_BNF_TARGET"));
		double stopLoss = Integer.parseInt(prop.getProperty("SIGMA_BNF_STOPLOSS"));		
		
		String instrumentPEPrimary="",instrumentPESecondary="";
		String instrumentIDPrimary="",instrumentIDSecondary="";
		String instrumentExIDPrimary="",instrumentExIDSecondary="";
		double PE_Secondary_Init_Price=0.0,PE_Primary_Init_Price=0.0,PE_Secondary_Final_Price=0.0,PE_Primary_Final_Price=0.0,PE_Price=0.0;//,Fut_Price=0.0;
		
		//boolean triggerEvaluated=false;
		//boolean triggerValidated = false;
		boolean bidaskValidation=false;
		boolean OISupportTrade = false;
		boolean PE_trade = Boolean.parseBoolean(prop.getProperty("SIGMA_BNF_PE_TRADE"));
		boolean isExpiryDay=false;
		
		if(PE_trade) {
			System.out.println(LocalDateTime.now()+" : PE Trade Activated");
		}else 
			System.out.println(LocalDateTime.now()+" : PE Trade De-Activated");
		
		double open=0,high=0,low=0,close=0;
		String startTime = "";
		
		TimeSeries series = new BaseTimeSeries("Series");
		
		DateTimeFormatter formatter_date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		
		KiteConnect kiteConnect=null;
		
		try {
			kiteConnect = new GetKiteConnect().retrieve();
		} catch (ClassNotFoundException | SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try {
			Class.forName(prop.getProperty("DB_CLASS_NAME"));
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		Connection con = null, con_sat=null, con_tdb=null;
		
		try {
			con = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
			con_sat = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING_SAT"),prop.getProperty("DB_USER_SAT"),prop.getProperty("DB_PASSWORD_SAT"));
			con_tdb = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING_TICKDATA"),prop.getProperty("DB_USER_TICKDATA"),prop.getProperty("DB_PASSWORD_TICKDATA"));
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		Statement stmt = null, stmt_sat=null, stmt_tdb=null;
		
		try {
			stmt = con.createStatement();
			stmt_sat = con_sat.createStatement();
			stmt_tdb = con_tdb.createStatement();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		HashMap<String, String> aliceUserMap = null;
		
		try {
			aliceUserMap = new GetAliceConnectUser().retrieve();
		} catch (ClassNotFoundException | SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		HashMap<String,KiteConnect> kiteUserMap = new HashMap<String,KiteConnect>();
		
		try {
			kiteUserMap = new GetKiteConnectUser().retrieve();
		} catch (ClassNotFoundException | SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		ResultSet rs = null;
		
		try {
			
			rs=stmt.executeQuery("Select count(*) from master_instrument_list where date_format(expiry,\"%Y-%m-%d\") = date_format(sysdate(),\"%Y-%m-%d\") and tradingSymbol like 'BANKNIFTY%';");
			
			while(rs.next()) {
				if(Integer.parseInt(rs.getString(1))>0) {
					isExpiryDay = true;
					System.out.println(LocalDateTime.now()+" : Expiry Day Option BidAsk Validation will be Skipped");
				}else {
					isExpiryDay = false;
				}
					
			}
		
			rs=stmt.executeQuery("Select name,instrumentID,exchangeToken from option_trade_instrument where ltp>="+Integer.parseInt(prop.getProperty("SIGMA_BNF_OPTION_PRICE"))+" and name like 'BANKNIFTY%00PE' order by ltp asc limit 1;");
			
			while(rs.next()) {
				
				instrumentPEPrimary=rs.getString(1);
				instrumentIDPrimary=rs.getString(2);
				instrumentExIDPrimary=rs.getString(3);
				
			}
			
			rs=stmt.executeQuery("Select name,instrumentID,exchangeToken from option_trade_instrument where ltp>="+Integer.parseInt(prop.getProperty("SIGMA_BNF_OPTION_PRICE"))+" and name like 'BANKNIFTY%00CE' order by ltp asc limit 1;");
			
			while(rs.next()) {
				
				instrumentPESecondary=rs.getString(1);
				instrumentIDSecondary=rs.getString(2);
				instrumentExIDSecondary=rs.getString(3);
				
			}
			
			System.out.println(LocalDateTime.now()+"| Option PE BUY Instrument | "+instrumentPEPrimary);
			
		}catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
		}
		
		while(startPEModule) {
			
			prop = new GetPropertiesObject().retrieve();
			LocalDateTime currentTime = LocalDateTime.now();
			
			if(OISupportTrade && bidaskValidation && PE_trade){
				
				ExecutorService executor = Executors.newFixedThreadPool(50);
				
				for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
					
					try {
						
						KiteConnect kiteConnection = (KiteConnect)entry.getValue();
			    	    
			    	    executor.execute(new ZerodhaEntryOrderPlacement(instrumentPEPrimary,quantity,"BUY","MIS",0.0,kiteConnection));
		    	    
					}catch(Exception e) {
						
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
					}
		    	    
		    	}
									
				for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
					
					try {
						
						executor.execute(new AliceOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentPEPrimary,"MIS",instrumentExIDPrimary,quantity,"BUY",0.0));
					
					}catch(Exception e) {
					
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma BUY Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						
					}
					
				}
					
				executor.shutdown();
					
				boolean orderPlaced=true;
					
				LocalDateTime orderTime = LocalDateTime.now();
					
				System.out.println(LocalDateTime.now()+" : PE Order Placed");
				
				try {
					PE_Primary_Init_Price=getLTP(kiteConnect, instrumentIDPrimary);			
					new RestTelegramCall().send("BUYING%20"+instrumentPEPrimary+"%20@%20"+PE_Primary_Init_Price);
				} catch (Exception | KiteException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				
				boolean orderFilled=true; //Since Market Order Placed
					
				while(orderPlaced) {
					
					prop = new GetPropertiesObject().retrieve();
					
					try {
						PE_Price=getLTP(kiteConnect, instrumentIDPrimary);
						//Fut_Price=getLTP(kiteConnect, prop.getProperty("SIGMA_BNF_FUT_ID"));
					} catch (IOException | KiteException e) {
						// TODO Auto-generated catch block
						StringWriter sw = new StringWriter();
			            e.printStackTrace(new PrintWriter(sw));
			            String exceptionAsString = sw.toString();
			            System.out.println(exceptionAsString);
			            
			            try {
			    			kiteConnect = new GetKiteConnect().retrieve();
			    		} catch (ClassNotFoundException | SQLException e1) {
			    			// TODO Auto-generated catch block
			    			System.out.println(LocalDateTime.now()+" : PE Error in re-initalizing Kite connect");
			    		}
					}
					
					target = Integer.parseInt(prop.getProperty("SIGMA_BNF_TARGET"));
					stopLoss = Integer.parseInt(prop.getProperty("SIGMA_BNF_STOPLOSS"));
					
					if(PE_Price>=PE_Primary_Init_Price+target || PE_Price<=PE_Primary_Init_Price-stopLoss || (LocalDateTime.now().getHour()==15 && LocalDateTime.now().getMinute()==15)) {
													
						executor = Executors.newFixedThreadPool(Integer.parseInt(prop.getProperty("THREAD_COUNT")));
						
						for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
							
							try {
								
								KiteConnect kiteConnection = (KiteConnect)entry.getValue();
					    	    
					    	    executor.execute(new ZerodhaExitOrderPlacement(instrumentPEPrimary,quantity,"SELL","MIS",0.0,kiteConnection));
				    	    
							}catch(Exception e) {
								
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							}
				    	    
				    	}
						
						for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
							
							try {
								
								executor.execute(new AliceExitOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentPEPrimary,"MIS",instrumentExIDPrimary,quantity,"SELL",0.0));
							
							}catch(Exception e) {
							
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								
							}
							
						}
						
						try {
							PE_Primary_Final_Price=getLTP(kiteConnect, instrumentIDPrimary);
							new RestTelegramCall().send("SELLING%20"+instrumentPEPrimary+"%20@%20"+PE_Primary_Final_Price);			
						} catch (Exception | KiteException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						System.out.println(LocalDateTime.now()+" : Order Exiting CE Initial Price="+PE_Primary_Init_Price+" | CE Current Price="+PE_Primary_Final_Price);
						
						executor.shutdown();

						orderPlaced=false;
						startPEModule=false;
						
						if(orderFilled) {
							try {
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Zeta Bank Nifty','"+instrumentPEPrimary+"','"+orderTime+"',"+PE_Primary_Init_Price+",'"+currentTime+"',"+PE_Primary_Final_Price+");");
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Zeta Bank Nifty','"+instrumentPESecondary+"','"+orderTime+"',"+PE_Secondary_Init_Price+",'"+currentTime+"',"+PE_Secondary_Final_Price+");");

							} catch (SQLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
					
					
			}
			
			if(LocalDateTime.now().getSecond()==15) {
				
				boolean bidaskValidationPE = false, bidaskValidationCE = false, bidaskValidationFut=false;
				
				try {
					
					//System.out.println("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentIDPrimary+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
				
					rs = stmt_tdb.executeQuery("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentIDPrimary+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))>1 || isExpiryDay) {
							
							bidaskValidationPE=true;
							//System.out.println(LocalDateTime.now()+" : PE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationPE);
							
						}else {
							
							bidaskValidationPE=false;
							//System.out.println(LocalDateTime.now()+" : PE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationPE);
						}
						
					}
					
					rs = stmt_tdb.executeQuery("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentIDSecondary+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))<1 || isExpiryDay) {
							
							bidaskValidationCE=true;
							//System.out.println(LocalDateTime.now()+" : CE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationCE);
							
						}else {
							
							bidaskValidationCE=false;
							//System.out.println(LocalDateTime.now()+" : CE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationCE);
						}
						
					}
					
					rs = stmt_tdb.executeQuery("Select avg(bid_qty/ask_qty) from tickdata where id="+prop.getProperty("SIGMA_BNF_FUT_ID")+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))<1) {
							
							bidaskValidationFut=true;
							//System.out.println(LocalDateTime.now()+" : Fut Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationFut);
							
						}else {
							
							bidaskValidationFut=false;
							//System.out.println(LocalDateTime.now()+" : Fut Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationFut);
						}
						
					}
					
					if(bidaskValidationCE && bidaskValidationFut) {
						bidaskValidation=true;
					}else {
						bidaskValidation=false;
					}
					
					System.out.println(LocalDateTime.now()+" PE BidAsk Data | CE "+bidaskValidationCE+" | PE "+bidaskValidationPE+" | FUT "+bidaskValidationFut+" | PE Long Validation "+bidaskValidation);
					
					//bidaskValidation=true;
					//bidaskValidationCompleted=true;		                	
	            
				}catch(Exception e) {
					
					e.printStackTrace();
					System.out.println(LocalDateTime.now()+" : Error in tickdata connection");
					
				}
				
			}
			
			if(LocalDateTime.now().getMinute()%5==0 && LocalDateTime.now().getSecond()==30) {
				
				int countCEPoint=0,countPEPoint=0;
				//OIAnalysisCompleted=true;
				
				try {
					rs=stmt.executeQuery("Select 5min,10min,15min,'NA' from option_oi_analysis where instrument='BANKNIFTY' order by timestamp desc limit 1;");
					
					
					while(rs.next()) {
						
						System.out.println(LocalDateTime.now()+"|"+rs.getString(1)+"|"+rs.getString(2)+"|"+rs.getString(3)+"|"+rs.getString(4));
						
						if(rs.getString(1).equalsIgnoreCase("Long")) {
							countCEPoint++;
						}else if(rs.getString(1).equalsIgnoreCase("Short")){
							countPEPoint++;
						}
						
						if(rs.getString(2).equalsIgnoreCase("Long")) {
							countCEPoint++;
						}else if(rs.getString(2).equalsIgnoreCase("Short")){
							countPEPoint++;
						}
						
						if(rs.getString(3).equalsIgnoreCase("Long")) {
							countCEPoint++;
						}else if(rs.getString(3).equalsIgnoreCase("Short")){
							countPEPoint++;
						}
						
						if(rs.getString(4).equalsIgnoreCase("Long")) {
							countCEPoint++;
						}else if(rs.getString(4).equalsIgnoreCase("Short")){
							countPEPoint++;
						}
							
					}
						
					} catch (SQLException e) {
						StringWriter sw = new StringWriter();
			            e.printStackTrace(new PrintWriter(sw));
			            String exceptionAsString = sw.toString();
			            System.out.println(exceptionAsString);
					}
					
				
				
				//System.out.println(LocalDateTime.now()+" PE Point = "+countPEPoint);
				//System.out.println(LocalDateTime.now()+" PE Point = "+countPEPoint);
				
				if(countPEPoint>countCEPoint) {
					OISupportTrade=true;
					System.out.println(LocalDateTime.now()+" : OI Support PE Trade");
				}else {
					OISupportTrade=false;
					System.out.println(LocalDateTime.now()+" : OI Does Not Support PE Trade");
				}
				
			}
			
			if(LocalDateTime.now().getSecond()==45) {
				
				/*				
				try {
					aliceUserMap = new GetAliceConnectUser().retrieve();
				} catch (ClassNotFoundException | SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				*/
			}
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if(LocalDateTime.now().getHour()==15 && LocalDateTime.now().getMinute()>=10) {
				
				startPEModule=false;
				
			}
			
		}
		
		try {
			con.close();
			con_sat.close();
			con_tdb.close();
		}catch(Exception e) {
			System.out.println(LocalDateTime.now()+" : Error in closing connection");
		}
			
	}

	
	
	public static double getLTP(KiteConnect kiteConnect, String instrumentIDPrimary) throws KiteException, IOException {
        String[] instruments = {instrumentIDPrimary};
        return kiteConnect.getLTP(instruments).get(instrumentIDPrimary).lastPrice;
    }

}
