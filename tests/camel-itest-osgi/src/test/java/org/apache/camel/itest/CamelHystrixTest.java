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
package org.apache.camel.itest;

import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.karaf.AbstractFeatureTest;
import org.apache.camel.test.karaf.CamelKarafTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
@Ignore("Weird osgi linkage error")
public class CamelHystrixTest extends AbstractFeatureTest {

    @Test
    public void testCamelHystrix() throws Exception {
        // install the camel blueprint xml file we use in this test
        URL url = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/CamelHystrixTest.xml", CamelHystrixTest.class.getClassLoader());
        installBlueprintAsBundle("CamelHystrixTest", url, true);

        // lookup Camel from OSGi
        CamelContext camel = getOsgiService(bundleContext, CamelContext.class);

        // test camel
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("Fallback message");

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();
    }

    @Configuration
    public Option[] configure() {
        return CamelKarafTestSupport.configure("camel-test-karaf", "camel-hystrix");
    }

}
