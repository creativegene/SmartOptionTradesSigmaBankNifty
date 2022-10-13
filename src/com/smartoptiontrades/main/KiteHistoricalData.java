package com.smartoptiontrades.main;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import org.json.JSONException;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Date;

public class KiteHistoricalData {

    public TimeSeries retrieve (KiteConnect kiteConnect, String fromStr,String toStr,String token,String timePeriod) {
    	
    	TimeSeries series = new BaseTimeSeries(token);
    	    	
        try {
        	
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date from =  new Date();
                Date to = new Date();
                try {
                    from = formatter.parse(fromStr);
                    to = formatter.parse(toStr);
                }catch (ParseException e) {
                    e.printStackTrace();
                }
                
                HistoricalData historicalData = kiteConnect.getHistoricalData(from, to, token , "minute", false, false);
                
                ZoneId defaultZoneId = ZoneId.systemDefault();
                for (HistoricalData hd:historicalData.dataArrayList){
                	try {
                			series.addBar(new BaseBar(
	        						Duration.ofMinutes(Integer.parseInt(timePeriod.substring(0,1))),new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+0530").parse(hd.timeStamp).toInstant().plusSeconds(60*Integer.parseInt(timePeriod.substring(0,1))).atZone(defaultZoneId),
	        						Decimal.valueOf(hd.open),
	        						Decimal.valueOf(hd.high),
	        						Decimal.valueOf(hd.low),
	        						Decimal.valueOf(hd.close),
	        						Decimal.valueOf(hd.volume)));
        			} catch (ParseException e) {
        				
        				e.printStackTrace();
        			}
                	
                }
                
                System.out.println("Bar Count : "+series.getBarCount());
                
                                
        }catch (KiteException e) {
            System.out.println(e.message+" "+e.code+" "+e.getClass().getName());
        } catch (JSONException e) {
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }
        
        return series;
    }

}

