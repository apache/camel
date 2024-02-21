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
package org.apache.camel.component.log;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LogComponentOptionsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testFastLogComponentOptions() throws Exception {
        context.start();

        long before = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withConfigurer(log.getComponentPropertyConfigurer())
                .withProperty("exchangeFormatter", myFormatter).bind();

        assertSame(myFormatter, log.getExchangeFormatter());

        long after = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        assertEquals(before, after, "Should not use Java reflection");
    }

    @Test
    public void testFastLogComponentNestedOptions() throws Exception {
        context.start();

        long before = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withConfigurer(log.getComponentPropertyConfigurer())
                .withProperty("exchangeFormatter", myFormatter)
                .withProperty("exchangeFormatter.showExchangeId", "true").bind();

        assertSame(myFormatter, log.getExchangeFormatter());

        long after = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        assertTrue(after > before, "Should use Java reflection");
    }

    @Test
    public void testFastLogComponentOptionsLookupRegistry() throws Exception {
        context.start();

        long before = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();
        context.getRegistry().bind("myGreatFormatter", myFormatter);
        DefaultExchangeFormatter mySecondFormatter = new DefaultExchangeFormatter();
        context.getRegistry().bind("mySecondFormatter", mySecondFormatter);

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withConfigurer(log.getComponentPropertyConfigurer())
                .withProperty("exchangeFormatter", "#bean:myGreatFormatter").bind();

        assertSame(myFormatter, log.getExchangeFormatter());

        long after = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        assertEquals(before, after, "Should not use Java reflection");
    }

    @Test
    public void testSlowLogComponentOptions() throws Exception {
        context.start();

        long before = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withProperty("exchangeFormatter", myFormatter)
                .withProperty("exchangeFormatter.showExchangeId", "true").bind();

        assertSame(myFormatter, log.getExchangeFormatter());
        assertTrue(myFormatter.isShowExchangeId());

        long after = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        assertTrue(after > before, "Should use reflection");
    }

    @Test
    public void testSlowLogComponentOptionsLookupRegistry() throws Exception {
        context.start();

        long before = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();
        context.getRegistry().bind("myGreatFormatter", myFormatter);
        DefaultExchangeFormatter mySecondFormatter = new DefaultExchangeFormatter();
        context.getRegistry().bind("mySecondFormatter", mySecondFormatter);

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withProperty("exchangeFormatter", "#bean:myGreatFormatter")
                .withProperty("exchangeFormatter.showExchangeId", "true").bind();

        assertSame(myFormatter, log.getExchangeFormatter());
        assertTrue(myFormatter.isShowExchangeId());

        long after = PluginHelper.getBeanIntrospection(context).getInvokedCounter();

        assertTrue(after > before, "Should use reflection");
    }
}
