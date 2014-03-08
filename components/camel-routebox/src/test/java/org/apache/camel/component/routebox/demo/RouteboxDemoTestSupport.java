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
package org.apache.camel.component.routebox.demo;

import java.net.URI;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.routebox.strategy.RouteboxDispatchStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;

public class RouteboxDemoTestSupport extends CamelTestSupport {
    
    public String sendAddToCatalogRequest(ProducerTemplate template, String endpointUri, String operation, Book book) throws Exception {
        String response = (String) template.requestBodyAndHeader(endpointUri, book, "ROUTE_DISPATCH_KEY", operation);  
        return response;      
    }
 
    public Book sendFindBookRequest(ProducerTemplate template, String endpointUri, String operation, String body) throws Exception {
        Book response = (Book) template.requestBodyAndHeader(endpointUri, body, "ROUTE_DISPATCH_KEY", operation);  
        return response;
    }
    
    public class SimpleRouteDispatchStrategy implements RouteboxDispatchStrategy {

        /* (non-Javadoc)
         * @see org.apache.camel.component.routebox.strategy.RouteboxDispatchStrategy#selectDestinationUri(java.util.List, org.apache.camel.Exchange)
         */
        public URI selectDestinationUri(List<URI> destinations,
                Exchange exchange) {
            URI dispatchDestination = null;
            
            String operation = exchange.getIn().getHeader("ROUTE_DISPATCH_KEY", String.class);
            for (URI destination : destinations) {
                if (destination.toASCIIString().equalsIgnoreCase("seda:" + operation)) {
                    dispatchDestination = destination;
                    break;
                }
            }
            
            return dispatchDestination;
        }
    }
    
}
