/**
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
package org.apache.camel.util;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.MyBarSingleton;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ModelHelper;
import org.junit.Test;

/**
 *
 */
public class DumpModelAsXmlRouteExpressionTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myCoolBean", new MyBarSingleton());
        return jndi;
    }

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<simple>Hello ${body}</simple>"));
    }

    @Test
    public void testDumpModelAsXmlXPath() throws Exception {
        String xml = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("myOtherRoute"));
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<xpath>/foo</xpath>"));
    }

    @Test
    public void testDumpModelAsXmlHeader() throws Exception {
        String xml = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("myFooRoute"));
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<header>bar</header>"));
    }

    @Test
    public void testDumpModelAsXmlBean() throws Exception {
        String xml = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("myBeanRoute"));
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<setHeader headerName=\"foo\""));
        assertTrue(xml.contains("<method>myCoolBean</method>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute")
                   .setBody(simple("Hello ${body}"))
                   .to("mock:result");

                from("direct:other").routeId("myOtherRoute")
                   .setBody(xpath("/foo"))
                   .to("mock:result");

                from("direct:foo").routeId("myFooRoute")
                   .setBody(header("bar"))
                   .to("mock:result");

                from("direct:bean").routeId("myBeanRoute")
                   .setHeader("foo", method("myCoolBean"))
                   .to("mock:result");
            }
        };
    }

}
