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
package org.apache.camel.builder.endpoint;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.endpoint.dsl.QuartzEndpointBuilderFactory;
import org.apache.camel.component.quartz.QuartzEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class QuartzTest extends BaseEndpointDslTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testQuartz() throws Exception {
        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                QuartzEndpointBuilderFactory.QuartzEndpointBuilder builder = quartz("myGroup/myTrigger").cron("0/1+*+*+*+*+?");
                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                QuartzEndpoint qe = assertIsInstanceOf(QuartzEndpoint.class, endpoint);
                assertEquals("0/1 * * * * ?", qe.getCron());
                assertEquals("myGroup", qe.getGroupName());
                assertEquals("myTrigger", qe.getTriggerName());

                builder = quartz("myGroup2/myTrigger2").cron("0/2 * * * * ?");
                endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                qe = assertIsInstanceOf(QuartzEndpoint.class, endpoint);
                assertEquals("0/2 * * * * ?", qe.getCron());
                assertEquals("myGroup2", qe.getGroupName());
                assertEquals("myTrigger2", qe.getTriggerName());
            }
        });

        context.stop();
    }

}
