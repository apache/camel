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

package org.apache.camel.component.tahu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.Test;

public class TahuHostConsumerIT extends TahuTestSupport {

    @Test
    public void hostSessionEstablishmentTest() throws Exception {

        CamelContext context = camelContextExtension.getContext();

        assertTrue(context::isStopped, () -> "CamelContext is stopped");

        spTckService.initiateTckTest("host SessionEstablishmentTest " + TahuHostConsumerRouteBuilder.HOST_ID);

        startContext();

        MockEndpoint.assertIsSatisfied(15L, TimeUnit.SECONDS, spTckService.spTckResultMockEndpoint);
    }

    protected void configureComponent(CamelContext context, TahuConfiguration tahuConfig) {
        TahuHostComponent tahuComponent = context.getComponent(TahuConstants.HOST_APP_SCHEME, TahuHostComponent.class);
        tahuComponent.setConfiguration(tahuConfig);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new TahuHostConsumerRouteBuilder(context));
    }
}
