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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.config.ConfigClient;
import software.amazon.awssdk.services.config.model.DeleteConformancePackRequest;
import software.amazon.awssdk.services.config.model.DeleteConformancePackResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AWSConfigRemoveConformancePackTest extends CamelTestSupport {

    @BindToRegistry("configClient")
    private final ConfigClient configClient = mock(ConfigClient.class);

    @Test
    public void removeConformancePackUsesTheConformancePackNameHeader() throws Exception {
        when(configClient.deleteConformancePack(any(DeleteConformancePackRequest.class)))
                .thenReturn(DeleteConformancePackResponse.builder().build());

        // The conformance pack name comes from CONFORMANCE_PACK_NAME; RULE_NAME is set to a different value
        // to prove removeConformancePack no longer reads it by mistake.
        template.send("direct:start", exchange -> {
            exchange.getIn().setHeader(AWSConfigConstants.CONFORMACE_PACK_NAME, "my-pack");
            exchange.getIn().setHeader(AWSConfigConstants.RULE_NAME, "some-rule");
        });

        ArgumentCaptor<DeleteConformancePackRequest> captor
                = ArgumentCaptor.forClass(DeleteConformancePackRequest.class);
        org.mockito.Mockito.verify(configClient).deleteConformancePack(captor.capture());
        assertEquals("my-pack", captor.getValue().conformancePackName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("aws-config://test?configClient=#configClient&operation=removeConformancePack");
            }
        };
    }
}
