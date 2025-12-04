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

package org.apache.camel.management;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class ManagedBrowsableEndpointAsXmlFileTest extends ManagementTestSupport {

    protected String domainName = DefaultManagementAgent.DEFAULT_DOMAIN;

    @Test
    public void testBrowseableEndpointAsXmlAllIncludeBody() throws Exception {
        template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, "hello.txt");

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName objName = new ObjectName(domainName + ":type=endpoints,*");
        Set<ObjectName> s = mbeanServer.queryNames(objName, null);
        Assertions.assertEquals(2, s.size());
        Iterator<ObjectName> it = s.iterator();
        ObjectName name = it.next();
        if (!name.toString().contains("file")) {
            name = it.next();
        }

        String out = (String) mbeanServer.invoke(
                name, "browseAllMessagesAsXml", new Object[] {true}, new String[] {"java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertTrue(out.contains("Hello World</body>"), "Should contain the body");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);

                from("direct:start").to(fileUri());
            }
        };
    }
}
