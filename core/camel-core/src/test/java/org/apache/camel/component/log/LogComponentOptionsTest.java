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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.Test;

public class LogComponentOptionsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testFastLogComponentOptions() throws Exception {
        context.start();

        long before = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withConfigurer(log.getComponentPropertyConfigurer())
                .withProperty("exchangeFormatter", myFormatter).bind();

        assertSame(myFormatter, log.getExchangeFormatter());

        long after = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        assertEquals("Should not use Java reflection", before, after);
    }

    @Test
    public void testFastLogComponentNestedOptions() throws Exception {
        context.start();

        long before = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withConfigurer(log.getComponentPropertyConfigurer())
                .withProperty("exchangeFormatter", myFormatter)
                .withProperty("exchangeFormatter.showExchangeId", "true").bind();

        assertSame(myFormatter, log.getExchangeFormatter());

        long after = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        assertTrue("Should use Java reflection", after > before);
    }

    @Test
    public void testFastLogComponentOptionsLookupRegistry() throws Exception {
        context.start();

        long before = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();
        context.getRegistry().bind("myGreatFormatter", myFormatter);

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withConfigurer(log.getComponentPropertyConfigurer())
                .withProperty("exchangeFormatter", "#bean:myGreatFormatter").bind();

        assertSame(myFormatter, log.getExchangeFormatter());

        long after = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        assertEquals("Should not use Java reflection", before, after);
    }

    @Test
    public void testSlowLogComponentOptions() throws Exception {
        context.start();

        long before = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withProperty("exchangeFormatter", myFormatter)
                .withProperty("exchangeFormatter.showExchangeId", "true").bind();

        assertSame(myFormatter, log.getExchangeFormatter());
        assertTrue(myFormatter.isShowExchangeId());

        long after = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        assertTrue("Should use reflection", after > before);
    }

    @Test
    public void testSlowLogComponentOptionsLookupRegistry() throws Exception {
        context.start();

        long before = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        DefaultExchangeFormatter myFormatter = new DefaultExchangeFormatter();
        context.getRegistry().bind("myGreatFormatter", myFormatter);

        LogComponent log = context.getComponent("log", LogComponent.class);
        assertNull(log.getExchangeFormatter());

        new PropertyBindingSupport.Builder().withCamelContext(context).withTarget(log)
                .withProperty("exchangeFormatter", "#bean:myGreatFormatter")
                .withProperty("exchangeFormatter.showExchangeId", "true").bind();

        assertSame(myFormatter, log.getExchangeFormatter());
        assertTrue(myFormatter.isShowExchangeId());

        long after = context.adapt(ExtendedCamelContext.class).getBeanIntrospection().getInvokedCounter();

        assertTrue("Should use reflection", after > before);
    }
}
