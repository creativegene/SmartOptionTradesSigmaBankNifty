
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
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.StochasticRSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.personal.HeikinAshi;
import org.ta4j.core.personal.MoneyFlowIndex;
import org.ta4j.core.personal.SuperTrend;
import org.ta4j.core.personal.VWAPIndicatorV2;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Quote;


public class OptionSigmaBNFPE implements Runnable{

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
		double PE_Secondary_Init_Price=0.0,PE_Primary_Init_Price=0.0,PE_Secondary_Final_Price=0.0,PE_Primary_Final_Price=0.0,PE_Price=0.0,Fut_Price=0.0;
		
		//boolean triggerEvaluated=false;
		//boolean triggerValidated = false;
		boolean bidaskValidation=false;
		boolean OISupportTrade = false;
		boolean newSeries=false;
		
		boolean pe_trade = Boolean.parseBoolean(prop.getProperty("SIGMA_BNF_pe_trade"));
		boolean isExpiryDay=false;
		
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
			
			rs=stmt.executeQuery("Select tradingSymbol,instrument_token,exchange_token from master_instrument_list "
					+ "where expiry=(Select expiry from master_instrument_list where tradingsymbol='"+instrumentPEPrimary+"') "
					+ "and strike_price=(Select strike_price-"+prop.getProperty("SIGMA_BNF_HEDGE_STRIKE_RANGE")+" from master_instrument_list where tradingsymbol='"+instrumentPEPrimary+"') "
					+ "and tradingsymbol like 'BANKNIFTY%PE';");
			
			while(rs.next()) {
				
				instrumentPESecondary=rs.getString(1);
				instrumentIDSecondary=rs.getString(2);
				instrumentExIDSecondary=rs.getString(3);
				
			}
			
