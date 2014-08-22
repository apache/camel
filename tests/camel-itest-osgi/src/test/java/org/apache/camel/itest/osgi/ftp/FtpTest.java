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
package org.apache.camel.itest.osgi.ftp;

import org.apache.camel.Exchange;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
@Ignore("Not fully implemented, see TODO")
public class FtpTest extends OSGiIntegrationSpringTestSupport {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/ftp/CamelContext.xml"});
    }

    @Test
    public void testFtp() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("ftp://localhost:21002?username=admin&password=admin", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(

            getDefaultCamelKarafOptions(),
            // using the features to install the camel components
            loadCamelFeatures("jetty", "camel-ftp"),

            // ftp server bundles
            mavenBundle().groupId("org.apache.mina").artifactId("mina-core").version("2.0.0"),
            mavenBundle().groupId("org.apache.ftpserver").artifactId("ftpserver-core").version("1.0.5"),
            mavenBundle().groupId("org.apache.ftpserver").artifactId("ftplet-api").version("1.0.5"),

            /*felix(),*/ equinox());
        
        return options;
    }

}