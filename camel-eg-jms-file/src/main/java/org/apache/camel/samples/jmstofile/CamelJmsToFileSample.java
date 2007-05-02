/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.samples.jmstofile;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;


/**
 * An example class for demonstrating some of the basics behind camel
 * 
 * This example will send some text messages on to a JMS Queue, consume them and 
 * persist them to disk
 *
 * @version $Revision: 529902 $
 * 
 */
public class CamelJmsToFileSample{

    public static void main(String args[]) throws Exception{
        CamelContext context=new DefaultCamelContext();
      
        //Set up the ActiveMQ JMS Components
        ConnectionFactory connectionFactory=new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        //note we can explicity  name the component
        context.addComponent("test-jms",jmsComponentAutoAcknowledge(connectionFactory));
        
        //Add some configuration by hand ...
        context.addRoutes(new RouteBuilder(){

            public void configure(){
                from("test-jms:queue:test.queue").to("file://test");
                // set up a listener on the file component
                from("file://test").process(new Processor(){

                    public void process(Exchange e){
                        System.out.println("Received exchange: "+e.getIn());
                    }
                });
            }
        });
        // Camel client - a handy class for kicking off exchanges
        CamelClient client=new CamelClient(context);
        
        //Now everything is set up - lets start the context
        context.start();
        
        //now send some test text to a component - for this case a JMS Queue 
        //The text get converted to JMS messages - and sent to the Queue test.queue
        //The file component is listening for messages from the Queue test.queue, consumes
        //them and stores them to disk. The content of each file will be the test test we sent here.
        //The listener on the file component gets notfied when new files are found ...
        //that's it!
        
        for(int i=0;i<10;i++){
            client.sendBody("test-jms:queue:test.queue","Test Message: "+i);
        }
       
        Thread.sleep(1000);
        context.stop();
    }
}