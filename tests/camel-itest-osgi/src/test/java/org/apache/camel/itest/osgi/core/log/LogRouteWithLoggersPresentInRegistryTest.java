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
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.osgi.CamelContextFactory;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.DoNotModifyLogOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileReplacementOption;
import org.slf4j.LoggerFactory;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class LogRouteWithLoggersPresentInRegistryTest extends OSGiIntegrationTestSupport {

    @Test
    public void testSendMessageToProvidedLogggerWithSiftLogging() throws Exception {
        template.sendBody("log:irrelevant.logger.name?level=info&logger=#mylogger1", "<level>INFO</level>");
        template.sendBody("log:irrelevant.logger.name?level=debug&logger=#mylogger1", "<level>DEBUG</level>");
        template.sendBody("log:irrelevant.logger.name?level=error&logger=#mylogger1", "<level>ERROR</level>");

        File logDir = new File(System.getProperty("karaf.base"), "data/log");
        File[] files = logDir.listFiles();
        assertEquals(1, files.length);
        assertEquals(bundleContext.getBundle().getSymbolicName() + ".log", files[0].getName());
    }

    @Test
    public void testSendMessageToCamelCreatedLogggerWithSiftLogging() throws Exception {
        template.sendBody("log:org.apache.camel.SIFT.relevant.logger.name?level=info", "<level>INFO</level>");
        template.sendBody("log:org.apache.camel.SIFT.relevant.logger.name?level=debug", "<level>DEBUG</level>");
        template.sendBody("log:org.apache.camel.SIFT.relevant.logger.name?level=error", "<level>ERROR</level>");

        File logDir = new File(System.getProperty("karaf.base"), "data/log");
        File[] files = logDir.listFiles();
        assertEquals(1, files.length);
        assertNotEquals(bundleContext.getBundle().getSymbolicName() + ".log", files[0].getName());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        LOG.info("Get the bundleContext is {}", bundleContext);
        LOG.info("Application installed as bundle id: {}", bundleContext.getBundle().getBundleId());

        setThreadContextClassLoader();

        CamelContextFactory factory = new CamelContextFactory();
        factory.setBundleContext(bundleContext);
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("mylogger1", LoggerFactory.getLogger("org.apache.camel.SIFT.l1"));
        registry.put("mylogger2", LoggerFactory.getLogger("org.apache.camel.SIFT.l2"));
        factory.setRegistry(registry);
        CamelContext camelContext = factory.createContext();
        camelContext.setApplicationContextClassLoader(getClass().getClassLoader());
        camelContext.setUseMDCLogging(true);
        return camelContext;
    }

    @Configuration
    public static Option[] configure() throws Exception {
        return combine(
            getDefaultCamelKarafOptions(),
            new DoNotModifyLogOption(),
            new KarafDistributionConfigurationFileReplacementOption("etc/org.ops4j.pax.logging.cfg", new File("src/test/resources/log4j.properties"))
        );
    }

}
