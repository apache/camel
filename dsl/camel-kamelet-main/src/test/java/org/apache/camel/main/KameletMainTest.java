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
package org.apache.camel.main;

import java.util.function.BiConsumer;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.app.Bean1;
import org.apache.camel.main.app.Bean2;
import org.apache.camel.support.ShortUuidGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class KameletMainTest {

    @Test
    public void testRouteWithSpringProcessor() throws Exception {
        doTestMain("classpath:org/apache/camel/main/xml/spring-camel1.xml", (main, camelContext) -> {
            try {
                MockEndpoint endpoint = camelContext.getEndpoint("mock:finish", MockEndpoint.class);
                endpoint.expectedBodiesReceived("Hello World (2147483647)");

                main.getCamelTemplate().sendBody("direct:start", "I'm World");

                endpoint.assertIsSatisfied();

                assertTrue(camelContext.getUuidGenerator() instanceof ShortUuidGenerator);

                Bean1 bean1 = main.lookupByType(Bean1.class).get("bean1");
                Bean2 bean2 = bean1.getBean();
                assertSame(bean1, bean2.getBean());
            } catch (Exception e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void testRouteWithSpringBeansAndCamelDependencies() throws Exception {
        doTestMain("classpath:org/apache/camel/main/xml/spring-camel2.xml", (main, camelContext) -> {
            try {
                MockEndpoint endpoint = camelContext.getEndpoint("mock:finish", MockEndpoint.class);
                endpoint.expectedBodiesReceived("Hello World (" + System.identityHashCode(camelContext) + ")");

                main.getCamelTemplate().sendBody("direct:start", "I'm World");

                endpoint.assertIsSatisfied();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        });
    }

    protected void doTestMain(String includes, BiConsumer<KameletMain, CamelContext> consumer) throws Exception {
        KameletMain main = new KameletMain();

        main.setDownload(false);
        main.configure().withRoutesIncludePattern(includes);
        main.configure().withAutoConfigurationEnabled(true);
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        consumer.accept(main, camelContext);

        main.stop();
    }

}
