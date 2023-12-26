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
package org.apache.camel.component.controlbus;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Manage and monitor Camel routes.
 *
 * For example, by sending a message to an Endpoint you can control the lifecycle of routes, or gather performance
 * statistics.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "controlbus", title = "Control Bus", syntax = "controlbus:command:language",
             remote = false, producerOnly = true, category = { Category.CORE, Category.MONITORING })
public class ControlBusEndpoint extends DefaultEndpoint {

    @UriPath(description = "Command can be either route or language", enums = "route,language")
    @Metadata(required = true)
    private String command;
    @UriPath(enums = "bean,constant,csimple,datasonnet,exchangeProperty,file,groovy,header,hl7terser,java,joor,jq,jsonpath,mvel,ognl,python,ref,simple,spel,tokenize,xpath,xquery,xtokenize")
    private Language language;
    @UriParam
    private String routeId;
    @UriParam(enums = "start,stop,fail,suspend,resume,restart,status,stats")
    private String action;
    @UriParam(defaultValue = "1000")
    private int restartDelay = 1000;
    @UriParam
    private boolean async;
    @UriParam(defaultValue = "INFO")
    private LoggingLevel loggingLevel = LoggingLevel.INFO;

    private transient CamelLogger logger;

    public ControlBusEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    protected void doInit() throws Exception {
        this.logger = new CamelLogger(ControlBusProducer.class.getName(), loggingLevel);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ControlBusProducer(this, logger);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume from a ControlBusEndpoint: " + getEndpointUri());
    }

    @Override
    public ControlBusComponent getComponent() {
        return (ControlBusComponent) super.getComponent();
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * Allows you to specify the name of a Language to use for evaluating the message body. If there is any result from
     * the evaluation, then the result is put in the message body.
     */
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getRouteId() {
        return routeId;
    }

    /**
     * To specify a route by its id. The special keyword "current" indicates the current route.
     */
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getAction() {
        return action;
    }

    /**
     * To denote an action that can be either: start, stop, or status.
     * <p/>
     * To either start or stop a route, or to get the status of the route as output in the message body. You can use
     * suspend and resume to either suspend or resume a route. You can use stats to get performance statics returned in
     * XML format; the routeId option can be used to define which route to get the performance stats for, if routeId is
     * not defined, then you get statistics for the entire CamelContext. The restart action will restart the route. And
     * the fail action will stop and mark the route as failed (stopped due to an exception)
     */
    public void setAction(String action) {
        this.action = action;
    }

    public int getRestartDelay() {
        return restartDelay;
    }

    /**
     * The delay in millis to use when restarting a route.
     */
    public void setRestartDelay(int restartDelay) {
        this.restartDelay = restartDelay;
    }

    public boolean isAsync() {
        return async;
    }

    /**
     * Whether to execute the control bus task asynchronously.
     * <p/>
     * Important: If this option is enabled, then any result from the task is not set on the Exchange. This is only
     * possible if executing tasks synchronously.
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Logging level used for logging when task is done, or if any exceptions occurred during processing the task.
     */
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }
}
