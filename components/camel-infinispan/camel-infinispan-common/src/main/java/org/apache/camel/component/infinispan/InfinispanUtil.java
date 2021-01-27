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
package org.apache.camel.component.infinispan;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;

public class InfinispanUtil {
    protected InfinispanUtil() {
    }

    public static boolean isInHeaderEmpty(Exchange exchange, String header) {
        return isHeaderEmpty(exchange.getMessage(), header);
    }

    public static boolean isHeaderEmpty(Message message, String header) {
        return ObjectHelper.isEmpty(message.getHeader(header));
    }

    public static Properties loadProperties(CamelContext camelContext, String uri) throws Exception {
        try (InputStream is = openInputStream(camelContext, uri)) {
            Properties properties = new Properties();
            properties.load(is);

            return properties;
        } catch (IOException e) {
        }

        throw new FileNotFoundException("Cannot find resource: " + uri);
    }

    public static InputStream openInputStream(CamelContext camelContext, String uri) throws Exception {
        if (camelContext != null) {
            uri = camelContext.resolvePropertyPlaceholders(uri);
            return ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri);
        }

        return Thread.currentThread().getContextClassLoader().getResourceAsStream(uri);
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(CamelContext camelContext, Object source) {
        return camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(
                source,
                source.getClass().getSimpleName());
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(
            CamelContextAware camelContextAware, Object source) {
        return newSingleThreadScheduledExecutor(camelContextAware.getCamelContext(), source);
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(
            CamelContext camelContext, Object source, String id) {
        return camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(
                source,
                source.getClass().getSimpleName() + "-" + id);
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(
            CamelContextAware camelContextAware, Object source, String id) {
        return newSingleThreadScheduledExecutor(camelContextAware.getCamelContext(), source, id);
    }
}
