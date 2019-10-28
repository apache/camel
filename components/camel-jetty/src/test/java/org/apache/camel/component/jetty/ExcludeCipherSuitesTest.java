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
import org.apache.camel.support.jsse.FilterParameters;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ExcludeCipherSuitesTest extends BaseJettyTest {

    protected String pwd = "changeit";

    private SSLContextParameters createSslContextParameters() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.p12").toString());
        ksp.setPassword(pwd);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(pwd);
        kmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);

        FilterParameters filter = new FilterParameters();
        filter.getExclude().add("^.*_(MD5|SHA|SHA1)$");
        sslContextParameters.setCipherSuitesFilter(filter);

        return sslContextParameters;
    }

    @Test
    public void testExclude() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived(1);

        template.sendBody("jetty:https://localhost:" + getPort() + "/test", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                JettyHttpComponent jetty = getContext().getComponent("jetty", JettyHttpComponent.class);
                jetty.setSslContextParameters(createSslContextParameters());

                from("jetty:https://localhost:" + getPort() + "/test").to("mock:a");
            }
        };
    }
}
