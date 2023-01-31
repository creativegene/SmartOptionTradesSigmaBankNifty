
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


public class OptionAlphaBNFCE implements Runnable{

	public void run() {
		
		boolean startCEModule = true;
		Properties prop = new GetPropertiesObject().retrieve();
		
		int quantity=Integer.parseInt(prop.getProperty("ALPHA_NF_QTY"));
		int interval=Integer.parseInt(prop.getProperty("ALPHA_NF_INTERVAL"));
		
		double target = Integer.parseInt(prop.getProperty("ALPHA_NF_TARGET"));
		double stopLoss = Integer.parseInt(prop.getProperty("ALPHA_NF_STOPLOSS"));		
		
		String instrument="",instrumentCE="";
		String instrumentID="";
		String instrumentExID="";
		double CE_Init_Price=0.0,CE_Price=0.0,Fut_Price=0.0;
		
		boolean triggerEvaluated=false;
		boolean triggerValidated = false;
		boolean bidaskValidation=false;
		boolean bidaskValidationCompleted = false;
		
		boolean OIAnalysisCompleted = false;
		boolean OISupportTrade = false;
		boolean ce_trade = Boolean.getBoolean(prop.getProperty("ALPHA_NF_CE_TRADE"));
		
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
		
			rs=stmt.executeQuery("Select name,instrumentId,exchangeToken from option_trade_instrument where ltp>="+Integer.parseInt(prop.getProperty("ALPHA_NF_OPTION_PRICE"))+" and name like 'NIFTY%00CE' order by ltp asc limit 1;");
			
			while(rs.next()) {
				
				instrument=rs.getString(1);
				instrumentCE=rs.getString(1);
				instrumentID=rs.getString(2);
				instrumentExID=rs.getString(3);
				
			}
			
			System.out.println(LocalDateTime.now()+"| Option CE Instrument | "+instrument);
			
		}catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
		}
		
		while(startCEModule) {
			
			prop = new GetPropertiesObject().retrieve();
			LocalDateTime currentTime = LocalDateTime.now();
			//System.out.println(LocalDateTime.now()+" : System CE Running");
			if(!triggerEvaluated) {	
				
				String dateStrStart = currentTime.format(formatter_date).toString()+" "+prop.getProperty("ALPHA_NF_START_TIME");
				String dateStrEnd = currentTime.format(formatter_date).toString()+" "+prop.getProperty("ALPHA_NF_END_TIME");
				
				System.out.println("Start Time : "+dateStrStart);
				System.out.println("End Time : "+dateStrEnd);
								
				series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,prop.getProperty("ALPHA_NF_FUT_ID"),"1minute");
				
				for(int i=0;i<series.getBarCount();i++) {
					
					/*
					System.out.println(series.getBar(i).getBeginTime()+"|"+series.getBar(i).getOpenPrice()+"|"+series.getBar(i).getMaxPrice()
							+"|"+series.getBar(i).getMinPrice()+"|"+series.getBar(i).getClosePrice());
					*/
					if(i==0) {
						startTime=series.getBar(i).getBeginTime().format(formatter);
						open=series.getBar(i).getOpenPrice().doubleValue();
						high=series.getBar(i).getMaxPrice().doubleValue();
						low=series.getBar(i).getMinPrice().doubleValue();
						close=series.getBar(i).getClosePrice().doubleValue();
					}
					
					if(series.getBar(i).getMaxPrice().doubleValue()>high) {
						high=series.getBar(i).getMaxPrice().doubleValue();
					}
					if(series.getBar(i).getMinPrice().doubleValue()<low) {
						low=series.getBar(i).getMinPrice().doubleValue();
					}
					
					close=series.getBar(i).getClosePrice().doubleValue();
				}
				System.out.println("------------------NIFTY FUTURE "+prop.getProperty("ALPHA_NF_START_TIME")+" "+prop.getProperty("ALPHA_NF_INTERVAL")+"min Candlestick------------------");
				System.out.println(startTime+"|"+open+"|"+high+"|"+low+"|"+close);
				triggerEvaluated=true;
			}
			
			if(triggerEvaluated && ce_trade) {
				
				try {
					Fut_Price=getLTP(kiteConnect, prop.getProperty("ALPHA_NF_FUT_ID"));
				} catch (IOException | KiteException e) {
					// TODO Auto-generated catch block
					// TODO Auto-generated catch block
					StringWriter sw = new StringWriter();
		            e.printStackTrace(new PrintWriter(sw));
		            String exceptionAsString = sw.toString();
		            System.out.println(exceptionAsString);
				}
				if(Fut_Price>=high) {
					triggerValidated=true;
					try {
						CE_Init_Price=getLTP(kiteConnect, instrumentID);
						System.out.println(LocalDateTime.now()+" : CE Trade Triggered Fut Price="+Fut_Price+" CE Price="+CE_Init_Price);
					} catch (IOException | KiteException e) {
						// TODO Auto-generated catch block
						// TODO Auto-generated catch block
						StringWriter sw = new StringWriter();
			            e.printStackTrace(new PrintWriter(sw));
			            String exceptionAsString = sw.toString();
			            System.out.println(exceptionAsString);
					}
				}
			}
			
			if(triggerValidated && OISupportTrade && bidaskValidation && ce_trade){
				
				ExecutorService executor = Executors.newFixedThreadPool(50);
				
				//double entryPrice = series.getBar(barCount-1).getClosePrice().doubleValue();
				
				for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
					
					try {
						
						KiteConnect kiteConnection = (KiteConnect)entry.getValue();
			    	    
			    	    executor.execute(new ZerodhaEntryOrderPlacement(instrumentCE,quantity,"BUY","NRML",CE_Init_Price,kiteConnection));
		    	    
					}catch(Exception e) {
						
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
					}
		    	    
		    	}
									
				for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
					
					try {
						
						executor.execute(new AliceEntryOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentCE,"NRML",instrumentExID,quantity,"BUY",CE_Init_Price));
					
					}catch(Exception e) {
					
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma BUY Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						
					}
					
				}
					
				executor.shutdown();
					
				boolean orderPlaced=true;
					
				LocalDateTime orderTime = LocalDateTime.now();
					
				System.out.println(LocalDateTime.now()+" : CE Order Placed");
				
				try {
					new RestTelegramCall().send("BUYING%20"+instrumentCE+"%20@%20"+CE_Init_Price);
				} catch (Exception e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				
				boolean orderFilled=false;
					
				while(orderPlaced) {
					
					prop = new GetPropertiesObject().retrieve();
					
					try {
						CE_Price=getLTP(kiteConnect, instrumentID);
						Fut_Price=getLTP(kiteConnect, prop.getProperty("ALPHA_NF_FUT_ID"));
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
			    			System.out.println(LocalDateTime.now()+" : CE Error in re-initalizing Kite connect");
			    		}
					}
					
					if(CE_Price<CE_Init_Price && !orderFilled) {
						
						try {
							new RestTelegramCall().sendUpdate("ORDER EXECUTED%20"+instrumentCE+"%20@%20"+CE_Init_Price);
						} catch (Exception e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						
						orderFilled=true;
					}
					
					target = Integer.parseInt(prop.getProperty("ALPHA_NF_TARGET"));
					stopLoss = Integer.parseInt(prop.getProperty("ALPHA_NF_STOPLOSS"));
					
					if(Fut_Price>=high+target || Fut_Price<=high-stopLoss || Fut_Price<low || (LocalDateTime.now().getHour()==15 && LocalDateTime.now().getMinute()==15)) {
													
						executor = Executors.newFixedThreadPool(Integer.parseInt(prop.getProperty("THREAD_COUNT")));
						
						for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
							
							try {
								
								KiteConnect kiteConnection = (KiteConnect)entry.getValue();
					    	    
					    	    executor.execute(new ZerodhaExitOrderPlacement(instrumentCE,quantity,"SELL","NRML",0.0,kiteConnection));
				    	    
							}catch(Exception e) {
								
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							}
				    	    
				    	}
						
						for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
							
							try {
								
								executor.execute(new AliceExitOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentCE,"NRML",instrumentExID,quantity,"SELL",0.0));
							
							}catch(Exception e) {
							
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								
							}
							
						}
						
						for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
							
							try {
								
								KiteConnect kiteConnection = (KiteConnect)entry.getValue();
					    	    
					    	    executor.execute(new ZerodhaCancelLimitOrder(instrumentCE,kiteConnection));
				    	    
							}catch(Exception e) {
								
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							}
				    	    
				    	}
						
						for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
							
							try {
								
								executor.execute(new AliceCancelLimitOrder((String)entry.getKey(),(String)entry.getValue(),instrumentCE));
							
							}catch(Exception e) {
							
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Alice Cancel Order Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								
							}
							
						}
						
						System.out.println(LocalDateTime.now()+" : Order Exiting CE Initial Price="+CE_Init_Price+" | CE Current Price="+CE_Price);
						
						executor.shutdown();

						orderPlaced=false;
						startCEModule=false;
						
						try {
							new RestTelegramCall().send("SELLING%20"+instrumentCE+"%20@%20"+CE_Price);
						} catch (Exception e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						
						if(orderFilled) {
							try {
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Alpha Nifty','"+instrument+"','"+orderTime+"',"+CE_Init_Price+",'"+currentTime+"',"+CE_Price+");");
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
				
				try {
					
					//System.out.println("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentID+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
				
					rs = stmt_tdb.executeQuery("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentID+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))>=1) {
							
							bidaskValidation=true;
							System.out.println(LocalDateTime.now()+" : CE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidation);
							
						}else {
							
							bidaskValidation=false;
							System.out.println(LocalDateTime.now()+" : CE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidation);
						}
						
					}
					
					//bidaskValidation=true;
					//bidaskValidationCompleted=true;		                	
	            
				}catch(Exception e) {
					
					e.printStackTrace();
					System.out.println(LocalDateTime.now()+" : Error in tickdata connection");
					
				}
				
			}
			
			if(LocalDateTime.now().getSecond()==30 && ce_trade) {
				
				int countCEPoint=0,countPEPoint=0;
				//OIAnalysisCompleted=true;
				
				try {
					rs=stmt.executeQuery("Select 5min,10min,15min,'NA' from option_oi_analysis where instrument='NIFTY' order by timestamp desc limit 1;");
					
					
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
					
				
				
				System.out.println(LocalDateTime.now()+" CE Point = "+countCEPoint);
				//System.out.println(LocalDateTime.now()+" PE Point = "+countPEPoint);
				
				if(countCEPoint>countPEPoint) {
					OISupportTrade=true;
					System.out.println(LocalDateTime.now()+" : OI Support CE Trade");
				}else {
					OISupportTrade=false;
					System.out.println(LocalDateTime.now()+" : OI Does Not Support CE Trade");
				}
				
			}
			
			if(LocalDateTime.now().getSecond()==45 && ce_trade) {
				
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
				
				startCEModule=false;
				
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

	
	
	public static double getLTP(KiteConnect kiteConnect, String instrumentId) throws KiteException, IOException {
        String[] instruments = {instrumentId};
        return kiteConnect.getLTP(instruments).get(instrumentId).lastPrice;
    }

}
