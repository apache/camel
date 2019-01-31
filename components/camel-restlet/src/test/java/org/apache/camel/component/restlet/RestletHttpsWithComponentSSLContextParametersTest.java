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

import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Test;

public class RestletHttpsWithComponentSSLContextParametersTest extends RestletTestSupport {

    private static final String REQUEST_MESSAGE = "<mail><body>HelloWorld!</body><subject>test</subject><to>x@y.net</to></mail>";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                RestletComponent restletSSL = new RestletComponent();
                RestletComponent restletWithGlobalSSL = new RestletComponent();

                context.addComponent("restlet-SSL", restletSSL);
                context.addComponent("restlet-withGlobalSSL", restletWithGlobalSSL);

                SSLContextParameters scp = generateSSLContextParametrs("changeit");
                SSLContextParameters globalScp = generateSSLContextParametrs("wrongPassword");

                context.getComponent("restlet-SSL", RestletComponent.class).setSslContextParameters(scp);

                context.getComponent("restlet-withGlobalSSL", RestletComponent.class).setUseGlobalSslContextParameters(true);
                context.getComponent("restlet-withGlobalSSL", RestletComponent.class).setSslContextParameters(scp);
                context.setSSLContextParameters(globalScp);

                from("restlet-SSL:https://localhost:" + portNum + "/users/SSL?restletMethods=post").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        convertBody(exchange);
                    }
                });

                from("restlet-withGlobalSSL:https://localhost:" + (portNum + 1) + "/users/globalSSL?restletMethods=post").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        convertBody(exchange);
                    }
                });
            }
        };
    }

    @Test
    public void testComponentSSLContextParametrs() throws Exception {
        // test that ssl context set for component
        postRequestMessage(REQUEST_MESSAGE, "SSL", 0);
    }

    @Test
    public void testComponentGlobalSSLContextParametrs() throws Exception {
        // test that ssl context set for component has bigger priority than
        // global context
        postRequestMessage(REQUEST_MESSAGE, "globalSSL", 1);
    }

    private void postRequestMessage(String message, String path, Integer portIncrement) throws Exception {
        URL trustStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.ks");
        System.setProperty("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());

        HttpPost post = new HttpPost("https://localhost:" + (portNum + portIncrement) + "/users/" + path);
        post.addHeader(Exchange.CONTENT_TYPE, "application/xml");
        post.setEntity(new StringEntity(message));

        HttpResponse response = doExecute(post);
        assertHttpResponse(response, 200, "application/xml");
        String s = context.getTypeConverter().convertTo(String.class, response.getEntity().getContent());
        assertTrue(s.contains("<status>OK</status>"));
    }

    void convertBody(Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
        assertTrue("Get a wrong request message", body.contains(REQUEST_MESSAGE));
        exchange.getOut().setBody("<status>OK</status>");
        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
    }

    SSLContextParameters generateSSLContextParametrs(String password) {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.ks").getPath().toString());
        ksp.setPassword(password);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(password);
        kmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);

        return sslContextParameters;
    }
}
