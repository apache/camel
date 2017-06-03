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
package org.apache.camel.component.urlrewrite.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.urlrewrite.BaseUrlRewriteTest;
import org.apache.camel.component.urlrewrite.HttpUrlRewrite;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 *
 */
public class JettyUrlRewriteLoadBalanceFailoverTest extends BaseUrlRewriteTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        HttpUrlRewrite myRewrite = new HttpUrlRewrite();
        myRewrite.setConfigFile("example/urlrewrite2.xml");

        jndi.bind("myRewrite", myRewrite);

        return jndi;
    }

    @Test
    public void testHttpUriRewrite() throws Exception {
        // we should failover from app2 to app3 all the time
        String out = template.requestBody("http4://localhost:{{port}}/myapp/products/1234", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp3/products/index.jsp?product_id=1234", out);

        out = template.requestBody("http4://localhost:{{port}}/myapp/products/5678", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp3/products/index.jsp?product_id=5678", out);

        out = template.requestBody("http4://localhost:{{port}}/myapp/products/3333", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp3/products/index.jsp?product_id=3333", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // just disable error handler as we use failover load balancer
                errorHandler(noErrorHandler());

                // START SNIPPET: e1
                // we want to use the failover loadbalancer
                // to have it to react we must set throwExceptionOnFailure=true, which is also the default value
                // so we can omit configuring this option
                from("jetty:http://localhost:{{port}}/myapp?matchOnUriPrefix=true")
                    .loadBalance().failover(Exception.class)
                        .to("jetty:http://localhost:{{port2}}/myapp2?bridgeEndpoint=true&throwExceptionOnFailure=true&urlRewrite=#myRewrite")
                        .to("jetty:http://localhost:{{port2}}/myapp3?bridgeEndpoint=true&throwExceptionOnFailure=true&urlRewrite=#myRewrite");
                // END SNIPPET: e1

                from("jetty:http://localhost:{{port2}}/myapp2?matchOnUriPrefix=true")
                    .log("I am going to fail")
                    .throwException(new IllegalArgumentException("Failed"));

                from("jetty:http://localhost:{{port2}}/myapp3?matchOnUriPrefix=true")
                    .transform().simple("${header.CamelHttpUrl}?${header.CamelHttpQuery}");
            }
        };
    }
}
