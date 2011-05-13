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

import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedBrowseableEndpointAsXmlTest extends ManagementTestSupport {

    public void testBrowseableEndpointAsXml() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(7);

        template.sendBody("direct:start", "<foo>Camel &gt; Donkey</foo>");
        template.sendBody("direct:start", "Camel > Donkey");
        template.sendBodyAndHeader("direct:start", "<foo>Camel &gt; Donkey</foo>", "name", "Me & You");
        template.sendBodyAndHeader("direct:start", "<foo>Camel &gt; Donkey</foo>", "title", "<title>Me &amp; You</title>");
        template.sendBodyAndHeader("direct:start", "Camel > Donkey", "name", "Me & You");
        template.sendBodyAndHeader("direct:start", 123, "user", true);
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("user", false);
        headers.put("uid", 123);
        headers.put("title", "Camel rocks");
        template.sendBodyAndHeaders("direct:start", "<animal><name>Donkey</name><age>17</age></animal>", headers);

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{0}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<body type=\"java.lang.String\"><foo>Camel &gt; Donkey</foo></body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{1}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<body type=\"java.lang.String\">Camel &gt; Donkey</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{2}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<headers>\n<header key=\"name\" type=\"java.lang.String\">Me &amp; You</header>\n</headers>\n"
                + "<body type=\"java.lang.String\"><foo>Camel &gt; Donkey</foo></body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{3}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<headers>\n<header key=\"title\" type=\"java.lang.String\"><title>Me &amp; You</title></header>\n</headers>\n"
                + "<body type=\"java.lang.String\"><foo>Camel &gt; Donkey</foo></body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{4}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<headers>\n<header key=\"name\" type=\"java.lang.String\">Me &amp; You</header>\n</headers>\n"
                + "<body type=\"java.lang.String\">Camel &gt; Donkey</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{5}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<headers>\n<header key=\"user\" type=\"java.lang.Boolean\">true</header>\n</headers>\n"
                + "<body type=\"java.lang.Integer\">123</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{6}, new String[]{"java.lang.Integer"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message>\n<headers>\n<header key=\"title\" type=\"java.lang.String\">Camel rocks</header>\n"
                + "<header key=\"uid\" type=\"java.lang.Integer\">123</header>\n"
                + "<header key=\"user\" type=\"java.lang.Boolean\">false</header>\n</headers>\n"
                + "<body type=\"java.lang.String\"><animal><name>Donkey</name><age>17</age></animal></body>\n</message>", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseBreadcrumb(false);

                from("direct:start").to("mock:result");
            }
        };
    }

}
