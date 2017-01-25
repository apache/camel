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
package org.apache.camel.management;

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedDataFormatTest extends ManagementTestSupport {

    public void testManageDataFormat() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        // there should be 1 data format
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=dataformats,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        String json = (String) mbeanServer.invoke(on, "informationJson", null, null);
        assertNotNull(json);

        assertTrue(json.contains("\"title\": \"String Encoding\""));
        assertTrue(json.contains("\"modelJavaType\": \"org.apache.camel.model.dataformat.StringDataFormat\""));
        assertTrue(json.contains("\"charset\": { \"kind\": \"attribute\""));
        assertTrue(json.contains("\"value\": \"iso-8859-1\""));

        TabularData data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{true}, new String[]{"boolean"});
        assertNotNull(data);
        assertEquals(3, data.size());

        data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{false}, new String[]{"boolean"});
        assertNotNull(data);
        assertEquals(1, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:test")
                    .unmarshal().string("iso-8859-1")
                        .to("mock:result");
            }
        };
    }

}