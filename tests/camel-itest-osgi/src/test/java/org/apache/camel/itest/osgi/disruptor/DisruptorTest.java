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
package org.apache.camel.itest.osgi.disruptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileExtendOption;

import static org.ops4j.pax.exam.OptionUtils.combine;


/**
 * @version 
 */
@RunWith(PaxExam.class)
public class DisruptorTest extends OSGiIntegrationTestSupport {
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("disruptor:foo").to("mock:bar");
            }
        };
    }

    @Test
    public void testSendMessage() throws Exception {
        MockEndpoint mock =  getMandatoryEndpoint("mock:bar", MockEndpoint.class);
        assertNotNull("The mock endpoint should not be null", mock);
        
        mock.expectedBodiesReceived("Hello World");
        template.sendBody("disruptor:foo", "Hello World");
        assertMockEndpointsSatisfied();        
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // disruptor requires sun.misc packages
            new KarafDistributionConfigurationFileExtendOption("etc/jre.properties", "jre-1.6", ",sun.misc"),
            new KarafDistributionConfigurationFileExtendOption("etc/jre.properties", "jre-1.7", ",sun.misc"),
            new KarafDistributionConfigurationFileExtendOption("etc/jre.properties", "jre-1.8", ",sun.misc"),
            // using the features to install the other camel components
            loadCamelFeatures("camel-disruptor"));
        
        return options;
    }
   
}