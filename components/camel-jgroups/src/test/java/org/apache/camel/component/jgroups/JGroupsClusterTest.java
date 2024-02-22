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
package org.apache.camel.component.jgroups;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.apache.camel.component.jgroups.JGroupsFilters.dropNonCoordinatorViews;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JGroupsClusterTest {

    // Tested state

    String master;

    int nominationCount;

    // Routing fixtures

    String jgroupsEndpoint = format("jgroups:%s?enableViewMessages=true", randomUUID());

    DefaultCamelContext firstCamelContext;

    DefaultCamelContext secondCamelContext;

    class Builder extends RouteBuilder {

        @Override
        public void configure() {
            from(jgroupsEndpoint).filter(dropNonCoordinatorViews()).process(new Processor() {
                @Override
                public void process(Exchange exchange) {
                    String camelContextName = exchange.getContext().getName();
                    if (!camelContextName.equals(master)) {
                        master = camelContextName;
                        nominationCount++;
                    }
                }
            });
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        firstCamelContext = new DefaultCamelContext();
        firstCamelContext.getCamelContextExtension().setName("firstNode");
        firstCamelContext.addRoutes(new Builder());

        secondCamelContext = new DefaultCamelContext();
        secondCamelContext.getCamelContextExtension().setName("secondNode");
        secondCamelContext.addRoutes(new Builder());
    }

    // Tests

    @Test
    public void shouldElectSecondNode() {
        // When
        firstCamelContext.start();
        String firstMaster = master;
        secondCamelContext.start();
        firstCamelContext.stop();
        String secondMaster = master;
        secondCamelContext.stop();

        // Then
        assertEquals(firstCamelContext.getName(), firstMaster);
        assertEquals(secondCamelContext.getName(), secondMaster);
        assertEquals(2, nominationCount);
    }

    @Test
    public void shouldKeepMaster() {
        // When
        firstCamelContext.start();
        String firstMaster = master;
        secondCamelContext.start();
        secondCamelContext.stop();
        String secondMaster = master;
        firstCamelContext.stop();

        // Then
        assertEquals(firstCamelContext.getName(), firstMaster);
        assertEquals(firstCamelContext.getName(), secondMaster);
        assertEquals(1, nominationCount);
    }

    @Test
    public void shouldElectSecondNodeAndReturnToFirst() {
        // When
        firstCamelContext.start();
        String firstMaster = master;
        secondCamelContext.start();
        firstCamelContext.stop();
        String secondMaster = master;
        firstCamelContext.start();
        String masterAfterRestartOfFirstNode = master;
        secondCamelContext.stop();
        String finalMaster = master;
        firstCamelContext.stop();

        // Then
        assertEquals(firstCamelContext.getName(), firstMaster);
        assertEquals(secondCamelContext.getName(), secondMaster);
        assertEquals(secondCamelContext.getName(), masterAfterRestartOfFirstNode);
        assertEquals(firstCamelContext.getName(), finalMaster);
        assertEquals(3, nominationCount);
    }

}
