
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


public class OptionUltraNFCE implements Runnable{

	public void run() {
		
		boolean startCEModule = true;
		Properties prop = new GetPropertiesObject().retrieve();
		
		int quantity=Integer.parseInt(prop.getProperty("ULTRA_NF_QTY"));
		int interval=1;
		
		double target = Integer.parseInt(prop.getProperty("ULTRA_NF_TARGET"));
		double stopLoss = Integer.parseInt(prop.getProperty("ULTRA_NF_STOPLOSS"));
		
		String instrument="",instrumentCE="";
		String instrumentID="";
		String instrumentExID="";
		double CE_Init_Price=0.0,CE_Price=0.0;
		
		boolean bidaskValidation=true;
		boolean bidaskValidationCompleted = false;
		
		boolean OIAnalysisCompleted = false;
		boolean OISupportTrade = false;
		
		
		TimeSeries series = new BaseTimeSeries("Series");
		
		DateTimeFormatter formatter_date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		
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
		
		Connection con = null, con_sat=null;
		
		try {
			con = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING"),prop.getProperty("DB_USER"),prop.getProperty("DB_PASSWORD"));
			con_sat = DriverManager.getConnection(prop.getProperty("DB_CONNECT_STRING_SAT"),prop.getProperty("DB_USER_SAT"),prop.getProperty("DB_PASSWORD_SAT"));
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		Statement stmt = null, stmt_sat=null;
		
		try {
			stmt = con.createStatement();
			stmt_sat = con_sat.createStatement();
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
		
			rs=stmt.executeQuery("Select name,instrumentId,exchangeToken from option_trade_instrument where ltp>="+Integer.parseInt(prop.getProperty("ULTRA_NF_OPTION_PRICE"))+" and name like 'NIFTY%CE' order by ltp asc limit 1;");
			
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
		
		LocalDateTime currentTime = LocalDateTime.now();	
		
		prop = new GetPropertiesObject().retrieve();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		while(startCEModule) {
			
			//System.out.println(LocalDateTime.now()+" : System CE Running");
			
			if(LocalDateTime.now().getSecond()==0 && OISupportTrade && bidaskValidation){
				
				currentTime = LocalDateTime.now();
				
				String dateStrStart = currentTime.format(formatter_date).toString()+" "+prop.getProperty("ULTRA_NF_START_TIME");
				String dateStrEnd = currentTime.format(formatter_date).toString()+" "+prop.getProperty("ULTRA_NF_START_TIME");
				
				System.out.println("Start Time : "+dateStrStart);
				System.out.println("End Time : "+dateStrEnd);
								
				series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,instrumentID,"1minute");
				
				int barCount = series.getBarCount();
			
				System.out.println(LocalDateTime.now()+" | CE Bar Count = "+barCount);
				System.out.println(LocalDateTime.now()+"|"+instrument+"|"+series.getBar(barCount-1).getBeginTime()+"|"+series.getBar(barCount-1).getOpenPrice()+"|"+series.getBar(barCount-1).getMaxPrice()
						+"|"+series.getBar(barCount-1).getMinPrice()+"|"+series.getBar(barCount-1).getClosePrice()+"|"+series.getBar(barCount-1).getVolume());
				
				
				ExecutorService executor = Executors.newFixedThreadPool(50);
				
				double entryPrice = series.getBar(barCount-1).getClosePrice().doubleValue();
				
				for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
					
					try {
						
						KiteConnect kiteConnection = (KiteConnect)entry.getValue();
			    	    
			    	    executor.execute(new ZerodhaEntryOrderPlacement(instrumentCE,quantity,"BUY","NRML",entryPrice,kiteConnection));
		    	    
					}catch(Exception e) {
						
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
					}
		    	    
		    	}
									
				for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
					
					try {
						
						executor.execute(new AliceEntryOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentCE,"NRML",instrumentExID,quantity,"BUY",entryPrice));
					
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
					CE_Init_Price=getLTP(kiteConnect, instrumentID);
				} catch (IOException | KiteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					new RestTelegramCall().send("BUYING%20"+instrumentCE+"%20@%20"+entryPrice);
				} catch (Exception e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				
				boolean orderFilled=false;
					
				while(orderPlaced) {
					
					prop = new GetPropertiesObject().retrieve();
					
					try {
						CE_Price=getLTP(kiteConnect, instrumentID);
					} catch (IOException | KiteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					if(CE_Price<entryPrice && !orderFilled) {
						
						try {
							new RestTelegramCall().sendUpdate("ORDER EXECUTED%20"+instrumentCE+"%20@%20"+entryPrice);
						} catch (Exception e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						
						orderFilled=true;
					}
					
					target = Integer.parseInt(prop.getProperty("ULTRA_NF_TARGET"));
					stopLoss = Integer.parseInt(prop.getProperty("ULTRA_NF_STOPLOSS"));
					
					if(CE_Price>=entryPrice+target || CE_Price<=entryPrice-stopLoss || (LocalDateTime.now().getHour()==15 && LocalDateTime.now().getMinute()==15)) {
													
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
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Ultra NF','"+instrument+"','"+orderTime+"',"+entryPrice+",'"+currentTime+"',"+CE_Price+");");
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
			
			if(LocalDateTime.now().getSecond()==15 && !bidaskValidationCompleted) {
				
				try {
					
					System.out.println("Select bid_ask_ratio from algotrade.option_bid_ask_data where option_type='NF_CE' and timestamp = '"+currentTime.format(formatter_date)+" "+prop.getProperty("ULTRA_NF_START_TIME")+"';");
				
					rs = stmt.executeQuery("Select bid_ask_ratio from algotrade.option_bid_ask_data where option_type='NF_CE' and timestamp = '"+currentTime.format(formatter_date)+" "+prop.getProperty("ULTRA_NF_START_TIME")+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))>=1) {
							
							bidaskValidation=true;
							
						}else {
							
							bidaskValidation=false;
							
						}
						
					}
					
					bidaskValidation=true;
					bidaskValidationCompleted=true;
					
					System.out.println(LocalDateTime.now()+" : CE Bid Ask Validation "+bidaskValidation);
		                	
	            
				}catch(Exception e) {
					
					e.printStackTrace();
					
				}
				
			}
			
			if(LocalDateTime.now().getSecond()==30 && !OIAnalysisCompleted) {
				
				int countCEPoint=0,countPEPoint=0;
				OIAnalysisCompleted=true;
				
				try {
					rs=stmt.executeQuery("Select 5min,10min,15min,30min from option_oi_analysis where instrument='NIFTY' and timestamp='"+currentTime.format(formatter_date)+" "+prop.getProperty("ULTRA_NF_START_TIME")+"';");
					
					
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
				
				startCEModule=false;
				
			}
			
		}
			
	}

	
	
	public static double getLTP(KiteConnect kiteConnect, String instrumentId) throws KiteException, IOException {
        String[] instruments = {instrumentId};
        return kiteConnect.getLTP(instruments).get(instrumentId).lastPrice;
    }

}
