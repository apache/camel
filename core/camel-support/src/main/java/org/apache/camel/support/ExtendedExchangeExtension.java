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

package org.apache.camel.support;

import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;

public class ExtendedExchangeExtension implements ExchangeExtension {
    private final AbstractExchange exchange;

    ExtendedExchangeExtension(AbstractExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setFromEndpoint(Endpoint fromEndpoint) {
        this.exchange.fromEndpoint = fromEndpoint;
    }

    @Override
    public void setFromRouteId(String fromRouteId) {
        exchange.fromRouteId = fromRouteId;
    }

    /**
     * Is stream caching disabled on the given exchange
     */
    public boolean isStreamCacheDisabled() {
        return this.exchange.streamCacheDisabled;
    }

    /**
     * Used to force disabling stream caching which some components can do in special use-cases.
     */
    public void setStreamCacheDisabled(boolean streamCacheDisabled) {
        this.exchange.streamCacheDisabled = streamCacheDisabled;
    }

    @Override
    public void addOnCompletion(Synchronization onCompletion) {
        this.exchange.addOnCompletion(onCompletion);
    }

    @Override
    public boolean isErrorHandlerHandledSet() {
        return this.exchange.isErrorHandlerHandledSet();
    }

    @Override
    public Boolean getErrorHandlerHandled() {
        return this.exchange.errorHandlerHandled;
    }

    @Override
    public void setErrorHandlerHandled(Boolean errorHandlerHandled) {
        this.exchange.errorHandlerHandled = errorHandlerHandled;
    }

    @Override
    public boolean isErrorHandlerHandled() {
        return this.exchange.errorHandlerHandled;
    }

    @Override
    public boolean isRedeliveryExhausted() {
        return this.exchange.redeliveryExhausted;
    }

    @Override
    public void setRedeliveryExhausted(boolean redeliveryExhausted) {
        this.exchange.redeliveryExhausted = redeliveryExhausted;
    }

    @Override
    public void handoverCompletions(Exchange target) {
        this.exchange.handoverCompletions(target);
    }

    @Override
    public List<Synchronization> handoverCompletions() {
        return this.exchange.handoverCompletions();
    }

    @Override
    public void setUnitOfWork(UnitOfWork unitOfWork) {
        this.exchange.setUnitOfWork(unitOfWork);
    }

    @Override
    public void copyInternalProperties(Exchange target) {
        this.exchange.copyInternalProperties(target);
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        this.exchange.setProperties(properties);
    }

    @Override
    public void setHistoryNodeId(String historyNodeId) {
        this.exchange.historyNodeId = historyNodeId;
    }

    @Override
    public String getHistoryNodeId() {
        return this.exchange.historyNodeId;
    }

    @Override
    public String getHistoryNodeSource() {
        return this.exchange.historyNodeSource;
    }

    @Override
    public void setHistoryNodeSource(String historyNodeSource) {
        this.exchange.historyNodeSource = historyNodeSource;
    }

    @Override
    public String getHistoryNodeLabel() {
        return this.exchange.historyNodeSource;
    }

    @Override
    public void setHistoryNodeLabel(String historyNodeLabel) {
        this.exchange.historyNodeLabel = historyNodeLabel;
    }

    @Override
    public boolean isNotifyEvent() {
        return this.exchange.notifyEvent;
    }

    @Override
    public void setNotifyEvent(boolean notifyEvent) {
        this.exchange.notifyEvent = notifyEvent;
    }

    @Override
    public Map<String, Object> getInternalProperties() {
        return this.exchange.getInternalProperties();
    }

    @Override
    public boolean containsOnCompletion(Synchronization onCompletion) {
        return this.exchange.containsOnCompletion(onCompletion);
    }

    @Override
    public void setTransacted(boolean transacted) {
        this.exchange.transacted = transacted;
    }

    @Override
    public void setInterruptable(boolean interruptable) {
        this.exchange.interruptable = interruptable;
    }

    @Override
    public boolean isInterrupted() {
        return this.exchange.interrupted;
    }

    @Override
    public void setInterrupted(boolean interrupted) {
        this.exchange.setInterrupted(interrupted);
    }

    @Override
    public <T> T getInOrNull(Class<T> type) {
        return this.exchange.getInOrNull(type);
    }

    @Override
    public AsyncCallback getDefaultConsumerCallback() {
        return this.exchange.defaultConsumerCallback;
    }

    @Override
    public void setDefaultConsumerCallback(AsyncCallback callback) {
        this.exchange.defaultConsumerCallback = callback;
    }

    @Override
    public void setSafeCopyProperty(String key, SafeCopyProperty value) {
        this.exchange.setSafeCopyProperty(key, value);
    }

    @Override
    public <T> T getSafeCopyProperty(String key, Class<T> type) {
        return this.exchange.getSafeCopyProperty(key, type);
    }

    @Override
    public boolean isFailureHandled() {
        return this.exchange.failureHandled;
    }

    @Override
    public void setFailureHandled(boolean failureHandled) {
        this.exchange.failureHandled = failureHandled;
    }
}
