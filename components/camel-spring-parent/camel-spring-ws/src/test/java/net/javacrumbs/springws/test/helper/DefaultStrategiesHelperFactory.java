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
package net.javacrumbs.springws.test.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.support.DefaultStrategiesHelper;
import org.springframework.ws.transport.http.HttpTransportException;
import org.springframework.ws.transport.http.MessageDispatcherServlet;

/**
 * Creates default Spring WS strategies (message factory / message receiver).
 * <p>
 * Re-homed into Camel from the abandoned {@code net.javacrumbs:spring-ws-test} library (last released 2011, Apache
 * License 2.0) so camel-spring-ws no longer depends on it. See CAMEL-23440.
 */
final class DefaultStrategiesHelperFactory {

    private static final String DEFAULT_STRATEGIES_PATH = "MessageDispatcherServlet.properties";

    private static final Log LOGGER = LogFactory.getLog(DefaultStrategiesHelperFactory.class);

    private static final String DEFAULT_MESSAGE_FACTORY_BEAN_NAME
            = MessageDispatcherServlet.DEFAULT_MESSAGE_FACTORY_BEAN_NAME;

    private DefaultStrategiesHelperFactory() {
    }

    static DefaultStrategiesHelper getDefaultStrategiesHelper() {
        // should be MessageDispatcherServlet.class but it would require servlet-api in the classpath, so we use
        // HttpTransportException instead (same package/jar location)
        return new DefaultStrategiesHelper(new ClassPathResource(DEFAULT_STRATEGIES_PATH, HttpTransportException.class));
    }

    static WebServiceMessageFactory createMessageFactory(ApplicationContext applicationContext) {
        if (applicationContext != null && applicationContext.containsBean(DEFAULT_MESSAGE_FACTORY_BEAN_NAME)) {
            return applicationContext.getBean(DEFAULT_MESSAGE_FACTORY_BEAN_NAME, WebServiceMessageFactory.class);
        } else {
            LOGGER.debug("No WebServiceMessageFactory found, using default");
            return (WebServiceMessageFactory) getDefaultStrategiesHelper()
                    .getDefaultStrategy(WebServiceMessageFactory.class, applicationContext);
        }
    }
}
