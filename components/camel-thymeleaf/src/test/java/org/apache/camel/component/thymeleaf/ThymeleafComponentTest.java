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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.support.PropertyBindingListener;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThymeleafComponentTest extends CamelTestSupport {

    private String templateMode;

    protected static String stringTemplate() {

        return """
                <!--/*-->
                    Licensed to the Apache Software Foundation (ASF) under one or more
                    contributor license agreements.  See the NOTICE file distributed with
                    this work for additional information regarding copyright ownership.
                    The ASF licenses this file to You under the Apache License, Version 2.0
                    (the "License"); you may not use this file except in compliance with
                    the License.  You may obtain a copy of the License at

                         http://www.apache.org/licenses/LICENSE-2.0

                    Unless required by applicable law or agreed to in writing, software
                    distributed under the License is distributed on an "AS IS" BASIS,
                    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                    See the License for the specific language governing permissions and
                    limitations under the License.
                <!--*/-->
                <!--/* This code will be removed at thymeleaf parsing time! */-->
                Dear [(${headers.lastName})], [(${headers.firstName})]

                Thank you for your order number [(${exchange.properties.orderNumber})] of [(${headers.item})].

                Regards Camel Riders Bookstore
                [(${body})]""";
    }

    @Test
    public void testThymeleaf() throws Exception {

        /*
         * Listens for the templateMode property binding to allow verification of the <code>createEndpoint()</code>
         * method in <code>ThymeleafComponent</code>.
         */
        context.getRegistry().bind(UUID.randomUUID().toString(),
                (PropertyBindingListener) (target, key, value) -> {
                    if ("templateMode".equals(key)) {
                        templateMode = (String) value;
                    }
                });

        ThymeleafComponent component = new ThymeleafComponent();
        assertNotNull(component);
        component.setCamelContext(context);

        Map<String, Object> parameters = new HashMap<>(
                Map.ofEntries(entry("templateMode", "CSS"), entry("allowContextMapAll", true), entry("resolver", "STRING")));

        ThymeleafEndpoint thymeleafEndpoint = (ThymeleafEndpoint) component.createEndpoint("thymeleaf", "dontcare", parameters);
        thymeleafEndpoint.setTemplate(stringTemplate());

        assertEquals(TemplateMode.CSS.name(), thymeleafEndpoint.getTemplateMode());

        assertEquals(1, thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().size());
        ITemplateResolver resolver = thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().stream().findFirst().get();
        assertTrue(resolver instanceof StringTemplateResolver);

        StringTemplateResolver templateResolver = (StringTemplateResolver) resolver;
        assertEquals(TemplateMode.CSS, templateResolver.getTemplateMode());
        assertEquals(TemplateMode.CSS.name(), templateMode);
    }

}
