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
package org.apache.camel.itest.springboot;

import java.net.URL;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Contains the main class of the sample spring-boot application created for the 
 * module under test.
 *
 */
@SpringBootApplication
@EnableAsync
@Import(ITestXmlConfiguration.class)
public class ITestApplication {

    public static void main(String[] args) throws Exception {

        try {
            overrideLoggingConfig();

            SpringApplication.run(ITestApplication.class, args);
        } catch (Throwable t) {
            LoggerFactory.getLogger(ITestApplication.class).error("Error while executing test", t);
            throw t;
        }
    }

    @Override
    public String toString() {
        // to tell source-check this is not a utility-class
        return "spring-boot-main";
    }

    private static void overrideLoggingConfig() {

        URL logbackFile = ITestApplication.class.getResource("/spring-logback.xml");
        if (logbackFile != null) {

            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            try {
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(context);
                // Call context.reset() to clear any previous configuration, e.g. default
                // configuration. For multi-step configuration, omit calling context.reset().
                context.reset();
                configurator.doConfigure(logbackFile);
            } catch (JoranException je) {
                // StatusPrinter will handle this
            }
            StatusPrinter.printInCaseOfErrorsOrWarnings(context);
        }

    }
}
