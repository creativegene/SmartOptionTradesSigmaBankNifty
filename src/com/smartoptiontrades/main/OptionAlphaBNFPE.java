
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


public class OptionAlphaBNFPE implements Runnable{

	public void run() {
		
		boolean startPEModule = true;
		Properties prop = new GetPropertiesObject().retrieve();
		
		int quantity=Integer.parseInt(prop.getProperty("ALPHA_BNF_QTY"));
		int interval=Integer.parseInt(prop.getProperty("ALPHA_BNF_INTERVAL"));
		
		double target = Integer.parseInt(prop.getProperty("ALPHA_BNF_TARGET"));
		double stopLoss = Integer.parseInt(prop.getProperty("ALPHA_BNF_STOPLOSS"));
		
		String instrument="",instrumentPE="";
		String instrumentID="";
		String instrumentExID="";
		double PE_Init_Price=0.0,PE_Price=0.0,Fut_Price=0.0;
		
		boolean triggerEvaluated=false;
		boolean triggerValidated = false;
		boolean bidaskValidation=true;
		boolean bidaskValidationCompleted = false;
		
		boolean OIAnalysisCompleted = false;
		boolean OISupportTrade = false;
		boolean pe_trade = Boolean.parseBoolean(prop.getProperty("ALPHA_BNF_PE_TRADE"));
		
		if(pe_trade) {
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
		
			rs=stmt.executeQuery("Select name,instrumentId,exchangeToken from option_trade_instrument where ltp>="+Integer.parseInt(prop.getProperty("ALPHA_BNF_OPTION_PRICE"))+" and name like 'BANKNIFTY%00PE' order by ltp asc limit 1;");
			
			while(rs.next()) {
				
				instrument=rs.getString(1);
				instrumentPE=rs.getString(1);
				instrumentID=rs.getString(2);
				instrumentExID=rs.getString(3);
				
			}
			
			System.out.println(LocalDateTime.now()+"| Option PE Instrument | "+instrument);
			
		}catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
		}
		
		while(startPEModule) {
			
			prop = new GetPropertiesObject().retrieve();
			LocalDateTime currentTime = LocalDateTime.now();
			//System.out.println(LocalDateTime.now()+" : System PE Running");
			if(!triggerEvaluated) {	
				
				String dateStrStart = currentTime.format(formatter_date).toString()+" "+prop.getProperty("ALPHA_BNF_START_TIME");
				String dateStrEnd = currentTime.format(formatter_date).toString()+" "+prop.getProperty("ALPHA_BNF_END_TIME");
				
				System.out.println("Start Time : "+dateStrStart);
				System.out.println("End Time : "+dateStrEnd);
								
				series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,prop.getProperty("ALPHA_BNF_FUT_ID"),"1minute");
				
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
				System.out.println("------------------BANK NIFTY FUTURE "+prop.getProperty("ALPHA_BNF_START_TIME")+" "+prop.getProperty("ALPHA_BNF_INTERVAL")+"min Candlestick PE------------------");
				System.out.println(startTime+"|"+open+"|"+high+"|"+low+"|"+close);
				triggerEvaluated=true;
			}
			
