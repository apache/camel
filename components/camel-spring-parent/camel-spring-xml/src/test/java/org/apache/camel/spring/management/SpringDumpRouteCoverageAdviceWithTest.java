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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class SpringDumpRouteCoverageAdviceWithTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/spring/management/SpringDumpRouteCoverageAdviceWithTest.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testDumpRouteCoverage() throws Exception {
        AdviceWith.adviceWith(context, "hello-process-pipeline", advice -> {
            advice.weaveById("target-id").replace().to("mock:hello").id("target-id");
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

        assertTrue(xml.contains("exchangesTotal=\"1\""));

        // should be valid XML
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);
        assertNotNull(doc);
    }

}
