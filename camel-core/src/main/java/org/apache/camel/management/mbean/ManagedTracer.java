/**
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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.api.management.ManagedNotification;
import org.apache.camel.api.management.ManagedNotifications;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.NotificationSender;
import org.apache.camel.api.management.NotificationSenderAware;
import org.apache.camel.api.management.mbean.ManagedTracerMBean;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
@ManagedResource(description = "Managed Tracer")
@ManagedNotifications(@ManagedNotification(name = "javax.management.Notification", 
    description = "Fine grained trace events", 
    notificationTypes = {"TraceNotification"}))
public class ManagedTracer implements NotificationSenderAware, ManagedTracerMBean {
    private final CamelContext camelContext;
    private final Tracer tracer;
    private JMXNotificationTraceEventHandler jmxTraceHandler;

    public ManagedTracer(CamelContext camelContext, Tracer tracer) {
        this.camelContext = camelContext;
        this.tracer = tracer;
        jmxTraceHandler = new JMXNotificationTraceEventHandler(tracer);
        tracer.addTraceHandler(jmxTraceHandler);
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public String getCamelId() {
        return camelContext.getName();
    }

    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    public boolean getEnabled() {
        return tracer.isEnabled();
    }

    public void setEnabled(boolean enabled) {
        tracer.setEnabled(enabled);
    }

    public String getDestinationUri() {
        return tracer.getDestinationUri();
    }

    public void setDestinationUri(String uri) {
        if (ObjectHelper.isEmpty(uri)) {
            tracer.setDestinationUri(null);
        } else {
            tracer.setDestinationUri(uri);
        }
    }

    public String getLogName() {
        return tracer.getLogName();
    }

    public boolean getUseJpa() {
        return tracer.isUseJpa();
    }

    public void setLogName(String logName) {
        tracer.setLogName(logName);
    }

    public String getLogLevel() {
        return tracer.getLogLevel().name();
    }

    public void setLogLevel(String logLevel) {
        tracer.setLogLevel(LoggingLevel.valueOf(logLevel));
    }

    public boolean getLogStackTrace() {
        return tracer.isLogStackTrace();
    }

    public void setLogStackTrace(boolean logStackTrace) {
        tracer.setLogStackTrace(logStackTrace);
    }

    public boolean getTraceInterceptors() {
        return tracer.isTraceInterceptors();
    }

    public void setTraceInterceptors(boolean traceInterceptors) {
        tracer.setTraceInterceptors(traceInterceptors);
    }

    public boolean getTraceExceptions() {
        return tracer.isTraceExceptions();
    }

    public void setTraceExceptions(boolean traceExceptions) {
        tracer.setTraceExceptions(traceExceptions);
    }

    public boolean getTraceOutExchanges() {
        return tracer.isTraceOutExchanges();
    }

    public void setTraceOutExchanges(boolean traceOutExchanges) {
        tracer.setTraceOutExchanges(traceOutExchanges);
    }

    public boolean getFormatterShowBody() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowBody();
    }

    public void setFormatterShowBody(boolean showBody) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowBody(showBody);
    }

    public boolean getFormatterShowBodyType() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowBodyType();
    }

    public void setFormatterShowBodyType(boolean showBodyType) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowBodyType(showBodyType);
    }

    public boolean getFormatterShowOutBody() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowOutBody();
    }

    public void setFormatterShowOutBody(boolean showOutBody) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowOutBody(showOutBody);
    }

    public boolean getFormatterShowOutBodyType() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowOutBodyType();
    }

    public void setFormatterShowOutBodyType(boolean showOutBodyType) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowOutBodyType(showOutBodyType);
    }

    public boolean getFormatterShowBreadCrumb() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowBreadCrumb();
    }

    public void setFormatterShowBreadCrumb(boolean showBreadCrumb) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowBreadCrumb(showBreadCrumb);
    }

    public boolean getFormatterShowExchangeId() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowExchangeId();
    }

    public void setFormatterShowExchangeId(boolean showExchangeId) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowExchangeId(showExchangeId);
    }

    public boolean getFormatterShowHeaders() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowHeaders();
    }

    public void setFormatterShowHeaders(boolean showHeaders) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowHeaders(showHeaders);
    }

    public boolean getFormatterShowOutHeaders() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowOutHeaders();
    }

    public void setFormatterShowOutHeaders(boolean showOutHeaders) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowOutHeaders(showOutHeaders);
    }

    public boolean getFormatterShowProperties() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowProperties();
    }

    public void setFormatterShowProperties(boolean showProperties) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowProperties(showProperties);
    }

    public boolean getFormatterMultiline() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isMultiline();
    }

    public void setFormatterMultiline(boolean multiline) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setMultiline(multiline);
    }

    
    public boolean getFormatterShowNode() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowNode();
    }

    public void setFormatterShowNode(boolean showNode) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowNode(showNode);
    }

    public boolean getFormatterShowExchangePattern() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowExchangePattern();
    }

    public void setFormatterShowExchangePattern(boolean showExchangePattern) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowExchangePattern(showExchangePattern);
    }

    public boolean getFormatterShowException() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowException();
    }

    public void setFormatterShowException(boolean showException) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowException(showException);
    }

    public boolean getFormatterShowRouteId() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowRouteId();
    }

    public void setFormatterShowRouteId(boolean showRouteId) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowRouteId(showRouteId);
    }

    public int getFormatterBreadCrumbLength() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return 0;
        }
        return tracer.getDefaultTraceFormatter().getBreadCrumbLength();
    }

    public void setFormatterBreadCrumbLength(int breadCrumbLength) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setBreadCrumbLength(breadCrumbLength);
    }

    public boolean getFormatterShowShortExchangeId() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowShortExchangeId();
    }

    public void setFormatterShowShortExchangeId(boolean showShortExchangeId) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowShortExchangeId(showShortExchangeId);
    }

    public int getFormatterNodeLength() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return 0;
        }
        return tracer.getDefaultTraceFormatter().getNodeLength();
    }

    public void setFormatterNodeLength(int nodeLength) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setNodeLength(nodeLength);
    }

    public int getFormatterMaxChars() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return 0;
        }
        return tracer.getDefaultTraceFormatter().getMaxChars();
    }

    public void setFormatterMaxChars(int maxChars) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setMaxChars(maxChars);
    }
    
    public boolean isJmxTraceNotifications() {
        return this.tracer.isJmxTraceNotifications();
    }

    public void setJmxTraceNotifications(boolean jmxTraceNotifications) {
        this.tracer.setJmxTraceNotifications(jmxTraceNotifications);
    }

    public int getTraceBodySize() {
        return this.tracer.getTraceBodySize();
    }

    public void setTraceBodySize(int traceBodySize) {
        this.tracer.setTraceBodySize(traceBodySize);
    }

    @Override
    public void setNotificationSender(NotificationSender sender) {
        jmxTraceHandler.setNotificationSender(sender);
    }

}
