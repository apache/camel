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
package org.apache.camel.component.thymeleaf;

import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.PropertyBindingListener;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

public class ThymeleafComponentTest extends ThymeleafAbstractBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ThymeleafComponentTest.class);

    private String templateMode;

    @Test
    public void testThymeleaf() throws InterruptedException {

        ThymeleafComponent component = context.getComponent("thymeleaf", ThymeleafComponent.class);
        assertNotNull(component);

        ThymeleafEndpoint thymeleafEndpoint = context.getEndpoint(
                "thymeleaf:dontcare?templateMode=CSS&allowContextMapAll=true&resolver=STRING",
                ThymeleafEndpoint.class);

        ThymeleafEndpoint spy = spy(thymeleafEndpoint);
        LOG.info("foo: {}",
                thymeleafEndpoint.getComponent().getEndpointPropertyConfigurer().getClass());

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);

        template.request(DIRECT_START, templateHeaderProcessor);

        mock.assertIsSatisfied();

        assertEquals(TemplateMode.CSS.name(), thymeleafEndpoint.getTemplateMode());

        assertEquals(1, thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().size());
        ITemplateResolver resolver = thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().stream().findFirst().get();
        assertTrue(resolver instanceof StringTemplateResolver);

        StringTemplateResolver templateResolver = (StringTemplateResolver) resolver;
        assertEquals(TemplateMode.CSS, templateResolver.getTemplateMode());
        assertEquals(TemplateMode.CSS.name(), templateMode);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            public void configure() {

                from(DIRECT_START)
                        .setBody(simple(SPAZZ_TESTING_SERVICE))
                        .to("thymeleaf:dontcare?templateMode=CSS&allowContextMapAll=true&resolver=STRING")
                        .to(MOCK_RESULT);

                /*
                 * Listens for the templateMode property binding to allow verification of the <code>createEndpoint()</code>
                 * method in <code>ThymeleafComponent</code>.
                 */
                getCamelContext().getRegistry().bind(UUID.randomUUID().toString(),
                        (PropertyBindingListener) (target, key, value) -> {
                            if ("templateMode".equals(key)) {
                                templateMode = (String) value;
                            }
                        });
            }
        };
    }
}
