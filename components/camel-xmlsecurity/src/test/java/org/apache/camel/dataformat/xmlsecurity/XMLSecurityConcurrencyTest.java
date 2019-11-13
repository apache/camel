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
package org.apache.camel.dataformat.xmlsecurity;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.KeyGenerator;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class XMLSecurityConcurrencyTest extends CamelTestSupport {

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:secure").expectedMessageCount(files);
        getMockEndpoint("mock:result").assertNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    String body = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><body>you can not read me " + index + "</body>";
                    template.sendBody("direct:start", body);
                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied();

        String secure = getMockEndpoint("mock:secure").getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(secure);
        assertTrue("Should not be readable", secure.indexOf("read") == -1);
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        Key defaultKey = keyGenerator.generateKey();

        return new RouteBuilder() {
            public void configure() {
                from("direct:start").
                        marshal().secureXML(defaultKey.getEncoded()).
                        to("mock:secure").
                        to("direct:marshalled");

                from("direct:marshalled").
                        unmarshal().secureXML(defaultKey.getEncoded()).
                        convertBodyTo(String.class).
                        to("mock:result");
            }
        };
    }

}
