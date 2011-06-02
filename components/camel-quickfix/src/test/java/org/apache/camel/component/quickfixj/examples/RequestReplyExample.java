/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.quickfixj.examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quickfixj.MessagePredicate;
import org.apache.camel.component.quickfixj.QuickfixjComponent;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.apache.camel.component.quickfixj.QuickfixjProducer;
import org.apache.camel.component.quickfixj.examples.transform.QuickfixjMessageJsonTransformer;
import org.apache.camel.component.quickfixj.examples.util.CountDownLatchDecrementer;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.FieldNotFound;
import quickfix.SessionID;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecTransType;
import quickfix.field.ExecType;
import quickfix.field.LeavesQty;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrderID;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.OrderStatusRequest;

public class RequestReplyExample {
    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjComponent.class);

    public static void main(String[] args) throws Exception {
        new RequestReplyExample().run();
    }
    
    public void run() throws Exception {        
        DefaultCamelContext context = new DefaultCamelContext();
        final CountDownLatch logonLatch = new CountDownLatch(1);
        final String orderStatusServiceUrl = "http://localhost:9123/order/status";
        
        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            	// Synchronize the logon so we don't start sending status requests too early
                from("quickfix:examples/inprocess.cfg?sessionID=FIX.4.2:TRADER->MARKET").
                    filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon)).
                    bean(new CountDownLatchDecrementer("logon", logonLatch));

                // Incoming status requests are converted to InOut exchange pattern and passed to the
                // order status service. The response is sent back to the session making the request.
                from("quickfix:examples/inprocess.cfg?sessionID=FIX.4.2:MARKET->TRADER&exchangePattern=InOut").
                	filter(header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.ORDER_STATUS_REQUEST)).
                    bean(new MarketOrderStatusService());
                
                from ("jetty:" + orderStatusServiceUrl).
	            	bean(new OrderStatusRequestTransformer()).
                	routingSlip(bean(FixSessionRouter.class, "route")).
	            	bean(new QuickfixjMessageJsonTransformer());
            }
        };
        
        context.addRoutes(routes);
        
        LOG.info("Starting Camel context");
        context.start();
        
        if (!logonLatch.await(5L, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Logon did not succeed");
        }
        
        // Send a request to the order status web service.
        // Verify that the response is a JSON response.
        
        URL orderStatusUrl = new URL(orderStatusServiceUrl + "?sessionID=FIX.4.2:TRADER->MARKET&orderID=abc");
		HttpURLConnection connection = (HttpURLConnection) orderStatusUrl.openConnection();
        BufferedReader orderStatusReply = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line = orderStatusReply.readLine();
        if (!line.equals("\"message\": {")) {
        	throw new Exception("Don't appear to be a JSON response");
        }
        else {
        	StringBuilder sb = new StringBuilder();
        	while (line != null) {
        		sb.append(line);
        		sb.append('\n');
        		line = orderStatusReply.readLine();
        	}
        	Log.info("Web request response:\n" + sb);
        	
        }
        orderStatusReply.close();
        
        LOG.info("Shutting down Camel context");
        context.stop();
        
        LOG.info("Example complete");
    }
        
    public static class OrderStatusRequestTransformer {
    	public void transform(Exchange exchange) throws FieldNotFound {
    		String sessionID = (String) exchange.getIn().getHeader("sessionID");
    		String orderID = (String) exchange.getIn().getHeader("orderID");
    		
            OrderStatusRequest request = new OrderStatusRequest(new ClOrdID("XYZ"), new Symbol("GOOG"), new Side(Side.BUY));
            request.set(new OrderID(orderID));
             
            // Look for a reply execution report back to the requester session
            // and having the requested OrderID. This is a loose correlation but the best
            // we can do with FIX 4.2. Newer versions of FIX have an optional explicit correlation field.
            exchange.setProperty(QuickfixjProducer.CORRELATION_CRITERIA_KEY,
            		new MessagePredicate(new SessionID(sessionID), MsgType.EXECUTION_REPORT)
            			.withField(OrderID.FIELD, request.getString(OrderID.FIELD)));
            
            exchange.getIn().setBody(request);
    	}
    }
    
    public static class MarketOrderStatusService {
        private static final Logger LOG = LoggerFactory.getLogger(QuickfixjComponent.class);
        
        public ExecutionReport getOrderStatus(OrderStatusRequest request) throws FieldNotFound {
        	LOG.info("Received order status request for orderId=" + request.getOrderID().getValue());
    		return new ExecutionReport(
    				request.getOrderID(), new ExecID(UUID.randomUUID().toString()),
    						new ExecTransType(ExecTransType.STATUS), 
    						new ExecType(ExecType.REJECTED),
    						new OrdStatus(OrdStatus.REJECTED),
    						new Symbol("GOOG"),
    						new Side(Side.BUY),
    						new LeavesQty(100),
    						new CumQty(0),
    						new AvgPx(0));
    	}
    }
    
    public static class FixSessionRouter {
    	public String route(@Header("sessionID") String sessionID, @Body Object body) {
    		return String.format("quickfix:examples/inprocess.cfg?sessionID=%s", sessionID);
    	}
    }
}
