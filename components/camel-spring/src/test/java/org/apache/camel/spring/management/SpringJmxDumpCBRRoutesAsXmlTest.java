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
package org.apache.camel.spring.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringJmxDumpCBRRoutesAsXmlTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/SpringJmxDumpCBRRouteAsXmlTest.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    public void testJmxDumpCBRRoutesAsXml() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");
        String xml = (String) mbeanServer.invoke(on, "dumpRoutesAsXml", null, null);
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("myRoute"));
        assertTrue(xml.contains("<when id=\"when1\">"));
        assertTrue(xml.contains("<otherwise id=\"otherwise1\">"));
        assertTrue(xml.contains("<route customId=\"true\" id=\"myRoute\">") || xml.contains("<route id=\"myRoute\" customId=\"true\">"));
        assertTrue(xml.contains("<choice customId=\"true\" id=\"myChoice\">") || xml.contains("<choice id=\"myChoice\" customId=\"true\">"));
    }

}
