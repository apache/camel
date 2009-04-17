/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.itest.osgi;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.*;
import org.ops4j.pax.exam.Inject;
import static org.ops4j.pax.exam.MavenUtils.asInProject;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.logProfile;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.MavenUrlProvisionOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * @version $Revision: 1.1 $
 */
@RunWith(JUnit4TestRunner.class)
public class OSGiIntegrationTest {
    private static final transient Log LOG = LogFactory.getLog(OSGiIntegrationTest.class);

    @Inject
    BundleContext bundleContext;

    @Test
    public void listBundles() throws Exception {
        LOG.info("************ Hello from OSGi ************");

        for (Bundle b : bundleContext.getBundles()) {
            LOG.info("Bundle " + b.getBundleId() + " : " + b.getSymbolicName());
        }

        // TODO we should be using Camel OSGi really to deal with class loader issues
        CamelContext camelContext = new DefaultCamelContext();

        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:foo").to("seda:bar");
            }
        });

        camelContext.start();

        LOG.info("CamelContext started");

        Thread.sleep(2000);

        LOG.info("CamelContext stopping");

        camelContext.stop();

        LOG.info("CamelContext stopped");
    }

    @Configuration
    public static Option[] configure() {
        return options(
                // install log service using pax runners profile abstraction (there are more profiles, like DS)
                logProfile(),

                // this is how you set the default log level when using pax logging (logProfile)
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),

                // TODO why can't we find these from the maven pom.xml with transitive dependency?
                mavenBundleAsInProject("org.apache.camel", "camel-core"),

                wrappedBundle(mavenBundleAsInProject("commons-logging", "commons-logging")),

                felix(), equinox());
    }

    /**
     * Adds a maven bundle for the given groupId and artifactId while deducing the version to use
     * from the <code>target/classes/META-INF/maven/dependencies.properties</code> file that is
     * generated via the
     * <a href="http://wiki.ops4j.org/display/paxexam/Pax+Exam+-+Tutorial+1">depends-maven-plugin
     * from ServiceMix</a>
     */
    public static MavenUrlProvisionOption mavenBundleAsInProject(String groupId, String artifactId) {
        return mavenBundle().groupId(groupId).artifactId(artifactId).version(asInProject());
    }
}