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
package org.apache.camel.component.xquery;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Fixed me later")
public class XQueryPredicateFilterTest extends CamelTestSupport {
    
    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint; 

    @Produce("direct:xpath")
    protected ProducerTemplate template; 

    @Test
    public void testXQuerySplitter() throws Exception { 
        resultEndpoint.expectedMessageCount(1); 
        template.sendBody("<records><record><type>1</type></record><record><type>2</type></record></records>"); 
        resultEndpoint.assertIsSatisfied();
       
        resultEndpoint.reset();
        template.sendBody("<records><record><type>3</type></record><record><type>4</type></record></records>"); 
        resultEndpoint.expectedMessageCount(0);
        resultEndpoint.assertIsSatisfied();
    } 

    @Override 
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                XPathBuilder splitter = new XPathBuilder("//records/record");

                context.setTracing(true);

                from("direct:xpath").split(splitter).filter().xquery("//record[type=2]")
                    .to("mock:result");

            }
        };
    }

}
