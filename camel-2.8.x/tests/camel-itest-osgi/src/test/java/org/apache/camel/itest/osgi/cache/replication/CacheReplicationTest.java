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
package org.apache.camel.itest.osgi.cache.replication;

import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.apache.karaf.testing.Helper;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;


import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;



@RunWith(JUnit4TestRunner.class)
public class CacheReplicationTest extends OSGiIntegrationSpringTestSupport {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{
            "org/apache/camel/itest/osgi/cache/replication/JMSReplicationCamelContext.xml"});
    }


    @Test
    public void testCache() throws Exception {
        getMockEndpoint("mock:result1").expectedBodiesReceived("Am I replicated?");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Am I replicated?");

        // do some routes to let everything be initialized
        template.sendBody("direct:getRoute1", "Let initialize the route");
        template.sendBody("direct:getRoute2", "Let initialize the route");
        template.sendBody("direct:addRoute", "Am I replicated?");

        // give some time to make replication
        Thread.sleep(200);

        template.sendBody("direct:getRoute1", "Will I get replicated cache");
        template.sendBody("direct:getRoute2", "Will I get replicated cache");

        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
        // Default karaf environment
                Helper.getDefaultOptions(
                    // this is how you set the default log level when using pax
                    // logging (logProfile)
                    Helper.setLogLevel("WARN")),
                    
                    // install the spring, http features first
                    scanFeatures(getKarafFeatureUrl(), "spring", "spring-dm", "jetty"),

                    // using the features to install AMQ
                    scanFeatures("mvn:org.apache.activemq/activemq-karaf/5.5.0/xml/features",
                            "activemq"),

                    // using the features to install the camel components
                    scanFeatures(getCamelKarafFeatureUrl(),
                            "camel-core", "camel-spring", "camel-test", "camel-jms", "camel-cache"),

                workingDirectory("target/paxrunner/"),

                felix());

        return options;
    }
}