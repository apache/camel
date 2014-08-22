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
package org.apache.camel.itest.osgi.servlet;

import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class ServletComponentTest extends OSGiIntegrationSpringTestSupport {
    
    private static final String CONTEXT_PATH = "/org/apache/camel/itest/osgi/servlet/ServletComponentTest-context.xml";

    @Test
    public void testSendMessage() {
        String endpointURI = "http://localhost:9080/camel/services/hello";
        String response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("Echo Hello World", response);
    }
    
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // install the war features first
            scanFeatures(getKarafFeatureUrl(),  "war"),
            // set the system property for pax web
            KarafDistributionOption.editConfigurationFilePut("etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", "9080"),

            // using the features to install the camel components
            loadCamelFeatures("camel-blueprint", "camel-http", "camel-servlet")

        );
        return options;
    }
    
    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[] {CONTEXT_PATH});
    }

}
