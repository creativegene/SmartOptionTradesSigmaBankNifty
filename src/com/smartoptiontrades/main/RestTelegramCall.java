package com.smartoptiontrades.main;

import java.net.URL;
import java.net.URLConnection;

public class RestTelegramCall {
	
	String id,id_support;
	
	RestTelegramCall(){
		
		this.id="-1001598743901"; //Zeta Bank Nifty Channel
		
		this.id_support="-1001189655442"; //Support Channel
		
	}
	
	RestTelegramCall(String id){
		this.id=id;
	}

    public void send(String message) throws Exception {
    	
    	//System.out.println("https://sandipmuk.pythonanywhere.com/telegram/send?text="+message+"&button_text=&page_redirect=https://smartalgotrades.com&chat_id="+id);
        URL url = new URL("https://sandipmuk.pythonanywhere.com/telegram/send?text="+message+"&button_text=&page_redirect=https://smartalgotrades.com&chat_id="+id);
        
        URLConnection urlc = url.openConnection();
        
        urlc.setDoOutput(true);
        urlc.setAllowUserInteraction(false);
        urlc.getInputStream();

    }
    
    public void sendUpdate(String message) throws Exception {
    	
    	//System.out.println("https://sandipmuk.pythonanywhere.com/telegram/send?text="+message+"&button_text=&page_redirect=https://smartalgotrades.com&chat_id="+id_support);
        URL url = new URL("https://sandipmuk.pythonanywhere.com/telegram/send?text="+message+"&button_text=&page_redirect=https://smartalgotrades.com&chat_id="+id_support);
        
        URLConnection urlc = url.openConnection();

        urlc.setDoOutput(true);
        urlc.setAllowUserInteraction(false);
        urlc.getInputStream();

    }
    
}