			if(!triggerValidated && triggerEvaluated && bidaskValidation && pe_trade) {
				
				try {
					Fut_Price=getLTP(kiteConnect, prop.getProperty("ALPHA_BNF_FUT_ID"));
				} catch (IOException | KiteException e) {
					// TODO Auto-generated catch block
					// TODO Auto-generated catch block
					StringWriter sw = new StringWriter();
		            e.printStackTrace(new PrintWriter(sw));
		            String exceptionAsString = sw.toString();
		            System.out.println(exceptionAsString);
				}
				if(Fut_Price<=low) {
					triggerValidated=true;
					try {
						PE_Init_Price=getLTP(kiteConnect, instrumentID);
						System.out.println(LocalDateTime.now()+" : PE Trade Triggered Fut Price="+Fut_Price+" PE Price="+PE_Init_Price);
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
			
			if(triggerValidated && bidaskValidation && pe_trade){
				
				ExecutorService executor = Executors.newFixedThreadPool(50);
				
				//double entryPrice = series.getBar(barCount-1).getClosePrice().doubleValue();
				
				for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
					
					try {
						
						KiteConnect kiteConnection = (KiteConnect)entry.getValue();
			    	    
			    	    executor.execute(new ZerodhaEntryOrderPlacement(instrumentPE,quantity,"BUY","MIS",PE_Init_Price,kiteConnection));
		    	    
					}catch(Exception e) {
						
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
					}
		    	    
		    	}
									
				for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
					
					try {
						
						executor.execute(new AliceOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentPE,"MIS",instrumentExID,quantity,"BUY",PE_Init_Price));
					
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
					new RestTelegramCall().send("BUYING%20"+instrumentPE+"%20@%20"+PE_Init_Price);
				} catch (Exception e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				
				boolean orderFilled=false;
					
				while(orderPlaced) {
					
					prop = new GetPropertiesObject().retrieve();
					
					try {
						PE_Price=getLTP(kiteConnect, instrumentID);
						Fut_Price=getLTP(kiteConnect, prop.getProperty("ALPHA_BNF_FUT_ID"));
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
					
					if(PE_Price<PE_Init_Price && !orderFilled) {
						
						try {
							new RestTelegramCall().sendUpdate("ORDER EXECUTED%20"+instrumentPE+"%20@%20"+PE_Init_Price);
						} catch (Exception e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						
						orderFilled=true;
					}
					
					target = Integer.parseInt(prop.getProperty("ALPHA_BNF_TARGET"));
					stopLoss = Integer.parseInt(prop.getProperty("ALPHA_BNF_STOPLOSS"));
					
					if(Fut_Price<=low-target || Fut_Price>=low+stopLoss || Fut_Price>=high || (LocalDateTime.now().getHour()==15 && LocalDateTime.now().getMinute()==15)) {
													
						executor = Executors.newFixedThreadPool(Integer.parseInt(prop.getProperty("THREAD_COUNT")));
						
						for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
							
							try {
								
								KiteConnect kiteConnection = (KiteConnect)entry.getValue();
					    	    
					    	    executor.execute(new ZerodhaExitOrderPlacement(instrumentPE,quantity,"SELL","MIS",0.0,kiteConnection));
				    	    
							}catch(Exception e) {
								
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							}
				    	    
				    	}
						
						for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
							
							try {
								
								executor.execute(new AliceExitOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentPE,"MIS",instrumentExID,quantity,"SELL",0.0));
							
							}catch(Exception e) {
							
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								
							}
							
						}
						
						for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
							
							try {
								
								KiteConnect kiteConnection = (KiteConnect)entry.getValue();
					    	    
					    	    executor.execute(new ZerodhaCancelLimitOrder(instrumentPE,kiteConnection));
				    	    
							}catch(Exception e) {
								
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							}
				    	    
				    	}
						
						for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
							
							try {
								
								executor.execute(new AliceCancelLimitOrder((String)entry.getKey(),(String)entry.getValue(),instrumentPE));
							
							}catch(Exception e) {
							
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Alice Cancel Order Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								
							}
							
						}
						
						System.out.println(LocalDateTime.now()+" : Order Exiting PE Initial Price="+PE_Init_Price+" | PE Current Price="+PE_Price);
						
						executor.shutdown();

						orderPlaced=false;
						startPEModule=false;
						
						try {
							new RestTelegramCall().send("SELLING%20"+instrumentPE+"%20@%20"+PE_Price);
						} catch (Exception e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						
						if(orderFilled) {
							try {
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Alpha Nifty','"+instrument+"','"+orderTime+"',"+PE_Init_Price+",'"+currentTime+"',"+PE_Price+");");
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
							System.out.println(LocalDateTime.now()+" : PE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidation);
							
						}else {
							
							bidaskValidation=false;
							System.out.println(LocalDateTime.now()+" : PE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidation);
						}
						
					}
					
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
					
				
				
				System.out.println(LocalDateTime.now()+" PE Point = "+countPEPoint);
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

	
	
	public static double getLTP(KiteConnect kiteConnect, String instrumentId) throws KiteException, IOException {
        String[] instruments = {instrumentId};
        return kiteConnect.getLTP(instruments).get(instrumentId).lastPrice;
    }

}
