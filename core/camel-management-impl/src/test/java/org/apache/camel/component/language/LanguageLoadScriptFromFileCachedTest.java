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
package org.apache.camel.component.language;

import java.util.ArrayList;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class LanguageLoadScriptFromFileCachedTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/script");
        super.setUp();
    }
    
    @Override
    public boolean useJmx() {
        return true;
    }

    @Test
    public void testLanguage() throws Exception {
        // create script to start with
        template.sendBodyAndHeader("file:target/data/script", "Hello ${body}", Exchange.FILE_NAME, "myscript.txt");

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello World");

        template.sendBody("direct:start", "World");

        // even if we update the file the content is cached
        template.sendBodyAndHeader("file:target/data/script", "Bye ${body}", Exchange.FILE_NAME, "myscript.txt");
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testClearCachedScriptViaJmx() throws Exception {
        // create script to start with
        template.sendBodyAndHeader("file:target/data/script", "Hello ${body}", Exchange.FILE_NAME, "myscript.txt");

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello World", "Bye World");

        template.sendBody("direct:start", "World");

        // even if we update the file the content is cached
        template.sendBodyAndHeader("file:target/data/script", "Bye ${body}", Exchange.FILE_NAME, "myscript.txt");
        template.sendBody("direct:start", "World");

        // now clear the cache via the mbean server
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        Set<ObjectName> objNameSet = mbeanServer.queryNames(
            new ObjectName("org.apache.camel:type=endpoints,name=\"language://simple:*contentCache=true*\",*"), null);
        ObjectName managedObjName = new ArrayList<>(objNameSet).get(0);
        mbeanServer.invoke(managedObjName, "clearContentCache", null, null);

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // use content cache to load the script once and cache it (content cache and script cache both enabled)
                    .to("language:simple:file:target/data/script/myscript.txt?contentCache=true&cacheScript=true")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
