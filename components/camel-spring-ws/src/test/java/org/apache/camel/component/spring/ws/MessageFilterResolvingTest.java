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
package org.apache.camel.component.spring.ws;

import java.io.IOException;
import java.net.URI;

import javax.xml.namespace.QName;

import net.javacrumbs.smock.springws.client.AbstractSmockClientTest;

import org.apache.camel.ProducerTemplate;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.test.client.RequestMatcher;

/**
 * Check if the MessageFilter is used and resolved from endpoint uri or global
 * context configuration.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:org/apache/camel/component/spring/ws/MessageFilter-context.xml"})
public class MessageFilterResolvingTest extends AbstractSmockClientTest {
    @Autowired
    private ProducerTemplate template;

    private String body = "<customerCountRequest xmlns='http://springframework.org/spring-ws'>" + "<customerName>John Doe</customerName>" + "</customerCountRequest>";

    @Test
    public void globalTestHeaderAttribute() {
        expect(soapHeader(new QName("http://newHeaderSupport/", "testHeaderValue1"))).andExpect(soapHeader(new QName("http://virtualCheck/", "globalFilter")));

        template.sendBodyAndHeader("direct:sendWithGlobalFilter", body, "headerKey", new QName("http://newHeaderSupport/", "testHeaderValue1"));
    }

    @Test
    public void localTestHeaderAttribute() {
        expect(soapHeader(new QName("http://newHeaderSupport/", "testHeaderValue1"))).andExpect(soapHeader(new QName("http://virtualCheck/", "localFilter")));

        template.sendBodyAndHeader("direct:sendWithLocalFilter", body, "headerKey", new QName("http://newHeaderSupport/", "testHeaderValue1"));

    }

    @Test
    public void emptyTestHeaderAttribute() {
        expect(doesntContains(soapHeader(new QName("http://newHeaderSupport/", "testHeaderValue1"))));

        template.sendBodyAndHeader("direct:sendWithoutFilter", body, "headerKey", new QName("http://newHeaderSupport/", "testHeaderValue1"));

    }

    private RequestMatcher doesntContains(final RequestMatcher soapHeader) {
        return new RequestMatcher() {
            public void match(URI uri, WebServiceMessage request) throws IOException, AssertionError {
                try {
                    soapHeader.match(uri, request);

                } catch (AssertionError e) {
                    // ok
                    return;
                }
                throw new AssertionError("Should failed!");
            }
        };
    }
   
    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        createServer(applicationContext);
    }

    @After
    public void verify() {
        super.verify();
    }
}
