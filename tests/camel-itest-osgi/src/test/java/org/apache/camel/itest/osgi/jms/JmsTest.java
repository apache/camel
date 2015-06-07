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
package org.apache.camel.itest.osgi.jms;

import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.UrlReference;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class JmsTest extends OSGiIntegrationSpringTestSupport {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/jms/CamelContext.xml"});
    }

    public static UrlReference getActiveMQKarafFeatureUrl(String version) {
        String type = "xml/features";
        MavenArtifactProvisionOption mavenOption = mavenBundle().groupId("org.apache.activemq").artifactId("activemq-karaf");
        if (version == null) {
            return mavenOption.versionAsInProject().type(type);
        } else {
            return mavenOption.version(version).type(type);
        }
    }

    @Test
    public void testJms() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("activemq:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
                
            // using the features to install AMQ
            scanFeatures(getActiveMQKarafFeatureUrl("5.10.1"), "activemq"),

            // using the features to install the camel components
            loadCamelFeatures("camel-jms"));

        return options;
    }

}
