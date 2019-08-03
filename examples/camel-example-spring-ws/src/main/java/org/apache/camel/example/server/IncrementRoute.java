/*
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
package org.apache.camel.example.server;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.example.server.model.IncrementRequest;
import org.apache.camel.example.server.model.IncrementResponse;

public class IncrementRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        JaxbDataFormat jaxb = new JaxbDataFormat(IncrementRequest.class.getPackage().getName());
        
        from("spring-ws:rootqname:{http://camel.apache.org/example/increment}incrementRequest?endpointMapping=#endpointMapping")
            .unmarshal(jaxb)
            .process(new IncrementProcessor())
            .marshal(jaxb);
    }
    
    private static final class IncrementProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            IncrementRequest request = exchange.getIn().getBody(IncrementRequest.class);
            IncrementResponse response = new IncrementResponse();
            int result = request.getInput() + 1; // increment input value
            response.setResult(result); 
            exchange.getOut().setBody(response);
        }
    }
   
}