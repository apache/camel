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
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.AuthenticationValidationHandler;
import org.apache.camel.impl.JndiRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpsAuthenticationTest extends BaseHttpsTest {

    private String user = "camel";
    private String password = "password";

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("x509HostnameVerifier", new AllowAllHostnameVerifier());

        return registry;
    }

    @Test
    public void httpsGetWithAuthentication() throws Exception {
        localServer.register("/", new AuthenticationValidationHandler("GET", null, null, getExpectedContent(), user, password));

        Exchange exchange = template.request("https4://127.0.0.1:" + getPort() + "/?authUsername=camel&authPassword=password&x509HostnameVerifier=x509HostnameVerifier", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

    @Override
    protected BasicHttpProcessor getBasicHttpProcessor() {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestBasicAuth());

        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseBasicUnauthorized());

        return httpproc;
    }
}