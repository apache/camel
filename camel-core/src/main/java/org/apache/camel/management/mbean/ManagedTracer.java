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
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ObjectHelper;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */
@ManagedResource(description = "Managed Tracer")
public class ManagedTracer {
    private final CamelContext camelContext;
    private final Tracer tracer;

    public ManagedTracer(CamelContext camelContext, Tracer tracer) {
        this.camelContext = camelContext;
        this.tracer = tracer;
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

    @ManagedAttribute(description = "Tracer enabled")
    public boolean getEnabled() {
        return tracer.isEnabled();
    }

    @ManagedAttribute(description = "Tracer enabled")
    public void setEnabled(boolean enabled) {
        tracer.setEnabled(enabled);
    }

    @ManagedAttribute(description = "Additional destination Uri")
    public String getDestinationUri() {
        return tracer.getDestinationUri();
    }

    @ManagedAttribute(description = "Additional destination Uri")
    public void setDestinationUri(String uri) {
        if (ObjectHelper.isEmpty(uri)) {
            tracer.setDestinationUri(null);
        } else {
            tracer.setDestinationUri(uri);
        }
    }

    @ManagedAttribute(description = "Logging Name")
    public String getLogName() {
        return tracer.getLogName();
    }

    @ManagedAttribute(description = "Using Jpa")
    public boolean getUseJpa() {
        return tracer.isUseJpa();
    }

    @ManagedAttribute(description = "Logging Name")
    public void setLogName(String logName) {
        tracer.setLogName(logName);
    }

    @ManagedAttribute(description = "Logging Level")
    public String getLogLevel() {
        return tracer.getLogLevel().name();
    }

    @ManagedAttribute(description = "Logging Level")
    public void setLogLevel(String logLevel) {
        tracer.setLogLevel(LoggingLevel.valueOf(logLevel));
    }

    @ManagedAttribute(description = "Log Stacktrace")
    public boolean getLogStackTrace() {
        return tracer.isLogStackTrace();
    }

    @ManagedAttribute(description = "Log Stacktrace")
    public void setLogStackTrace(boolean logStackTrace) {
        tracer.setLogStackTrace(logStackTrace);
    }

    @ManagedAttribute(description = "Trace Interceptors")
    public boolean getTraceInterceptors() {
        return tracer.isTraceInterceptors();
    }

    @ManagedAttribute(description = "Trace Interceptors")
    public void setTraceInterceptors(boolean traceInterceptors) {
        tracer.setTraceInterceptors(traceInterceptors);
    }

    @ManagedAttribute(description = "Trace Exceptions")
    public boolean getTraceExceptions() {
        return tracer.isTraceExceptions();
    }

    @ManagedAttribute(description = "Trace Exceptions")
    public void setTraceExceptions(boolean traceExceptions) {
        tracer.setTraceExceptions(traceExceptions);
    }

    @ManagedAttribute(description = "Trace Out Exchanges")
    public boolean getTraceOutExchanges() {
        return tracer.isTraceOutExchanges();
    }

    @ManagedAttribute(description = "Trace Out Exchanges")
    public void setTraceOutExchanges(boolean traceOutExchanges) {
        tracer.setTraceOutExchanges(traceOutExchanges);
    }

    @ManagedAttribute(description = "Formatter show body")
    public boolean getFormatterShowBody() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowBody();
    }

