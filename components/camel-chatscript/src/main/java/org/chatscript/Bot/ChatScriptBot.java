package org.chatscript.Bot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;

import org.apache.camel.component.ChatScriptProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatScriptBot {

	String host=null;
	int port=1024;
	String message=null;
	String botname=null;
	String username=null;
    final char nullChar = (char) 0;
    boolean initialized=false;
    private static final transient Logger log = LoggerFactory.getLogger(ChatScriptBot.class);

	public ChatScriptBot(String ihost,int port, String ibotname,String iusername)
	{
		this.host=ihost;
		this.port=port;
		this.botname=ibotname;
		this.username=iusername;
	}
	 
	 public String sendChat(String input) {
		 if (!initialized) init(null);
	  return doMessage(this.username+nullChar+nullChar+input+nullChar);
	 }

	 private String doMessage(String mess)
	 {
	  Socket echoSocket;
	  String resp = "";
	  
	  try {
	   echoSocket = new Socket(this.host, this.port);
	   PrintWriter out = new PrintWriter(echoSocket.getOutputStream(),
	     true);
	   BufferedReader in = new BufferedReader(new InputStreamReader(
	     echoSocket.getInputStream()));
	   out.println(mess);
	   resp = in.readLine();
	   echoSocket.close();
	  } catch (IOException e) {
	   e.printStackTrace();
	   log.error("Error: " + e.getMessage());
	  }

	  return resp;
	  
	 }
	 public void init(HashMap<String, String> args) {
	  log.debug("ChatScript Bot init()...");
	   doMessage(nullChar+"1"+nullChar+nullChar);
	  log.debug("now starting conversation..." + doMessage(this.botname+nullChar+nullChar+nullChar));
	  log.debug("conversation started with bot" + this.botname);
	  initialized=true;
	 }

	 public String getBotType() {
	  return "ChatSCript";
	 }

	public void reset() {
		  doMessage(this.username+nullChar+nullChar+":reset"+nullChar);
	}

}