package org.apache.camel.component.casper.examples;

import org.apache.camel.main.Main;

/**
 * A Camel Application
 */
public class App {
   
	/**
	 * 
	 * @param args : main rags
	 * @throws Exception : exception
	 */
   public static void main(String...args) throws Exception
   {
      Main main = new Main();

      main.configure().addRoutesBuilder(new CasperRouteBuilder());
      main.run(args);
   }
}
