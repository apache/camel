/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl.engine;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class DefaultBeanIntrospection extends ServiceSupport implements BeanIntrospection {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBeanIntrospection.class);
    private static final Pattern SECRETS = Pattern.compile(".*(passphrase|password|secretKey).*", Pattern.CASE_INSENSITIVE);

    private final AtomicLong invoked = new AtomicLong();
    private volatile boolean extendedStatistics;
    private LoggingLevel loggingLevel = LoggingLevel.TRACE;
    private CamelLogger logger = new CamelLogger(LOG, loggingLevel);

    @Override
    public long getInvokedCounter() {
        return invoked.get();
    }

    @Override
    public void resetCounters() {
        invoked.set(0);
    }

    public boolean isExtendedStatistics() {
        return extendedStatistics;
    }

    public void setExtendedStatistics(boolean extendedStatistics) {
        this.extendedStatistics = extendedStatistics;
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
        // recreate logger as level is changed
        this.logger = new CamelLogger(LOG, loggingLevel);
    }

    private void log(String method, Object target, Object... args) {
        Object obj = "null";
        if (args != null && args.length > 0) {
            obj = Arrays.asList(args);
        }
        logger.log("Invoked: " + invoked.get() + " times (overall) [Method: " + method + ", Target: " + target + ", Arguments: " + obj + " ]");
    }

    @Override
    public ClassInfo cacheClass(Class<?> clazz) {
        log("cacheClass", clazz);
        invoked.incrementAndGet();
        return IntrospectionSupport.cacheClass(clazz);
    }

    @Override
    public boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix) {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("getProperties", target);
        }
        return IntrospectionSupport.getProperties(target, properties, optionPrefix);
    }

    @Override
    public boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix, boolean includeNull) {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("getProperties", target);
        }
        return IntrospectionSupport.getProperties(target, properties, optionPrefix, includeNull);
    }

    @Override
    public Object getOrElseProperty(Object target, String propertyName, Object defaultValue) {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("getOrElseProperty", target, propertyName);
        }
        return IntrospectionSupport.getOrElseProperty(target, propertyName, defaultValue);
    }

    @Override
    public Object getOrElseProperty(Object target, String propertyName, Object defaultValue, boolean ignoreCase) {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("getOrElseProperty", target, propertyName);
        }
        return IntrospectionSupport.getOrElseProperty(target, propertyName, defaultValue, ignoreCase);
    }

    @Override
    public Method getPropertyGetter(Class<?> type, String propertyName) throws NoSuchMethodException {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("getPropertyGetter", type, propertyName);
        }
        return IntrospectionSupport.getPropertyGetter(type, propertyName);
    }

    @Override
    public Method getPropertyGetter(Class<?> type, String propertyName, boolean ignoreCase) throws NoSuchMethodException {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("getPropertyGetter", type, propertyName);
        }
        return IntrospectionSupport.getPropertyGetter(type, propertyName, ignoreCase);
    }

    @Override
    public boolean setProperty(CamelContext context, TypeConverter typeConverter, Object target, String name, Object value, String refName, boolean allowBuilderPattern) throws Exception {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            Object text = value;
            if (SECRETS.matcher(name).find()) {
                text = "xxxxxx";
            }
            log("setProperty", target, name, text);
        }
        return IntrospectionSupport.setProperty(context, typeConverter, target, name, value, refName, allowBuilderPattern);
    }

    @Override
    public boolean setProperty(CamelContext context, TypeConverter typeConverter, Object target, String name, Object value, String refName, boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) throws Exception {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            Object text = value;
            if (SECRETS.matcher(name).find()) {
                text = "xxxxxx";
            }
            log("setProperty", target, name, text);
        }
        return IntrospectionSupport.setProperty(context, typeConverter, target, name, value, refName, allowBuilderPattern, allowPrivateSetter, ignoreCase);
    }

    @Override
    public boolean setProperty(CamelContext context, Object target, String name, Object value) throws Exception {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            Object text = value;
            if (SECRETS.matcher(name).find()) {
                text = "xxxxxx";
            }
            log("setProperty", target, name, text);
        }
        return IntrospectionSupport.setProperty(context, target, name, value);
    }

    @Override
    public boolean setProperty(TypeConverter typeConverter, Object target, String name, Object value) throws Exception {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            Object text = value;
            if (SECRETS.matcher(name).find()) {
                text = "xxxxxx";
            }
            log("setProperty", target, name, text);
        }
        return IntrospectionSupport.setProperty(typeConverter, target, name, value);
    }

    @Override
    public Set<Method> findSetterMethods(Class<?> clazz, String name, boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) {
        invoked.incrementAndGet();
        if (logger.shouldLog()) {
            log("findSetterMethods", clazz);
        }
        return IntrospectionSupport.findSetterMethods(clazz, name, allowBuilderPattern, allowPrivateSetter, ignoreCase);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        IntrospectionSupport.stop();
        if (extendedStatistics) {
            LOG.info("BeanIntrospection invoked: {} times", getInvokedCounter());
        } else {
            LOG.debug("BeanIntrospection invoked: {} times", getInvokedCounter());
        }
    }
}
