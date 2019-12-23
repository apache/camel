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
package org.apache.camel.component.velocity;

import java.util.ArrayList;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test the cache when reloading .vm files in the classpath
 */
public class VelocityContentCacheTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // create a vm file in the classpath as this is the tricky reloading stuff
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Hello $headers.name", Exchange.FILE_NAME, "hello.vm");
    }
    
    @Override
    public boolean useJmx() {
        return true;
    }

    @Test
    public void testNotCached() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("direct:a", "Body", "name", "London");
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Bye $headers.name", Exchange.FILE_NAME, "hello.vm");

        mock.reset();
        mock.expectedBodiesReceived("Bye Paris", "Bye World");

        template.sendBodyAndHeader("direct:a", "Body", "name", "Paris");
        template.sendBodyAndHeader("direct:a", "Body", "name", "World");
        mock.assertIsSatisfied();
    }

    @Test
    public void testCached() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("direct:b", "Body", "name", "London");
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Bye $headers.name", Exchange.FILE_NAME, "hello.vm");

        mock.reset();
        // we must expected the original filecontent as the cache is enabled, so its Hello and not Bye
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("direct:b", "Body", "name", "Paris");
        mock.assertIsSatisfied();
    }

    @Test
    public void testCachedIsDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("direct:c", "Body", "name", "London");
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Bye $headers.name", Exchange.FILE_NAME, "hello.vm");

        mock.reset();
        // we must expected the original filecontent as the cache is enabled, so its Hello and not Bye
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("direct:c", "Body", "name", "Paris");
        mock.assertIsSatisfied();
    }
    
    @Test
    public void testClearCacheViaJmx() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("direct:b", "Body", "name", "London");
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Bye $headers.name", Exchange.FILE_NAME, "hello.vm");

        mock.reset();
        // we must expected the original filecontent as the cache is enabled, so its Hello and not Bye
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("direct:b", "Body", "name", "Paris");
        mock.assertIsSatisfied();
        
        // clear the cache via the mbean server
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        Set<ObjectName> objNameSet = mbeanServer.queryNames(new ObjectName("org.apache.camel:type=endpoints,name=\"velocity:*contentCache=true*\",*"), null);
        ObjectName managedObjName = new ArrayList<>(objNameSet).get(0);        
        mbeanServer.invoke(managedObjName, "clearContentCache", null, null);
           
        // now change content in the file in the classpath
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Bye $headers.name", Exchange.FILE_NAME, "hello.vm");

        mock.reset();
        // we expect the update to work now since the cache has been cleared
        mock.expectedBodiesReceived("Bye Paris");
        template.sendBodyAndHeader("direct:b", "Body", "name", "Paris");
        mock.assertIsSatisfied();
        
        // change the content in the file again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/velocity?fileExist=Override", "Hello $headers.name", Exchange.FILE_NAME, "hello.vm");
        mock.reset();
        // we expect the new value to be ignored since the cache was re-established with the prior exchange
        mock.expectedBodiesReceived("Bye Paris");
        template.sendBodyAndHeader("direct:b", "Body", "name", "Paris");
        mock.assertIsSatisfied();
    }
    

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("velocity://org/apache/camel/component/velocity/hello.vm?contentCache=false").to("mock:result");

                from("direct:b").to("velocity://org/apache/camel/component/velocity/hello.vm?contentCache=true").to("mock:result");

                from("direct:c").to("velocity://org/apache/camel/component/velocity/hello.vm").to("mock:result");
            }
        };
    }
}
