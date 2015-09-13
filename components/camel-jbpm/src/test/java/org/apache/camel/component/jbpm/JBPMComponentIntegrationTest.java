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

package org.apache.camel.component.jbpm;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("This is an integration test that needs BPMS running on the local machine")
public class JBPMComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void interactsOverRest() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:rest", null, JBPMConstants.PROCESS_ID, "project1.integration-test");
        assertMockEndpointsSatisfied();

        assertNotNull(getMockEndpoint("mock:result").getExchanges().get(0).getIn().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:rest")
                        .to("jbpm:http://localhost:8080/business-central?userName=bpmsAdmin&password=pa$word1"
                            + "&deploymentId=org.kie.example:project1:1.0.0-SNAPSHOT")
                        .to("mock:result");
            }
        };
    }
}
