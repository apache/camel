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
package org.apache.camel.component.scp;

import java.io.File;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

public class ScpSimpleProduceTest extends ScpServerTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:" + getScpPath() + "?recursive=true&delete=true")
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }

    @Test
    public void testScpSimpleProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScpSimpleProduceTwoTimes() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(uri, "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScpSimpleSubPathProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Bye World", Exchange.FILE_NAME, "mysub/bye.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScpSimpleTwoSubPathProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Farewell World");

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Farewell World", Exchange.FILE_NAME, "mysub/mysubsub/farewell.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScpProduceChmod() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Bonjour Monde");

        String uri = getScpUri() + "?username=admin&password=admin&chmod=640&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Bonjour Monde", Exchange.FILE_NAME, "monde.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    @Ignore("Fails on CI servers")
    public void testScpProducePrivateKey() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        String uri = getScpUri() + "?username=admin&privateKeyFile=src/test/resources/camel-key.priv&privateKeyFilePassphrase=password&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hallo Welt", Exchange.FILE_NAME, "welt.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    @Ignore("Fails on CI servers")
    public void testScpProducePrivateKeyFromClasspath() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        String uri = getScpUri() + "?username=admin&privateKeyFile=classpath:camel-key.priv&privateKeyFilePassphrase=password&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hallo Welt", Exchange.FILE_NAME, "welt.txt");

        assertMockEndpointsSatisfied();
    }
  
    @Test
    @Ignore("Fails on CI servers")
    public void testScpProducePrivateKeyByte() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        String uri = getScpUri() + "?username=admin&privateKeyBytes=#privKey&privateKeyFilePassphrase=password&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hallo Welt", Exchange.FILE_NAME, "welt.txt");

        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        byte[] privKey = Files.readAllBytes(new File("src/test/resources/camel-key.priv").toPath());
        registry.bind("privKey", privKey);
        return registry;
    }
}
