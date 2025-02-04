/*
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
package org.apache.camel.spring.processor;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class SpringTestHelper {

    public static final String PROPERTY_TEST_DIR = "testDirectory";

    private SpringTestHelper() {
    }

    public static CamelContext createSpringCamelContext(ContextTestSupport test, String classpathUri) throws Exception {
        return createSpringCamelContext(test, classpathUri, Collections.emptyMap());
    }

    public static CamelContext createSpringCamelContext(ContextTestSupport test, String classpathUri, Map<String, Object> beans)
            throws Exception {
        test.setUseRouteBuilder(false);

        boolean isNoStart = DefaultCamelContext.isNoStart();
        final AbstractXmlApplicationContext applicationContext;
        try {
            DefaultCamelContext.setNoStart(true);
            applicationContext = new ClassPathXmlApplicationContext(new String[] { classpathUri }, false);
            applicationContext.getEnvironment().getSystemProperties().put(
                    PROPERTY_TEST_DIR, test.testDirectory().toString());
            applicationContext.refresh();
        } finally {
            DefaultCamelContext.setNoStart(isNoStart);
        }
        test.setCamelContextService(new Service() {
            public void start() {
                applicationContext.start();
            }

            public void stop() {
                applicationContext.stop();
            }
        });
        SpringCamelContext context = SpringCamelContext.springCamelContext(applicationContext, false);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            context.getCamelContextExtension().getRegistry().bind(entry.getKey(), entry.getValue());
        }
        context.getCamelContextExtension().getRegistry().bind(PROPERTY_TEST_DIR, test.testDirectory());
        context.getPropertiesComponent().addInitialProperty(PROPERTY_TEST_DIR, test.testDirectory().toString());
        if (!isNoStart) {
            context.start();
        }

        return context;
    }
}
