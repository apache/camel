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
package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty10.JettyHttpComponent10;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JettyComponentMuteExceptionTest extends BaseJettyTest {

    @Test
    public void testMuteException() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpGet get = new HttpGet("http://localhost:" + getPort() + "/foo");
        get.addHeader("Accept", "application/text");
        HttpResponse response = client.execute(get);

        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        assertEquals("", responseString);
        assertEquals(500, response.getStatusLine().getStatusCode());

        client.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                JettyHttpComponent10 jc = context.getComponent("jetty", JettyHttpComponent10.class);
                jc.setMuteException(true);

                from("jetty:http://localhost:{{port}}/foo").to("mock:destination")
                        .throwException(new IllegalArgumentException("Camel cannot do this"));
            }
        };
    }

}
