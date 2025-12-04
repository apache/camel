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

package org.apache.camel.component.rest.openapi;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.junit.jupiter.api.AfterEach;

abstract class ManagedCamelTestSupport {
    protected CamelContext context;
    protected ProducerTemplate template;

    protected abstract RoutesBuilder createRouteBuilder();

    protected abstract CamelContext createCamelContext(String componentName);

    protected void initializeContextForComponent(String componentName) throws Exception {
        context = createCamelContext(componentName);

        context.addRoutes(createRouteBuilder());

        context.start();
        template = context.createProducerTemplate();
        template.start();
    }

    @AfterEach
    final void shutdownEverything() {
        if (template != null) {
            template.stop();
        }

        if (context != null) {
            context.stop();
        }
    }
}
