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
import java.util.List;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedBrowsableEndpointAsXmlTest extends ManagementTestSupport {

    public void testBrowseableEndpointAsXmlIncludeBody() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

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

        List<Exchange> exchanges = getMockEndpoint("mock:result").getReceivedExchanges();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{0, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertEquals("<message exchangeId=\"" + exchanges.get(0).getExchangeId() + "\">\n  <body type=\"java.lang.String\">&lt;foo&gt;Camel &amp;gt; Donkey&lt;/foo&gt;</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{1, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n  <body type=\"java.lang.String\">Camel &gt; Donkey</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{2, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(2).getExchangeId() + "\">\n  <headers>\n    <header key=\"name\" type=\"java.lang.String\">Me &amp; You</header>\n  </headers>\n"
                + "  <body type=\"java.lang.String\">&lt;foo&gt;Camel &amp;gt; Donkey&lt;/foo&gt;</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{3, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(3).getExchangeId() + "\">\n  <headers>\n"
                + "    <header key=\"title\" type=\"java.lang.String\">&lt;title&gt;Me &amp;amp; You&lt;/title&gt;</header>\n  </headers>\n"
                + "  <body type=\"java.lang.String\">&lt;foo&gt;Camel &amp;gt; Donkey&lt;/foo&gt;</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{4, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(4).getExchangeId() + "\">\n  <headers>\n    <header key=\"name\" type=\"java.lang.String\">Me &amp; You</header>\n  </headers>\n"
                + "  <body type=\"java.lang.String\">Camel &gt; Donkey</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{5, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(5).getExchangeId() + "\">\n  <headers>\n    <header key=\"user\" type=\"java.lang.Boolean\">true</header>\n  </headers>\n"
                + "  <body type=\"java.lang.Integer\">123</body>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{6, true}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(6).getExchangeId() + "\">\n  <headers>\n    <header key=\"title\" type=\"java.lang.String\">Camel rocks</header>\n"
                + "    <header key=\"uid\" type=\"java.lang.Integer\">123</header>\n"
                + "    <header key=\"user\" type=\"java.lang.Boolean\">false</header>\n  </headers>\n"
                + "  <body type=\"java.lang.String\">&lt;animal&gt;&lt;name&gt;Donkey&lt;/name&gt;&lt;age&gt;17&lt;/age&gt;&lt;/animal&gt;</body>\n</message>", out);
    }

    public void testBrowseableEndpointAsXml() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:result").getReceivedExchanges();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{0, false}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertEquals("<message exchangeId=\"" + exchanges.get(0).getExchangeId() + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n  </headers>\n</message>", out);

        out = (String) mbeanServer.invoke(name, "browseMessageAsXml", new Object[]{1, false}, new String[]{"java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);
        assertEquals("<message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">456</header>\n  </headers>\n</message>", out);
    }

    public void testBrowseableEndpointAsXmlAllIncludeBody() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:result").getReceivedExchanges();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseAllMessagesAsXml", new Object[]{true}, new String[]{"java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertEquals("<messages>\n<message exchangeId=\"" + exchanges.get(0).getExchangeId() + "\">\n  <body type=\"java.lang.String\">Hello World</body>\n</message>\n"
                + "<message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">456</header>\n  </headers>\n"
                + "  <body type=\"java.lang.String\">Bye World</body>\n</message>\n</messages>", out);
    }

    public void testBrowseableEndpointAsXmlAll() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        List<Exchange> exchanges = getMockEndpoint("mock:result").getReceivedExchanges();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseAllMessagesAsXml", new Object[]{false}, new String[]{"java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertEquals("<messages>\n<message exchangeId=\"" + exchanges.get(0).getExchangeId() + "\">\n  <headers>\n"
                + "    <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n  </headers>\n</message>\n"
                + "<message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">456</header>\n  </headers>\n"
                + "</message>\n</messages>", out);
    }

    public void testBrowseableEndpointAsXmlRangeIncludeBody() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(3);

        template.sendBody("direct:start", "Hello World");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);
        template.sendBody("direct:start", "Hi Camel");

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:result").getReceivedExchanges();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseRangeMessagesAsXml", new Object[]{0, 1, true}, new String[]{"java.lang.Integer", "java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertEquals("<messages>\n<message exchangeId=\"" + exchanges.get(0).getExchangeId() + "\">\n  <body type=\"java.lang.String\">Hello World</body>\n</message>\n"
                + "<message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">456</header>\n  </headers>\n"
                + "  <body type=\"java.lang.String\">Bye World</body>\n</message>\n</messages>", out);
    }

    public void testBrowseableEndpointAsXmlRange() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(3);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);
        template.sendBody("direct:start", "Hi Camel");

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = getMockEndpoint("mock:result").getReceivedExchanges();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        String out = (String) mbeanServer.invoke(name, "browseRangeMessagesAsXml", new Object[]{0, 1, false}, new String[]{"java.lang.Integer", "java.lang.Integer", "java.lang.Boolean"});
        assertNotNull(out);
        log.info(out);

        assertEquals("<messages>\n<message exchangeId=\"" + exchanges.get(0).getExchangeId() 
                + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">123</header>\n  </headers>\n</message>\n"
                + "<message exchangeId=\"" + exchanges.get(1).getExchangeId() + "\">\n  <headers>\n    <header key=\"foo\" type=\"java.lang.Integer\">456</header>\n  </headers>\n"
                + "</message>\n</messages>", out);
    }

    public void testBrowseableEndpointAsXmlRangeInvalidIndex() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");

        try {
            mbeanServer.invoke(name, "browseRangeMessagesAsXml", new Object[]{3, 1, false}, new String[]{"java.lang.Integer", "java.lang.Integer", "java.lang.Boolean"});
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("From index cannot be larger than to index, was: 3 > 1", e.getCause().getMessage());
        }
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
