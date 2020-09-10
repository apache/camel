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
package org.apache.camel.component.quickfixj;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;
import quickfix.SessionID;

/**
 * Open a Financial Interchange (FIX) session using an embedded QuickFix/J engine.
 */
@UriEndpoint(firstVersion = "2.1.0", scheme = "quickfix", title = "QuickFix", syntax = "quickfix:configurationName",
             category = { Category.MESSAGING })
public class QuickfixjEndpoint extends DefaultEndpoint implements QuickfixjEventListener, MultipleConsumersSupport {
    public static final String EVENT_CATEGORY_KEY = "EventCategory";
    public static final String SESSION_ID_KEY = "SessionID";
    public static final String MESSAGE_TYPE_KEY = "MessageType";
    public static final String DATA_DICTIONARY_KEY = "DataDictionary";

    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjEndpoint.class);

    private final QuickfixjEngine engine;
    private final List<QuickfixjConsumer> consumers = new CopyOnWriteArrayList<>();

    @UriPath
    @Metadata(required = true)
    private String configurationName;
    @UriParam
    private SessionID sessionID;
    @UriParam
    private boolean lazyCreateEngine;

    public QuickfixjEndpoint(QuickfixjEngine engine, String uri, Component component) {
        super(uri, component);
        this.engine = engine;
    }

    public SessionID getSessionID() {
        return sessionID;
    }

    /**
     * The optional sessionID identifies a specific FIX session. The format of the sessionID is:
     * (BeginString):(SenderCompID)[/(SenderSubID)[/(SenderLocationID)]]->(TargetCompID)[/(TargetSubID)[/(TargetLocationID)]]
     */
    public void setSessionID(SessionID sessionID) {
        this.sessionID = sessionID;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    /**
     * Path to the quickfix configuration file.
     * <p/>
     * You can prefix with: classpath, file, http, ref, or bean. classpath, file and http loads the configuration file
     * using these protocols (classpath is default). ref will lookup the configuration file in the registry. bean will
     * call a method on a bean to be used as the configuration. For bean you can specify the method name after dot, eg
     * bean:myBean.myMethod
     */
    public void setConfigurationName(String configurationName) {
        this.configurationName = configurationName;
    }

    public boolean isLazyCreateEngine() {
        return lazyCreateEngine;
    }

    /**
     * This option allows to create QuickFIX/J engine on demand. Value true means the engine is started when first
     * message is send or there's consumer configured in route definition. When false value is used, the engine is
     * started at the endpoint creation. When this parameter is missing, the value of component's property
     * lazyCreateEngines is being used.
     */
    public void setLazyCreateEngine(boolean lazyCreateEngine) {
        this.lazyCreateEngine = lazyCreateEngine;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.info("Creating QuickFIX/J consumer: {}, ExchangePattern={}", sessionID != null ? sessionID : "No Session",
                getExchangePattern());
        QuickfixjConsumer consumer = new QuickfixjConsumer(this, processor);
        configureConsumer(consumer);
        consumers.add(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        LOG.info("Creating QuickFIX/J producer: {}", sessionID != null ? sessionID : "No Session");
        if (isWildcarded()) {
            throw new ResolveEndpointFailedException("Cannot create consumer on wildcarded session identifier: " + sessionID);
        }
        return new QuickfixjProducer(this);
    }

    @Override
    public void onEvent(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) throws Exception {
        if (this.sessionID == null || isMatching(sessionID)) {
            for (QuickfixjConsumer consumer : consumers) {
                Exchange exchange
                        = QuickfixjConverters.toExchange(this, sessionID, message, eventCategory, getExchangePattern());
                consumer.onExchange(exchange);
                if (exchange.getException() != null) {
                    throw exchange.getException();
                }
            }
        }
    }

    private boolean isMatching(SessionID sessionID) {
        if (this.sessionID.equals(sessionID)) {
            return true;
        }
        return isMatching(this.sessionID.getBeginString(), sessionID.getBeginString())
                && isMatching(this.sessionID.getSenderCompID(), sessionID.getSenderCompID())
                && isMatching(this.sessionID.getSenderSubID(), sessionID.getSenderSubID())
                && isMatching(this.sessionID.getSenderLocationID(), sessionID.getSenderLocationID())
                && isMatching(this.sessionID.getTargetCompID(), sessionID.getTargetCompID())
                && isMatching(this.sessionID.getTargetSubID(), sessionID.getTargetSubID())
                && isMatching(this.sessionID.getTargetLocationID(), sessionID.getTargetLocationID());
    }

    private boolean isMatching(String s1, String s2) {
        return s1.equals("") || s1.equals("*") || s1.equals(s2);
    }

    private boolean isWildcarded() {
        if (sessionID == null) {
            return false;
        }
        return sessionID.getBeginString().equals("*")
                || sessionID.getSenderCompID().equals("*")
                || sessionID.getSenderSubID().equals("*")
                || sessionID.getSenderLocationID().equals("*")
                || sessionID.getTargetCompID().equals("*")
                || sessionID.getTargetSubID().equals("*")
                || sessionID.getTargetLocationID().equals("*");
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    /**
     * Initializing and starts the engine if it wasn't initialized so far.
     */
    public void ensureInitialized() throws Exception {
        if (!engine.isInitialized()) {
            synchronized (engine) {
                if (!engine.isInitialized()) {
                    engine.initializeEngine();
                    engine.start();
                }
            }
        }
    }

    public QuickfixjEngine getEngine() {
        return engine;
    }

    @Override
    protected void doStop() throws Exception {
        // clear list of consumers
        consumers.clear();
    }
}
