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
package org.apache.camel.component.xslt;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XsltTotalOpsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testXsltTotalOpsLimitRejectsStylesheet() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                XsltComponent xslt = context.getComponent("xslt", XsltComponent.class);
                xslt.setXpathTotalOpLimit(1);

                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/example.xsl?output=bytes").to("mock:result");
            }
        });

        Exception e = assertThrows(Exception.class, () -> context.start(),
                "Should fail due to low total ops");

        TransformerConfigurationException tce
                = assertIsInstanceOf(TransformerConfigurationException.class, e.getCause().getCause().getCause());
        assertThat(tce.getMessage()).contains("exceeds the '1' limit");
    }

    @Test
    void testXsltTotalOpsLimitDoesNotLeakSystemProperty() throws Exception {
        // Ensure no pre-existing system property
        System.clearProperty("jdk.xml.xpathTotalOpLimit");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                XsltComponent xslt = context.getComponent("xslt", XsltComponent.class);
                xslt.setXpathTotalOpLimit(1);

                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/example.xsl?output=bytes").to("mock:result");
            }
        });

        // Context start will fail due to low limit, but the point is to check
        // that no system property was set
        assertThrows(Exception.class, () -> context.start());

        // The limit should be set per-factory, not as a system property (CAMEL-24216)
        assertThat(System.getProperty("jdk.xml.xpathTotalOpLimit"))
                .as("xpathTotalOpLimit should not leak as a JVM system property")
                .isNull();
    }

    @Test
    void testXsltTotalOpLimitHighEnoughAllowsStylesheet() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                XsltComponent xslt = context.getComponent("xslt", XsltComponent.class);
                xslt.setXpathTotalOpLimit(20000);

                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/example.xsl?output=bytes").to("mock:result");
            }
        });

        // Should start successfully with a high enough limit
        context.start();

        // No system property should be leaked
        assertThat(System.getProperty("jdk.xml.xpathTotalOpLimit"))
                .as("xpathTotalOpLimit should not leak as a JVM system property")
                .isNull();

        context.stop();
    }

    @Test
    void testXsltTotalOpLimitPerEndpointUri() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/example.xsl?output=bytes&xpathTotalOpLimit=20000")
                        .to("mock:result");
            }
        });

        // Should start successfully with the limit set via endpoint URI
        context.start();

        // No system property should be leaked
        assertThat(System.getProperty("jdk.xml.xpathTotalOpLimit"))
                .as("xpathTotalOpLimit should not leak as a JVM system property")
                .isNull();

        context.stop();
    }
}
