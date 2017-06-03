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
package org.apache.camel.component.urlrewrite.http4;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.urlrewrite.BaseUrlRewriteTest;
import org.apache.camel.component.urlrewrite.HttpUrlRewrite;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 *
 */
public class Http4UrlRewritePingTest extends BaseUrlRewriteTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        HttpUrlRewrite myRewrite = new HttpUrlRewrite();
        myRewrite.setConfigFile("example/urlrewrite-ping.xml");
        myRewrite.setUseQueryString(false);

        jndi.bind("myRewrite", myRewrite);

        return jndi;
    }

    @Test
    public void testHttpUriRewrite() throws Exception {
        String out = template.requestBody("http4://localhost:{{port}}/ping", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/proxy/ping", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/?matchOnUriPrefix=true")
                    .to("http4://localhost:{{port2}}/?bridgeEndpoint=true&throwExceptionOnFailure=false&urlRewrite=#myRewrite");

                from("jetty:http://localhost:{{port2}}/proxy/?matchOnUriPrefix=true")
                    .transform().simple("${header.CamelHttpUrl}");
            }
        };
    }
}
