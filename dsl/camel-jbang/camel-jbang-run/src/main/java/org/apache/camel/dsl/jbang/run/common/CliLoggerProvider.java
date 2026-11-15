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
package org.apache.camel.dsl.jbang.run.common;

import java.util.Deque;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.NOP_FallbackServiceProvider;
import org.slf4j.helpers.SubstituteLogger;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * The SLF4J provider of a Camel CLI started from the command line: its loggers log nothing until the CLI configures the
 * logging ({@link RuntimeUtil#configureLog}), and then log with the SLF4J provider on the classpath (log4j).
 * <p/>
 * Most commands, such as camel ps or camel get, turn the logging off, and something that only creates a logger (a
 * static logger of a class of Camel) then made log4j start, which is about a third of such a command (CAMEL-26937).
 * Log4j already logged nothing until the CLI configured it (CAMEL-26429), so the commands log as before. The main
 * method of the CLI installs it, see {@link CliLogging#install()}.
 */
public final class CliLoggerProvider implements SLF4JServiceProvider {

    private final CliLoggerFactory loggerFactory = new CliLoggerFactory();
    private final MDCAdapter mdcAdapter = new CliMDCAdapter(this);
    private final Supplier<SLF4JServiceProvider> delegateFinder;
    private volatile SLF4JServiceProvider delegate;

    /**
     * The provider that SLF4J creates, as the system property {@code slf4j.provider} names it.
     */
    public CliLoggerProvider() {
        this(CliLoggerProvider::findProvider);
    }

    CliLoggerProvider(Supplier<SLF4JServiceProvider> delegateFinder) {
        this.delegateFinder = delegateFinder;
    }

    /**
     * The CLI configured the logging: from now on the loggers log with the provider on the classpath.
     */
    void configured() {
        loggerFactory.delegateTo(delegate().getLoggerFactory());
    }

    @Override
    public void initialize() {
        // SLF4J chose this provider, as something asked for a logger
        CliLogging.bound(this::configured);
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return delegate().getMarkerFactory();
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        // SLF4J asks for it when it chooses the provider, before anything logs
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.99";
    }

    // the provider on the classpath, which is found as SLF4J would without this one
    private SLF4JServiceProvider delegate() {
        SLF4JServiceProvider answer = delegate;
        if (answer == null) {
            synchronized (this) {
                answer = delegate;
                if (answer == null) {
                    answer = delegateFinder.get();
                    answer.initialize();
                    delegate = answer;
                }
            }
        }
        return answer;
    }

    private static SLF4JServiceProvider findProvider() {
        ClassLoader cl = CliLoggerProvider.class.getClassLoader();
        for (SLF4JServiceProvider provider : ServiceLoader.load(SLF4JServiceProvider.class, cl)) {
            if (!(provider instanceof CliLoggerProvider)) {
                return provider;
            }
        }
        return new NOP_FallbackServiceProvider();
    }

    /**
     * The loggers created before the logging is configured stand in for the loggers of the provider on the classpath,
     * which they log with once it is.
     */
    static final class CliLoggerFactory implements ILoggerFactory {

        private final Map<String, SubstituteLogger> loggers = new ConcurrentHashMap<>();
        private volatile ILoggerFactory delegate;

        @Override
        public Logger getLogger(String name) {
            ILoggerFactory factory = delegate;
            if (factory != null) {
                return factory.getLogger(name);
            }
            synchronized (this) {
                factory = delegate;
                if (factory != null) {
                    return factory.getLogger(name);
                }
                // a substitute logger without a delegate logs nothing
                return loggers.computeIfAbsent(name, n -> new SubstituteLogger(n, null, true));
            }
        }

        synchronized void delegateTo(ILoggerFactory factory) {
            if (delegate == null) {
                loggers.forEach((name, logger) -> logger.setDelegate(factory.getLogger(name)));
                delegate = factory;
            }
        }
    }

    /**
     * The MDC of the provider on the classpath, which its layouts read, created when something first uses the MDC.
     */
    static final class CliMDCAdapter implements MDCAdapter {

        private final CliLoggerProvider provider;

        CliMDCAdapter(CliLoggerProvider provider) {
            this.provider = provider;
        }

        private MDCAdapter adapter() {
            return provider.delegate().getMDCAdapter();
        }

        @Override
        public void put(String key, String val) {
            adapter().put(key, val);
        }

        @Override
        public String get(String key) {
            return adapter().get(key);
        }

        @Override
        public void remove(String key) {
            adapter().remove(key);
        }

        @Override
        public void clear() {
            adapter().clear();
        }

        @Override
        public Map<String, String> getCopyOfContextMap() {
            return adapter().getCopyOfContextMap();
        }

        @Override
        public void setContextMap(Map<String, String> contextMap) {
            adapter().setContextMap(contextMap);
        }

        @Override
        public void pushByKey(String key, String value) {
            adapter().pushByKey(key, value);
        }

        @Override
        public String popByKey(String key) {
            return adapter().popByKey(key);
        }

        @Override
        public Deque<String> getCopyOfDequeByKey(String key) {
            return adapter().getCopyOfDequeByKey(key);
        }

        @Override
        public void clearDequeByKey(String key) {
            adapter().clearDequeByKey(key);
        }
    }
}
