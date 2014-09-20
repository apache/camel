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
package org.apache.camel.itest.osgi.http4;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 *
 */
@RunWith(PaxExam.class)
public class Http4Test extends OSGiIntegrationTestSupport {

    @Test
    public void testHttp4Get() throws Exception {
        String reply = template.requestBody("http4://localhost:9081/foo", null, String.class);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testHttp4Post() throws Exception {
        String reply = template.requestBody("http4://localhost:9081/foo", "Hello World", String.class);
        assertEquals("Bye World", reply);
    }

    @Test
    @Ignore("Requires online internet for testing")
    public void testHttp4Google() throws Exception {
        String reply = template.requestBody("http4://www.google.se", null, String.class);
        assertNotNull(reply);
        log.info(reply);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://0.0.0.0:9081/foo")
                    .transform(constant("Bye World"));
            }
        };
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-http4", "camel-jetty"));
        
        return options;
    }


}
