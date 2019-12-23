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
package org.apache.camel.component.cxf;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.non_wrapper.types.GetPerson;
import org.apache.camel.non_wrapper.types.GetPersonResponse;
import org.apache.cxf.message.MessageContentsList;

public class RouteBuilderCxfTracer extends RouteBuilder {
    @Override
    public void configure() throws Exception {        
        from("cxf:http://localhost:9000/PersonService/" 
            + "?serviceClass=org.apache.camel.non_wrapper.Person"
            + "&serviceName={http://camel.apache.org/non-wrapper}PersonService"
            + "&portName={http://camel.apache.org/non-wrapper}soap"
            + "&dataFormat=POJO")
            .process(new BeforeProcessor()).to("direct:something").process(new AfterProcessor());

        from("direct:something")
            .process(new DoSomethingProcessor())
            .process(new DoNothingProcessor());
    }
    
    private static class DoSomethingProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().setBody(exchange.getIn().getBody() + " world!");        
        }
    }
    
    private static class DoNothingProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().setBody(exchange.getIn().getBody());        
        }
    }
     
    private static class BeforeProcessor implements Processor {
        @Override
        public void process(Exchange e) throws Exception {
            MessageContentsList mclIn = e.getIn().getBody(MessageContentsList.class);
            e.getIn().setBody(((GetPerson) mclIn.get(0)).getPersonId(), String.class);
        }
    }

    private static class AfterProcessor implements Processor {
        @Override
        public void process(Exchange e) throws Exception {
            GetPersonResponse gpr = new GetPersonResponse();
            gpr.setName("Bill");
            gpr.setPersonId(e.getIn().getBody(String.class));
            gpr.setSsn("Test");

            MessageContentsList mclOut = new MessageContentsList();
            mclOut.set(0, gpr);
            e.getOut().setBody(mclOut, MessageContentsList.class);
        }
    }
}
