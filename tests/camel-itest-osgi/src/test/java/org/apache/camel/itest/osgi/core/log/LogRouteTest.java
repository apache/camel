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
package org.apache.camel.itest.osgi.core.log;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.karaf.tooling.exam.options.DoNotModifyLogOption;
import org.apache.karaf.tooling.exam.options.KarafDistributionConfigurationFileReplacementOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.slf4j.LoggerFactory;

import static org.ops4j.pax.exam.OptionUtils.*;

@RunWith(JUnit4TestRunner.class)
public class LogRouteTest extends OSGiIntegrationTestSupport {

    @Test
    public void testSendMessageToLog() throws Exception {
        template.sendBody("log:org.apache.camel.TEST", "<level>default</level>");
    }

    @Test
    public void testSendMessageToInfoLog() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?level=INFO", "<level>INFO</level>");
    }

    @Test
    public void testSendMessageToWarnLog() throws Exception {
        template.sendBody("log:org.apache.camel.TEST?level=warn", "<level>WARN</level>");
    }

    @Test
    public void testSendMessageToInfoLogWithSiftLogging() throws Exception {
        template.sendBody("log:org.apache.camel.SIFT?level=info&logger=#mylogger", "<level>INFO</level>");
        template.sendBody("log:org.apache.camel.SIFT?level=debug&logger=#mylogger", "<level>DEBUG</level>");
        template.sendBody("log:org.apache.camel.SIFT?level=info&logger=#mylogger", "<level>INFO</level>");
        template.sendBody("log:org.apache.camel.SIFT?level=error&logger=#mylogger", "<level>ERROR</level>");
    }

    @Test
    public void testSendMessageToBadLevel() throws Exception {
        try {
            template.sendBody("log:org.apache.camel.TEST?level=noSuchLevel", "<level>noSuchLevel</level>");
            fail("Shoudl have failed!");
        } catch (Exception e) {
            LOG.debug("Caught expected exception: " + e, e);
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setApplicationContextClassLoader(getClass().getClassLoader());
        camelContext.setUseMDCLogging(true);
        return camelContext;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("mylogger", LoggerFactory.getLogger("org.apache.camel.SIFT"));
        return registry;
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            new Option[] {
                new DoNotModifyLogOption(),
                new KarafDistributionConfigurationFileReplacementOption("etc/org.ops4j.pax.logging.cfg", new File("src/test/resources/log4j.properties")),
//                KarafDistributionOption.debugConfiguration("9999", true)
            });
        return options;
    }


}
