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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.pojo.SayService;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionalPropertyPlaceholderBeanTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testQueryOptionalPresent() throws Exception {
        SayService say = new SayService();
        assertEquals("Hello", say.getMessage());

        PropertyBindingSupport.build().withTarget(say).withProperty("message", "{{?cool.name}}")
                .withCamelContext(context)
                .bind();
        assertEquals("Camel", say.getMessage());
    }

    @Test
    public void testQueryOptionalNotPresent() throws Exception {
        SayService say = new SayService();
        assertEquals("Hello", say.getMessage());

        PropertyBindingSupport.build().withTarget(say).withProperty("message", "{{?unknown}}")
                .withCamelContext(context)
                .bind();
        assertEquals("Hello", say.getMessage());
    }

    @Test
    public void testQueryOptionalNotPresentDefaultValue() throws Exception {
        SayService say = new SayService();
        assertEquals("Hello", say.getMessage());

        PropertyBindingSupport.build().withTarget(say).withProperty("message", "{{?unknown:Bye}}")
                .withCamelContext(context)
                .bind();
        assertEquals("Bye", say.getMessage());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        return context;
    }

}
