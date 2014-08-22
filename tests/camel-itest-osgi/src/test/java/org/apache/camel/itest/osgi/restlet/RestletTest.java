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
package org.apache.camel.itest.osgi.restlet;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class RestletTest extends OSGiIntegrationTestSupport {

    @Test
    public void testRestletProducer() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("username", "homer");
        String response = (String)template.requestBodyAndHeaders("restlet:http://localhost:9080/users/{username}?restletMethod=POST", "<request>message</request>", headers);
        assertEquals("The response is wrong ", response, "{homer}");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:9080/users/{username}?restletMethod=POST").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String userName = exchange.getIn().getHeader("username", String.class);
                        assertNotNull("userName should not be null", userName);
                        exchange.getOut().setBody("{" + userName + "}");
                        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/JSON");
                    }
                });
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-restlet"));
        
        return options;
    }
  
}