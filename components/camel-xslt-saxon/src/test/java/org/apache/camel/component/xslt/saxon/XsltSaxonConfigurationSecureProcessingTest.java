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
package org.apache.camel.component.xslt.saxon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.FeatureKeys;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The secureProcessing option is applied on top of a custom Saxon configuration. The stylesheet writes a result
 * document, which Saxon refuses when extension functions are disabled by secure processing.
 */
public class XsltSaxonConfigurationSecureProcessingTest extends CamelTestSupport {

    private static final String XSL
            = "xslt-saxon:org/apache/camel/component/xslt/saxon/result-document.xsl?saxonConfiguration=#saxonConfig";

    @BindToRegistry("saxonConfig")
    private final Configuration saxonConfig = new Configuration();

    @BindToRegistry("allowExternalFunctions")
    private final Map<String, Object> allowExternalFunctions = Map.of(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS, true);

    @TempDir
    Path outDir;

    @Test
    public void secureProcessingIsAppliedWithSaxonConfiguration() {
        Exception e = assertThrows(Exception.class, () -> addRoute(XSL));

        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("xsl:result-document is disabled when extension functions are disabled"),
                root.getMessage());
        assertFalse(Files.exists(outDir.resolve("part.xml")));
    }

    @Test
    public void secureProcessingCanBeTurnedOffWithSaxonConfiguration() throws Exception {
        addRoute(XSL + "&secureProcessing=false");

        sendAndAssertResultDocument();
    }

    @Test
    public void saxonConfigurationPropertiesOverrideSecureProcessing() throws Exception {
        addRoute(XSL + "&saxonConfigurationProperties=#allowExternalFunctions");

        sendAndAssertResultDocument();
    }

    private void addRoute(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(uri).to("mock:result");
            }
        });
    }

    private void sendAndAssertResultDocument() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "<in/>", "outDir", outDir.toUri().toString());
        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.contains("<done/>"), body);
        String part = Files.readString(outDir.resolve("part.xml"));
        assertTrue(part.contains("<part/>"), part);
    }
}
