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
package org.apache.camel.itest.osgi.hl7;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

@RunWith(JUnit4TestRunner.class)
public class HL7MLLPCodecTest extends OSGiIntegrationSpringTestSupport implements Processor {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/hl7/CamelContext.xml"});
    }

    @Test
    public void testSendHL7Message() throws Exception {
        String line1 = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\n");
        in.append(line2);

        String out = (String)template.requestBody("mina:tcp://127.0.0.1:8888?sync=true&codec=#hl7codec", in.toString());

        String[] lines = out.split("\r");
        assertEquals("MSH|^~\\&|MYSENDER||||200701011539||ADR^A19||||123", lines[0]);
        assertEquals("MSA|AA|123", lines[1]);
    }

    public void process(Exchange exchange) throws Exception {
        String out = "MSH|^~\\&|MYSENDER||||200701011539||ADR^A19||||123\rMSA|AA|123\n"; 
        exchange.getOut().setBody(out);
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = options(
            // install the spring dm profile
            profile("spring.dm").version("1.2.0"),
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),

            // using the features to install the camel components
            scanFeatures(getCamelKarafFeatureUrl(),
                          "camel-core", "camel-spring", "camel-test", "camel-mina", "camel-hl7"),

            // add hl7 osgi bundle
            mavenBundle().groupId("http://hl7api.sourceforge.net/m2/!ca.uhn.hapi").artifactId("hapi-osgi-base").version("1.0.1"),

            workingDirectory("target/paxrunner/"),

            felix(), equinox());

        return options;
    }
}