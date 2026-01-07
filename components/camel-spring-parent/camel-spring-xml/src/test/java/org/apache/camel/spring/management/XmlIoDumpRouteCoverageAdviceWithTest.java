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
package org.apache.camel.spring.management;

import java.io.InputStream;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.w3c.dom.Document;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.util.IOHelper;
import org.apache.camel.xml.LwModelHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementAgent.DEFAULT_DOMAIN;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_CONTEXT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_NAME;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_TYPE;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class XmlIoDumpRouteCoverageAdviceWithTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testDumpRouteCoverage() throws Exception {
        InputStream is = this.getClass()
                .getResourceAsStream("XmlIoDumpRouteCoverageAdviceWithTest.xml");
        RoutesDefinition routes = LwModelHelper.loadRoutesDefinition(is);
        context.addRouteDefinition(routes.getRoutes().get(0));
        IOHelper.close(is);

        AdviceWith.adviceWith(context, "hello-process-pipeline", advice -> {
            advice.weaveById("target-id").replace().to("mock:hello");
        });

        context.start();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getContextObjectName();

        getMockEndpoint("mock:hello").expectedMessageCount(1);
        template.sendBody("direct:hello-process", "Hello World");
        assertMockEndpointsSatisfied();

        String xml = (String) mbeanServer.invoke(on, "dumpRoutesCoverageAsXml", null, null);
        log.info(xml);

        // advice replaced <log> -> <to> which we should find in the XML dump
        assertTrue(xml.contains("<to exchangesTotal=\"1\""));
        assertTrue(xml.contains("uri=\"mock:hello\""));
        assertTrue(xml.contains("sourceLineNumber=\"24\""));

        // should be valid XML
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);
        assertNotNull(doc);
    }

    private ObjectName getContextObjectName() throws MalformedObjectNameException {
        return getCamelObjectName(TYPE_CONTEXT, context.getName());
    }

    private ObjectName getCamelObjectName(String type, String name) throws MalformedObjectNameException {
        String quote = "\"";
        String on = DEFAULT_DOMAIN + ":"
                    + KEY_CONTEXT + "=" + context.getManagementName() + ","
                    + KEY_TYPE + "=" + type + ","
                    + KEY_NAME + "=" + quote + name + quote;
        return ObjectName.getInstance(on);
    }

}
