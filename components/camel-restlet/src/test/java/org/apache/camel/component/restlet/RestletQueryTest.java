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
package org.apache.camel.component.restlet;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

/**
 *
 * @version $Revision$
 */
public class RestletQueryTest extends ContextTestSupport {
    private static final String QUERY_STRING = "foo=bar&test=123";

    
    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:9080/users/{username}")
                    .process(new SetUserProcessor());
                
            }
            
        };
    }
    
    class SetUserProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {   
            assertEquals(QUERY_STRING, exchange.getIn().getHeader(RestletConstants.QUERY_STRING, 
                    String.class));
        }
        
    }
    
    public void testPostBody() throws Exception {
        HttpMethod method = new GetMethod("http://localhost:9080/users/homer?" + QUERY_STRING);
        try {
            HttpClient client = new HttpClient();
            assertEquals(200, client.executeMethod(method));
        } finally {
            method.releaseConnection();
        }

    }
}
