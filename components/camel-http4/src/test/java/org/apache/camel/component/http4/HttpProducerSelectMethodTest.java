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
package org.apache.camel.component.http4;

import org.apache.camel.Exchange;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.localserver.LocalTestServer;
import org.junit.Test;

import static org.apache.camel.component.http4.HttpMethods.GET;
import static org.apache.camel.component.http4.HttpMethods.POST;

/**
 * Unit test to verify the algorithm for selecting either GET or POST.
 *
 * @version 
 */
public class HttpProducerSelectMethodTest extends BaseHttpTest {

    @Override
    protected void registerHandler(LocalTestServer server) {
        localServer.register("/myget", new BasicValidationHandler("GET", null, null, getExpectedContent()));
        localServer.register("/mypost", new BasicValidationHandler("POST", null, null, getExpectedContent()));
        localServer.register("/myget2", new BasicValidationHandler("GET", "q=Camel", null, getExpectedContent()));
    }

    @Test
    public void noDataDefaultIsGet() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);
        
        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/myget");
        HttpProducer producer = new HttpProducer(endpoiont);
        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);
        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }

    @Test
    public void dataDefaultIsPost() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/mypost");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("This is some data to post");
        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }

    @Test
    public void withMethodPostInHeader() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/mypost");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, POST);
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Test
    public void withMethodGetInHeader() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/myget");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, GET);
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Test
    public void withEndpointQuery() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/myget2?q=Camel");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Test
    public void withQueryInHeader() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/myget2");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "q=Camel");
        producer.start();
        producer.process(exchange);
        producer.stop();
    }
    
    @Test 
    public void withHttpURIInHeader() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/myget2");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_URI, "http://" + getHostName() + ":" + getPort() + "/myget2?q=Camel");
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Test
    public void withQueryInHeaderOverrideEndpoint() throws Exception {
        HttpComponent component = context.getComponent("http4", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http4://" + getHostName() + ":" + getPort() + "/myget2?q=Donkey");
        HttpProducer producer = new HttpProducer(endpoiont);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "q=Camel");
        producer.start();
        producer.process(exchange);
        producer.stop();
    }
}