			System.out.println(LocalDateTime.now()+"| Option PE BUY Instrument | "+instrumentPESecondary);
			System.out.println(LocalDateTime.now()+"| Option PE Sell Instrument | "+instrumentPEPrimary);
			
		}catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
		}
		
		LocalDateTime currentTime = LocalDateTime.now();
		String dateStrStart = currentTime.minusDays(5).format(formatter_date).toString()+" "+prop.getProperty("SIGMA_BNF_START_TIME");
		String dateStrEnd = currentTime.format(formatter_date).toString()+" "+prop.getProperty("SIGMA_BNF_END_TIME");
	
		System.out.println("Start Time : "+dateStrStart);
		System.out.println("End Time : "+dateStrEnd);
	
		series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,instrumentIDPrimary,"1minute");	
		
		int seriesBarCount = series.getBarCount();
		
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);		       
		SMAIndicator MA1 = new SMAIndicator(closePrice, 5);			
		List<Double> vwap = new VWAPIndicatorV2(series).getVWAPIndicator();			
		List<Double> superTrend = new SuperTrend(series,interval,2,1).getSuperTrend();			
		StochasticOscillatorKIndicator stoch = new StochasticOscillatorKIndicator(series,15);
		SMAIndicator stoch_K = new SMAIndicator(stoch, 5);
		SMAIndicator stoch_D = new SMAIndicator(stoch_K, 5);
		RSIIndicator rsi = new RSIIndicator(closePrice, 7);		
		StochasticRSIIndicator rsi_stoch = new StochasticRSIIndicator(rsi,15);
		SMAIndicator rsi_K = new SMAIndicator(rsi_stoch, 5);
		SMAIndicator rsi_D = new SMAIndicator(rsi_K, 5);
		MACDIndicator macd = new MACDIndicator(closePrice,12,26);
		SMAIndicator macd_signal = new SMAIndicator(macd, 9);
		
		System.out.println(currentTime+" : "+closePrice.getValue(seriesBarCount-1)+"|"+vwap.get(seriesBarCount-1)
		+"|"+superTrend.get(seriesBarCount-1)+"|"+stoch_K.getValue(seriesBarCount-1)+"|"+stoch_D.getValue(seriesBarCount-1)
		+"|"+rsi_K.getValue(seriesBarCount-1)+"|"+rsi_D.getValue(seriesBarCount-1)+"|"+macd.getValue(seriesBarCount-1)
		+"|"+macd_signal.getValue(seriesBarCount-1));
		
		while(startPEModule) {
			
			prop = new GetPropertiesObject().retrieve();
			currentTime = LocalDateTime.now();
			
			if(currentTime.getSecond()==0) {
				
				//dateStrStart = currentTime.minusDays(5).format(formatter_date).toString()+" "+prop.getProperty("SIGMA_BNF_START_TIME");
				dateStrEnd = currentTime.format(formatter).toString();
			
				//System.out.println("Start Time : "+dateStrStart);
				//System.out.println("End Time : "+dateStrEnd);
			
				series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,instrumentIDPrimary,"1minute");	
				seriesBarCount = series.getBarCount();
				
				closePrice = new ClosePriceIndicator(series);		       
				MA1 = new SMAIndicator(closePrice, 5);			
				vwap = new VWAPIndicatorV2(series).getVWAPIndicator();			
				superTrend = new SuperTrend(series,interval,2,1).getSuperTrend();			
				stoch = new StochasticOscillatorKIndicator(series,15);
				stoch_K = new SMAIndicator(stoch, 5);
				stoch_D = new SMAIndicator(stoch_K, 5);
				rsi = new RSIIndicator(closePrice, 7);		
				rsi_stoch = new StochasticRSIIndicator(rsi,15);
				rsi_K = new SMAIndicator(rsi_stoch, 5);
				rsi_D = new SMAIndicator(rsi_K, 5);
				macd = new MACDIndicator(closePrice,12,26);
				macd_signal = new SMAIndicator(macd, 9);
				
				System.out.println(currentTime+" : "+closePrice.getValue(seriesBarCount-1)+"|"+vwap.get(seriesBarCount-1)
				+"|"+superTrend.get(seriesBarCount-1)+"|"+stoch_K.getValue(seriesBarCount-1)+"|"+stoch_D.getValue(seriesBarCount-1)
				+"|"+rsi_K.getValue(seriesBarCount-1)+"|"+rsi_D.getValue(seriesBarCount-1)+"|"+macd.getValue(seriesBarCount-1)
				+"|"+macd_signal.getValue(seriesBarCount-1));				
				
				if(series.getBar(seriesBarCount-1).getClosePrice().isGreaterThan(superTrend.get(seriesBarCount-1)) 
						&& series.getBar(seriesBarCount-2).getClosePrice().isLessThan(superTrend.get(seriesBarCount-2))) {
					System.out.println(LocalDateTime.now()+" : New PE Series Initiated");
					newSeries=true;
				}
				
			}
			
			/*
			if(!triggerEvaluated) {	
				
				String dateStrStart = currentTime.format(formatter_date).toString()+" "+prop.getProperty("SIGMA_BNF_START_TIME");
				String dateStrEnd = currentTime.format(formatter_date).toString()+" "+prop.getProperty("SIGMA_BNF_END_TIME");
				
				System.out.println("Start Time : "+dateStrStart);
				System.out.println("End Time : "+dateStrEnd);
								
				series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,prop.getProperty("SIGMA_BNF_FUT_ID"),"1minute");				
				
				for(int i=0;i<series.getBarCount();i++) {
					
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
				System.out.println("------------------BANK NIFTY FUTURE "+prop.getProperty("SIGMA_BNF_START_TIME")+" "+prop.getProperty("SIGMA_BNF_INTERVAL")+"min Candlestick------------------");
				System.out.println(startTime+"|"+open+"|"+high+"|"+low+"|"+close);
				triggerEvaluated=true;
			}
			
			if(!triggerValidated && triggerEvaluated && pe_trade) {
				
				try {
					Fut_Price=getLTP(kiteConnect, prop.getProperty("SIGMA_BNF_FUT_ID"));
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
						PE_Init_Price=getLTP(kiteConnect, instrumentIDPrimary);
						System.out.println(LocalDateTime.now()+" : CE Trade Triggered Fut Price="+Fut_Price+" CE Price="+PE_Init_Price);
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
			*/
			
			if(series.getBar(seriesBarCount-1).getClosePrice().isGreaterThan(superTrend.get(seriesBarCount-1)) && newSeries
					&& series.getBar(seriesBarCount-1).getClosePrice().isGreaterThan(series.getBar(seriesBarCount-1).getOpenPrice())
					&& macd.getValue(seriesBarCount-1).isGreaterThan(macd_signal.getValue(seriesBarCount-1))
					&& stoch_K.getValue(seriesBarCount-1).isGreaterThan(stoch_D.getValue(seriesBarCount-1)) && stoch_K.getValue(seriesBarCount-1).isLessThan(80)
					&& rsi_K.getValue(seriesBarCount-1).isGreaterThan(rsi_D.getValue(seriesBarCount-1)) && rsi_K.getValue(seriesBarCount-1).isLessThan(80)
					&& OISupportTrade && bidaskValidation && pe_trade){
				
				newSeries=false;
				
				ExecutorService executor = Executors.newFixedThreadPool(50);
				
				/*
				for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
					
					try {
						
						KiteConnect kiteConnection = (KiteConnect)entry.getValue();
			    	    
			    	    executor.execute(new ZerodhaEntryOrderPlacement(instrumentPESecondary,quantity,"BUY","MIS",0.0,kiteConnection));
		    	    
					}catch(Exception e) {
						
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha BUY Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
					}
		    	    
		    	}
									
				for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
					
					try {
						
						executor.execute(new AliceOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentPESecondary,"MIS",instrumentExIDSecondary,quantity,"BUY",0.0));
					
					}catch(Exception e) {
					
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma BUY Block!!!!!!!!!!!!!!!");
						System.out.println(LocalDateTime.now()+" : "+e.getMessage());
						System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						
					}
					
				}
				
				try {
					Thread.sleep(1000); 
				} catch (InterruptedException e3) {
					// TODO Auto-generated catch block
					e3.printStackTrace();
				}
				*/
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
					//PE_Secondary_Init_Price=getLTP(kiteConnect, instrumentIDSecondary);
					PE_Primary_Init_Price=getLTP(kiteConnect, instrumentIDPrimary);
					//new RestTelegramCall().send("BUYING%20"+instrumentPESecondary+"%20@%20"+PE_Secondary_Init_Price);	
					//Thread.sleep(1000);
					new RestTelegramCall().send("BUYING%20"+instrumentPEPrimary+"%20@%20"+PE_Primary_Init_Price);
				} catch (Exception | KiteException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				
				boolean orderFilled=true; //Since Market Order Placed
					
				while(orderPlaced) {
					
					prop = new GetPropertiesObject().retrieve();
					currentTime = LocalDateTime.now();
					
					if(currentTime.getSecond()==0) {
						
						//dateStrStart = currentTime.minusDays(5).format(formatter_date).toString()+" "+prop.getProperty("SIGMA_BNF_START_TIME");
						dateStrEnd = currentTime.format(formatter).toString();
					
						//System.out.println("Start Time : "+dateStrStart);
						//System.out.println("End Time : "+dateStrEnd);
					
						series= new KiteHistoricalData().retrieve(kiteConnect,dateStrStart,dateStrEnd,instrumentIDPrimary,"1minute");	
						seriesBarCount = series.getBarCount();
						
						closePrice = new ClosePriceIndicator(series);		       
						MA1 = new SMAIndicator(closePrice, 5);			
						vwap = new VWAPIndicatorV2(series).getVWAPIndicator();			
						superTrend = new SuperTrend(series,interval,2,1).getSuperTrend();			
						stoch = new StochasticOscillatorKIndicator(series,15);
						stoch_K = new SMAIndicator(stoch, 5);
						stoch_D = new SMAIndicator(stoch_K, 5);
						rsi = new RSIIndicator(closePrice, 7);		
						rsi_stoch = new StochasticRSIIndicator(rsi,15);
						rsi_K = new SMAIndicator(rsi_stoch, 5);
						rsi_D = new SMAIndicator(rsi_K, 5);
						macd = new MACDIndicator(closePrice,12,26);
						macd_signal = new SMAIndicator(macd, 9);
						
						System.out.println(LocalDateTime.now()+" : "+closePrice.getValue(seriesBarCount-1)+"|"+vwap.get(seriesBarCount-1)
						+"|"+superTrend.get(seriesBarCount-1)+"|"+stoch_K.getValue(seriesBarCount-1)+"|"+stoch_D.getValue(seriesBarCount-1)
						+"|"+rsi_K.getValue(seriesBarCount-1)+"|"+rsi_D.getValue(seriesBarCount-1)+"|"+macd.getValue(seriesBarCount-1)
						+"|"+macd_signal.getValue(seriesBarCount-1));
						
						if(series.getBar(seriesBarCount-1).getClosePrice().isGreaterThan(superTrend.get(seriesBarCount-1)) 
								&& series.getBar(seriesBarCount-2).getClosePrice().isLessThan(superTrend.get(seriesBarCount-2))) {
							System.out.println(LocalDateTime.now()+" : New PE Series Initiated");
							newSeries=true;
						}
					}
					
					try {
						PE_Price=getLTP(kiteConnect, instrumentIDPrimary);
						Fut_Price=getLTP(kiteConnect, prop.getProperty("SIGMA_BNF_FUT_ID"));
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
					/*
					if(PE_Price<PE_Init_Price && !orderFilled) {
						
						try {
							new RestTelegramCall().sendUpdate("ORDER EXECUTED%20"+instrumentPEPrimary+"%20@%20"+PE_Init_Price);
						} catch (Exception e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						
						orderFilled=true;
					}
					*/
					target = Integer.parseInt(prop.getProperty("SIGMA_BNF_TARGET"));
					stopLoss = Integer.parseInt(prop.getProperty("SIGMA_BNF_STOPLOSS"));
					
					if(PE_Price<vwap.get(seriesBarCount-1) || PE_Price<superTrend.get(seriesBarCount-1) 
							|| PE_Price>=PE_Primary_Init_Price+target || PE_Price<=PE_Primary_Init_Price-stopLoss 
							|| (LocalDateTime.now().getHour()==15 && LocalDateTime.now().getMinute()==15)) {	
						
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
						/*
						for (Map.Entry<String, KiteConnect> entry : kiteUserMap.entrySet()) {
							
							try {
								
								KiteConnect kiteConnection = (KiteConnect)entry.getValue();
					    	    
					    	    executor.execute(new ZerodhaExitOrderPlacement(instrumentPESecondary,quantity,"SELL","MIS",0.0,kiteConnection));
				    	    
							}catch(Exception e) {
								
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Zerodha SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							}
				    	    
				    	}
						
						for (Map.Entry<String, String> entry : aliceUserMap.entrySet()) {
							
							try {
								
								executor.execute(new AliceExitOrderPlacement((String)entry.getKey(),(String)entry.getValue(),instrumentPESecondary,"MIS",instrumentExIDPrimary,quantity,"SELL",0.0));
							
							}catch(Exception e) {
							
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!FROM Sigma SELL Block!!!!!!!!!!!!!!!");
								System.out.println(LocalDateTime.now()+" : "+e.getMessage());
								System.out.println(LocalDateTime.now()+" : !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								
							}
							
						}
						*/
						
						try {
							//PE_Secondary_Final_Price=getLTP(kiteConnect, instrumentIDSecondary);
							PE_Primary_Final_Price=getLTP(kiteConnect, instrumentIDPrimary);
							new RestTelegramCall().send("SELLING%20"+instrumentPEPrimary+"%20@%20"+PE_Primary_Final_Price);
							//Thread.sleep(1000);
							//new RestTelegramCall().send("SELLING%20"+instrumentPESecondary+"%20@%20"+PE_Secondary_Final_Price);
						} catch (Exception | KiteException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						System.out.println(LocalDateTime.now()+" : Order Exiting CE Initial Price="+PE_Primary_Init_Price+" | CE Current Price="+PE_Primary_Final_Price);
						
						executor.shutdown();

						orderPlaced=false;
						//startPEModule=false;
						
						if(orderFilled) {
							try {
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Sigma Bank Nifty','"+instrumentPEPrimary+"','"+orderTime+"',"+PE_Primary_Init_Price+",'"+currentTime+"',"+PE_Primary_Final_Price+");");
								stmt.executeUpdate("insert into algotrade.strategy_order_book values ('Sigma Bank Nifty','"+instrumentPESecondary+"','"+orderTime+"',"+PE_Secondary_Init_Price+",'"+currentTime+"',"+PE_Secondary_Final_Price+");");

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
				
				boolean bidaskValidationPE = false, bidaskValidationFut=false;
				
				try {
					
					//System.out.println("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentIDPrimary+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
				
					rs = stmt_tdb.executeQuery("Select avg(bid_qty/ask_qty) from tickdata where id="+instrumentIDPrimary+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))>1 || isExpiryDay) {
							
							bidaskValidationPE=true;
							System.out.println(LocalDateTime.now()+" : PE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationPE);
							
						}else {
							
							bidaskValidationPE=false;
							System.out.println(LocalDateTime.now()+" : PE Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationPE);
						}
						
					}
					
					rs = stmt_tdb.executeQuery("Select avg(bid_qty/ask_qty) from tickdata where id="+prop.getProperty("SIGMA_BNF_FUT_ID")+" and timestamp >= '"+currentTime.minusSeconds(30).format(formatter)+"';");
					
					while(rs.next()) {
						
						if(Double.parseDouble(rs.getString(1))<1) {
							
							bidaskValidationFut=true;
							System.out.println(LocalDateTime.now()+" : Fut Short Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationFut);
							
						}else {
							
							bidaskValidationFut=false;
							System.out.println(LocalDateTime.now()+" : Fut Short Bid Ask "+rs.getString(1)+" Validation => "+bidaskValidationFut);
						}
						
					}
					
					if(bidaskValidationPE && bidaskValidationFut) {
						bidaskValidation=true;
					}else {
						bidaskValidation=false;
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