    @ManagedAttribute(description = "Formatter show body")
    public void setFormatterShowBody(boolean showBody) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowBody(showBody);
    }

    @ManagedAttribute(description = "Formatter show body type")
    public boolean getFormatterShowBodyType() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowBodyType();
    }

    @ManagedAttribute(description = "Formatter show body type")
    public void setFormatterShowBodyType(boolean showBodyType) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowBodyType(showBodyType);
    }

    @ManagedAttribute(description = "Formatter show out body")
    public boolean getFormatterShowOutBody() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowOutBody();
    }

    @ManagedAttribute(description = "Formatter show out body")
    public void setFormatterShowOutBody(boolean showOutBody) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowOutBody(showOutBody);
    }

    @ManagedAttribute(description = "Formatter show out body type")
    public boolean getFormatterShowOutBodyType() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowOutBodyType();
    }

    @ManagedAttribute(description = "Formatter show out body type")
    public void setFormatterShowOutBodyType(boolean showOutBodyType) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowOutBodyType(showOutBodyType);
    }

    @ManagedAttribute(description = "Formatter show breadcrumb")
    public boolean getFormatterShowBreadCrumb() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowBreadCrumb();
    }

    @ManagedAttribute(description = "Formatter show breadcrumb")
    public void setFormatterShowBreadCrumb(boolean showBreadCrumb) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowBreadCrumb(showBreadCrumb);
    }

    @ManagedAttribute(description = "Formatter show exchange id")
    public boolean getFormatterShowExchangeId() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowExchangeId();
    }

    @ManagedAttribute(description = "Formatter show exchange id")
    public void setFormatterShowExchangeId(boolean showExchangeId) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowExchangeId(showExchangeId);
    }

    @ManagedAttribute(description = "Formatter show headers")
    public boolean getFormatterShowHeaders() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowHeaders();
    }

    @ManagedAttribute(description = "Formatter show headers")
    public void setFormatterShowHeaders(boolean showHeaders) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowHeaders(showHeaders);
    }

    @ManagedAttribute(description = "Formatter show out headers")
    public boolean getFormatterShowOutHeaders() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowOutHeaders();
    }

    @ManagedAttribute(description = "Formatter show out headers")
    public void setFormatterShowOutHeaders(boolean showOutHeaders) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowOutHeaders(showOutHeaders);
    }

    @ManagedAttribute(description = "Formatter show properties")
    public boolean getFormatterShowProperties() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowProperties();
    }

    @ManagedAttribute(description = "Formatter show properties")
    public void setFormatterShowProperties(boolean showProperties) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowProperties(showProperties);
    }

    @ManagedAttribute(description = "Formatter show node")
    public boolean getFormatterShowNode() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowNode();
    }

    @ManagedAttribute(description = "Formatter show node")
    public void setFormatterShowNode(boolean showNode) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowNode(showNode);
    }

    @ManagedAttribute(description = "Formatter show exchange pattern")
    public boolean getFormatterShowExchangePattern() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowExchangePattern();
    }

    @ManagedAttribute(description = "Formatter show exchange pattern")
    public void setFormatterShowExchangePattern(boolean showExchangePattern) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowExchangePattern(showExchangePattern);
    }

    @ManagedAttribute(description = "Formatter show exception")
    public boolean getFormatterShowException() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowException();
    }

    @ManagedAttribute(description = "Formatter show exception")
    public void setFormatterShowException(boolean showException) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowException(showException);
    }

    @ManagedAttribute(description = "Formatter show route id")
    public boolean getFormatterShowRouteId() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowRouteId();
    }

    @ManagedAttribute(description = "Formatter show route id")
    public void setFormatterShowRouteId(boolean showRouteId) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowRouteId(showRouteId);
    }

    @ManagedAttribute(description = "Formatter breadcrumb length")
    public int getFormatterBreadCrumbLength() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return 0;
        }
        return tracer.getDefaultTraceFormatter().getBreadCrumbLength();
    }

    @ManagedAttribute(description = "Formatter breadcrumb length")
    public void setFormatterBreadCrumbLength(int breadCrumbLength) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setBreadCrumbLength(breadCrumbLength);
    }

    @ManagedAttribute(description = "Formatter show short exchange id")
    public boolean getFormatterShowShortExchangeId() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return false;
        }
        return tracer.getDefaultTraceFormatter().isShowShortExchangeId();
    }

    @ManagedAttribute(description = "Formatter show short exchange id")
    public void setFormatterShowShortExchangeId(boolean showShortExchangeId) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setShowShortExchangeId(showShortExchangeId);
    }

    @ManagedAttribute(description = "Formatter node length")
    public int getFormatterNodeLength() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return 0;
        }
        return tracer.getDefaultTraceFormatter().getNodeLength();
    }

    @ManagedAttribute(description = "Formatter node length")
    public void setFormatterNodeLength(int nodeLength) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setNodeLength(nodeLength);
    }

    @ManagedAttribute(description = "Formatter max chars")
    public int getFormatterMaxChars() {
        if (tracer.getDefaultTraceFormatter() == null) {
            return 0;
        }
        return tracer.getDefaultTraceFormatter().getMaxChars();
    }

    @ManagedAttribute(description = "Formatter max chars")
    public void setFormatterMaxChars(int maxChars) {
        if (tracer.getDefaultTraceFormatter() == null) {
            return;
        }
        tracer.getDefaultTraceFormatter().setMaxChars(maxChars);
    }

}
