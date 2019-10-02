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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

import io.netty.handler.codec.http.FullHttpResponse;

public class NettyHttp204ErrorTest extends BaseNettyTest {

	@Test
	public void testHttp204Error_web() throws Exception {
		HttpUriRequest request = new HttpGet( "http://localhost:" + getPort() + "/foo");
		HttpClient httpClient = HttpClientBuilder.create().build();
		HttpResponse httpResponse = httpClient.execute(request);
		
		assertEquals(204, httpResponse.getStatusLine().getStatusCode());
		assertNull(httpResponse.getEntity());
	}
	
    @Test
    public void testHttp204Error_direct_call() throws Exception {
    	Exchange inExchange = this.createExchangeWithBody("Hello World");
    	Exchange outExchange = template.send("netty-http:http://localhost:{{port}}/foo", inExchange);
    	
    	assertEquals(204, outExchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
    	assertEquals("", outExchange.getIn().getBody(String.class));
    	
    	NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
    	FullHttpResponse response = message.getHttpResponse();
    	
    	assertEquals(204, response.getStatus().code());
    	assertEquals("", message.getBody(String.class));
    }
    
    @Test
    public void testHttp204Error_indirect_call() throws Exception {
    	Exchange inExchange = this.createExchangeWithBody("Hello World");
    	Exchange outExchange = template.send("direct:start", inExchange);
    	
    	assertEquals(204, outExchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
    	assertEquals("", outExchange.getIn().getBody(String.class));
    	
    	assertEquals(null, outExchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    	assertEquals(null, outExchange.getOut().getBody(String.class));
    	
    	NettyHttpMessage message = outExchange.getIn(NettyHttpMessage.class);
    	FullHttpResponse response = message.getHttpResponse();
    	
    	assertEquals(204, response.getStatus().code());
    	assertEquals("", message.getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            	from("netty-http:http://localhost:{{port}}/foo")
            		.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
            		.setBody().constant("Nothing Found");
            	
            	from("direct:start")
        			.to("netty-http:http://localhost:{{port}}/foo");
 
            }
        };
    }
}
