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

package org.apache.camel.support.resume;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdapterHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AdapterHelper.class);
    private static final String ADAPTER_PROPERTIES = "/org/apache/camel/resume/adapter.properties";
    private static final String PROP_ADAPTER_CLASS = "adapterClass";

    private AdapterHelper() {
    }

    public static ResumeAdapter eval(CamelContext context, Consumer consumer) {
        assert context != null;
        assert consumer != null;

        Object adapterInstance = context.getRegistry().lookupByName("resumeAdapter");
        if (adapterInstance == null) {
            adapterInstance = resolveAdapter(context, consumer);

            if (adapterInstance == null) {
                throw new RuntimeException("Cannot find a resume adapter class in the consumer classpath or in the registry");
            }
        }

        if (adapterInstance instanceof ResumeAdapter) {
            ResumeAdapter resumeAdapter = (ResumeAdapter) adapterInstance;

            Object obj = context.getRegistry().lookupByName("resumeCache");
            if (resumeAdapter instanceof Cacheable && obj instanceof ResumeCache) {
                ((Cacheable) resumeAdapter).setCache((ResumeCache<?>) obj);
            } else {
                LOG.debug("The resume adapter {} is not cacheable", resumeAdapter.getClass().getName());
            }

            return resumeAdapter;
        } else {
            LOG.error("Invalid resume adapter type: {}", getType(adapterInstance));
            throw new IllegalArgumentException("Invalid resume adapter type: " + getType(adapterInstance));
        }
    }

    private static Object resolveAdapter(CamelContext context, Consumer consumer) {
        try (InputStream adapterStream = consumer.getClass().getResourceAsStream(ADAPTER_PROPERTIES)) {

            if (adapterStream == null) {
                LOG.error("Cannot find a resume adapter class in the consumer {} classpath", consumer.getClass());
                return null;
            }

            Properties properties = new Properties();
            properties.load(adapterStream);

            String adapterClass = properties.getProperty(PROP_ADAPTER_CLASS);

            if (ObjectHelper.isEmpty(adapterClass)) {
                LOG.error("A resume adapter class is not defined in the adapter configuration");

                return null;
            }

            LOG.debug("About to load an adapter class {} for consumer {}", adapterClass, consumer.getClass());
            Class<?> clazz = context.getClassResolver().resolveClass(adapterClass);
            if (clazz == null) {
                LOG.error("Cannot find the resume adapter class in the classpath {}", adapterClass);

                return null;
            }

            return clazz.getDeclaredConstructor().newInstance();
        } catch (IOException e) {
            LOG.error("Unable to read the resolve the resume adapter due to I/O error: {}", e.getMessage(), e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            LOG.error("Unable to create a resume adapter instance: {}", e.getMessage(), e);
        }

        return null;
    }

    private static Object getType(Object instance) {
        return instance == null ? "null" : instance.getClass();
    }
}
