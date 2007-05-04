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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.processor.LoggingLevel;
import org.apache.camel.processor.SendProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for implementation inheritance for different clauses in the 
 * <a href="http://activemq.apache.org/camel/dsl.html">Java DSL</a>
 *
 * @version $Revision: $
 */
public abstract class BuilderSupport {
    private CamelContext context;
    private ErrorHandlerBuilder errorHandlerBuilder;
    private boolean inheritErrorHandler = true;

    protected BuilderSupport(CamelContext context) {
        this.context = context;
    }

    protected BuilderSupport(BuilderSupport parent) {
        this.context = parent.getContext();
        this.inheritErrorHandler = parent.inheritErrorHandler;
        if (inheritErrorHandler && parent.errorHandlerBuilder != null) {
            this.errorHandlerBuilder = parent.errorHandlerBuilder.copy();
        }
    }

    // Builder methods
    //-------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header
     */
    @Fluent
    public ValueBuilder header(@FluentArg("name")String name) {
        return Builder.header(name);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    @Fluent
    public ValueBuilder body() {
        return Builder.body();
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a specific type
     */
    @Fluent
    public <T> ValueBuilder bodyAs(@FluentArg("class")Class<T> type) {
        return Builder.bodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an exchange
     */
    @Fluent
    public ValueBuilder outBody() {
        return Builder.outBody();
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a specific type
     */
    @Fluent
    public <T> ValueBuilder outBody(@FluentArg("class")Class<T> type) {
        return Builder.outBody(type);
    }

    /**
     * Returns a value builder for the given system property
     */
    @Fluent
    public ValueBuilder systemProperty(@FluentArg("name")String name) {
        return Builder.systemProperty(name);
    }

    /**
     * Returns a value builder for the given system property
     */
    @Fluent
    public ValueBuilder systemProperty(
            @FluentArg("name")String name, @FluentArg("defaultValue")String defaultValue) {
        return Builder.systemProperty(name, defaultValue);
    }

    /**
     * Resolves the given URI to an endpoint
     */
    @Fluent
    public Endpoint endpoint(@FluentArg("uri")String uri) {
        return getContext().getEndpoint(uri);
    }

    /**
     * Resolves the list of URIs into a list of {@link Endpoint} instances
     */
    @Fluent
    public List<Endpoint> endpoints(@FluentArg("uris")String... uris) {
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (String uri : uris) {
            endpoints.add(endpoint(uri));
        }
        return endpoints;
    }

    /**
     * Helper method to create a list of {@link Endpoint} instances
     */
    @Fluent
    public List<Endpoint> endpoints(@FluentArg("endpoints")Endpoint... endpoints) {
        List<Endpoint> answer = new ArrayList<Endpoint>();
        for (Endpoint endpoint : endpoints) {
            answer.add(endpoint);
        }
        return answer;
    }

    /**
     * Creates a disabled error handler for removing the default error handler
     */
    @Fluent
    public NoErrorHandlerBuilder noErrorHandler() {
        return new NoErrorHandlerBuilder();
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder loggingErrorHandler() {
        return new LoggingErrorHandlerBuilder();
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder loggingErrorHandler(@FluentArg("log")String log) {
        return loggingErrorHandler(LogFactory.getLog(log));
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder loggingErrorHandler(@FluentArg("log")Log log) {
        return new LoggingErrorHandlerBuilder(log);
    }

    /**
     * Creates an error handler which just logs errors
     */
    @Fluent
    public LoggingErrorHandlerBuilder loggingErrorHandler(
            @FluentArg("log")Log log, @FluentArg("level")LoggingLevel level) {
        return new LoggingErrorHandlerBuilder(log, level);
    }

    @Fluent
    public DeadLetterChannelBuilder deadLetterChannel() {
        return new DeadLetterChannelBuilder();
    }

    @Fluent
    public DeadLetterChannelBuilder deadLetterChannel(@FluentArg("uri")String deadLetterUri) {
        return deadLetterChannel(endpoint(deadLetterUri));
    }

    @Fluent
    public DeadLetterChannelBuilder deadLetterChannel(@FluentArg("ref")Endpoint deadLetterEndpoint) {
        return new DeadLetterChannelBuilder(new SendProcessor(deadLetterEndpoint));
    }

    // Properties
    //-------------------------------------------------------------------------
    public CamelContext getContext() {
        return context;
    }

    public void setContext(CamelContext context) {
        this.context = context;
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    protected ErrorHandlerBuilder createErrorHandlerBuilder() {
        if (isInheritErrorHandler()) {
            return new DeadLetterChannelBuilder();
        }
        else {
            return new NoErrorHandlerBuilder();
        }
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    public boolean isInheritErrorHandler() {
        return inheritErrorHandler;
    }

    public void setInheritErrorHandler(boolean inheritErrorHandler) {
        this.inheritErrorHandler = inheritErrorHandler;
    }
}
