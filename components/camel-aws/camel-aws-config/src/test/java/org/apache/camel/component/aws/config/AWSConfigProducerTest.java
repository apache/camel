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
package org.apache.camel.component.aws.config;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.config.ConfigClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class AWSConfigProducerTest extends CamelTestSupport {

    @BindToRegistry("configClient")
    private final ConfigClient configClient = mock(ConfigClient.class);

    @Test
    public void noOperationFailsWithAHint() {
        Exchange exchange = template.send("direct:noOperation", e -> e.getIn().setBody("test"));

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("Operation must be specified, either with the operation option or the CamelAwsConfigOperation header",
                exchange.getException().getMessage());
        verifyNoInteractions(configClient);
    }

    @Test
    public void putConformancePackWithoutNameAsksForTheConformancePackName() {
        Exchange exchange = template.send("direct:putConformancePack",
                e -> e.getIn().setHeader(AWSConfigConstants.CONFORMACE_PACK_TEMPLATE_BODY, "Resources: {}"));

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("Conformance Pack Name must be specified", exchange.getException().getMessage());
        verifyNoInteractions(configClient);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noOperation")
                        .to("aws-config://test?configClient=#configClient");
                from("direct:putConformancePack")
                        .to("aws-config://test?configClient=#configClient&operation=putConformancePack");
            }
        };
    }
}
