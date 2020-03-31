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
package org.apache.camel.impl.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.LoggingLevel;
import org.apache.camel.StartupListener;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class DefaultBeanIntrospection extends ServiceSupport implements BeanIntrospection, CamelContextAware, StartupListener {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBeanIntrospection.class);
    private static final Pattern SECRETS = Pattern.compile(".*(passphrase|password|secretKey).*", Pattern.CASE_INSENSITIVE);

    private CamelContext camelContext;
    private volatile boolean preStartDone;
    private final List<String> preStartLogs = new ArrayList<>();
    private final AtomicLong invoked = new AtomicLong();
    private volatile boolean extendedStatistics;
    private LoggingLevel loggingLevel = LoggingLevel.TRACE;
    private CamelLogger logger = new CamelLogger(LOG, loggingLevel);

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

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

        String line;
        if (target == null) {
            line = "Invoked: " + invoked.get() + " times (overall) [Method: " + method + "]";
        } else if (args == null) {
            line = "Invoked: " + invoked.get() + " times (overall) [Method: " + method + ", Target: " + target + "]";
        } else {
            line = "Invoked: " + invoked.get() + " times (overall) [Method: " + method + ", Target: " + target + ", Arguments: " + obj + "]";
        }

        if (preStartDone) {
            logger.log(line);
        } else {
            // remember log lines before we are starting
            preStartLogs.add(line);
        }
    }

    @Override
    public ClassInfo cacheClass(Class<?> clazz) {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
            log("cacheClass", clazz);
        }
        return IntrospectionSupport.cacheClass(clazz);
    }

    @Override
    public void clearCache() {
        if (invoked.get() > 0) {
            invoked.incrementAndGet();
            if (!preStartDone || logger.shouldLog()) {
                log("clearCache", null);
            }
            IntrospectionSupport.clearCache();
        }
    }

    @Override
    public long getCachedClassesCounter() {
        if (invoked.get() > 0) {
            return IntrospectionSupport.getCacheCounter();
        } else {
            return 0;
        }
    }

    @Override
    public boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix) {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
            log("getProperties", target);
        }
        return IntrospectionSupport.getProperties(target, properties, optionPrefix);
    }

    @Override
    public boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix, boolean includeNull) {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
            log("getProperties", target);
        }
        return IntrospectionSupport.getProperties(target, properties, optionPrefix, includeNull);
    }

    @Override
    public Object getOrElseProperty(Object target, String propertyName, Object defaultValue, boolean ignoreCase) {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
            log("getOrElseProperty", target, propertyName);
        }
        return IntrospectionSupport.getOrElseProperty(target, propertyName, defaultValue, ignoreCase);
    }

    @Override
    public Method getPropertyGetter(Class<?> type, String propertyName, boolean ignoreCase) throws NoSuchMethodException {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
            log("getPropertyGetter", type, propertyName);
        }
        return IntrospectionSupport.getPropertyGetter(type, propertyName, ignoreCase);
    }

    @Override
    public boolean setProperty(CamelContext context, TypeConverter typeConverter, Object target, String name, Object value, String refName, boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) throws Exception {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
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
        if (!preStartDone || logger.shouldLog()) {
            Object text = value;
            if (SECRETS.matcher(name).find()) {
                text = "xxxxxx";
            }
            log("setProperty", target, name, text);
        }
        return IntrospectionSupport.setProperty(context, target, name, value);
    }

    @Override
    public Set<Method> findSetterMethods(Class<?> clazz, String name, boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase) {
        invoked.incrementAndGet();
        if (!preStartDone || logger.shouldLog()) {
            log("findSetterMethods", clazz);
        }
        return IntrospectionSupport.findSetterMethods(clazz, name, allowBuilderPattern, allowPrivateSetter, ignoreCase);
    }

    @Override
    public void afterPropertiesConfigured(CamelContext camelContext) {
        // log any pre starting logs so we can see them all
        preStartLogs.forEach(logger::log);
        preStartLogs.clear();
        preStartDone = true;
    }

    @Override
    protected void doInit() throws Exception {
        if (camelContext != null) {
            camelContext.addStartupListener(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (invoked.get() > 0) {
            IntrospectionSupport.stop();
        }
        if (extendedStatistics) {
            LOG.info("Stopping BeanIntrospection which was invoked: {} times", invoked.get());
        } else {
            LOG.debug("Stopping BeanIntrospection which was invoked: {} times", invoked.get());
        }
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        // ensure after properties is called
        afterPropertiesConfigured(camelContext);
    }
}
