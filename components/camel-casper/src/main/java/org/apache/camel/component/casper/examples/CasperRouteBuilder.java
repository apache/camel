package org.apache.camel.component.casper.examples;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;

/**
 * CasperRouteBuilder loading 4 routes
 * @author p35862
 *
 */

public class CasperRouteBuilder extends RouteBuilder {

	public void configure()
   {
     
	   
	   //example routes for consumer  using a testnet node
	   
	   from("casper:http://localhost:8080/events/main?operation=block_added").log("Block Hash - ${body}");
	 

	   //example routes for producer  using a testnet node
	   
	   from("timer://simpleTimer?period=5000")
	      .to("casper:http://65.21.227.180:7777/?operation="+CasperConstants.ACCOUNT_INFO)
	      .log("call "+CasperConstants.ACCOUNT_INFO +" with params : blockHeight=530214 and publicKey=017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077   gives result = ${body}");
	   
	   
	   
	   from("timer://simpleTimer?period=5000")
	      .to("casper:http://65.21.227.180:7777/?operation="+CasperConstants.ACCOUNT_INFO+"&blockHeight=530214&publicKey=017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077")
	      .log("call "+CasperConstants.ACCOUNT_INFO +" with params : blockHeight=530214 and publicKey=017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077   gives result = ${body}");
	   
	 	   
	  
	   
	   from("timer://simpleTimer?period=3000")
	      .to("casper:http://65.21.227.180:7777/?operation="+CasperConstants.STATE_ROOT_HASH+"&blockHeight=530214")
	      .log("call "+CasperConstants.STATE_ROOT_HASH +" with params : blockHeight=530214 gives result = - ${body}");
	 
   }
}
