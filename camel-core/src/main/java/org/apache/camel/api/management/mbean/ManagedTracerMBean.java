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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;

public interface ManagedTracerMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Tracer enabled")
    boolean getEnabled();

    @ManagedAttribute(description = "Tracer enabled")
    void setEnabled(boolean enabled);

    @ManagedAttribute(description = "Additional destination URI")
    String getDestinationUri();

    @ManagedAttribute(description = "Additional destination URI")
    void setDestinationUri(String uri);

    @ManagedAttribute(description = "Logging Name")
    String getLogName();

    @ManagedAttribute(description = "Using JPA")
    boolean getUseJpa();

    @ManagedAttribute(description = "Logging Name")
    void setLogName(String logName);

    @ManagedAttribute(description = "Logging Level")
    String getLogLevel();

    @ManagedAttribute(description = "Logging Level")
    void setLogLevel(String logLevel);

    @ManagedAttribute(description = "Log Stacktrace")
    boolean getLogStackTrace();

    @ManagedAttribute(description = "Log Stacktrace")
    void setLogStackTrace(boolean logStackTrace);

    @ManagedAttribute(description = "Trace Interceptors")
    boolean getTraceInterceptors();

    @ManagedAttribute(description = "Trace Interceptors")
    void setTraceInterceptors(boolean traceInterceptors);

    @ManagedAttribute(description = "Trace Exceptions")
    boolean getTraceExceptions();

    @ManagedAttribute(description = "Trace Exceptions")
    void setTraceExceptions(boolean traceExceptions);

    @ManagedAttribute(description = "Trace Out Exchanges")
    boolean getTraceOutExchanges();

    @ManagedAttribute(description = "Trace Out Exchanges")
    void setTraceOutExchanges(boolean traceOutExchanges);

    @ManagedAttribute(description = "Formatter show body")
    boolean getFormatterShowBody();

    @ManagedAttribute(description = "Formatter show body")
    void setFormatterShowBody(boolean showBody);

    @ManagedAttribute(description = "Formatter show body type")
    boolean getFormatterShowBodyType();

    @ManagedAttribute(description = "Formatter show body type")
    void setFormatterShowBodyType(boolean showBodyType);

    @ManagedAttribute(description = "Formatter show out body")
    boolean getFormatterShowOutBody();

    @ManagedAttribute(description = "Formatter show out body")
    void setFormatterShowOutBody(boolean showOutBody);

    @ManagedAttribute(description = "Formatter show out body type")
    boolean getFormatterShowOutBodyType();

    @ManagedAttribute(description = "Formatter show out body type")
    void setFormatterShowOutBodyType(boolean showOutBodyType);

    @ManagedAttribute(description = "Formatter show breadcrumb")
    boolean getFormatterShowBreadCrumb();

    @ManagedAttribute(description = "Formatter show breadcrumb")
    void setFormatterShowBreadCrumb(boolean showBreadCrumb);

    @ManagedAttribute(description = "Formatter show exchange ID")
    boolean getFormatterShowExchangeId();

    @ManagedAttribute(description = "Formatter show exchange ID")
    void setFormatterShowExchangeId(boolean showExchangeId);

    @ManagedAttribute(description = "Formatter show headers")
    boolean getFormatterShowHeaders();

    @ManagedAttribute(description = "Formatter show headers")
    void setFormatterShowHeaders(boolean showHeaders);

    @ManagedAttribute(description = "Formatter show out headers")
    boolean getFormatterShowOutHeaders();

    @ManagedAttribute(description = "Formatter show out headers")
    void setFormatterShowOutHeaders(boolean showOutHeaders);

    @ManagedAttribute(description = "Formatter show properties")
    boolean getFormatterShowProperties();

    @ManagedAttribute(description = "Formatter show properties")
    void setFormatterShowProperties(boolean showProperties);

    @ManagedAttribute(description = "Formatter show node")
    boolean getFormatterShowNode();

    @ManagedAttribute(description = "Formatter show node")
    void setFormatterShowNode(boolean showNode);

    @ManagedAttribute(description = "Formatter show exchange pattern")
    boolean getFormatterShowExchangePattern();

    @ManagedAttribute(description = "Formatter show exchange pattern")
    void setFormatterShowExchangePattern(boolean showExchangePattern);

    @ManagedAttribute(description = "Formatter show exception")
    boolean getFormatterShowException();

    @ManagedAttribute(description = "Formatter show exception")
    void setFormatterShowException(boolean showException);

    @ManagedAttribute(description = "Formatter show route ID")
    boolean getFormatterShowRouteId();

    @ManagedAttribute(description = "Formatter show route ID")
    void setFormatterShowRouteId(boolean showRouteId);

    @ManagedAttribute(description = "Formatter breadcrumb length")
    int getFormatterBreadCrumbLength();

    @ManagedAttribute(description = "Formatter breadcrumb length")
    void setFormatterBreadCrumbLength(int breadCrumbLength);

    @ManagedAttribute(description = "Formatter show short exchange ID")
    boolean getFormatterShowShortExchangeId();

    @ManagedAttribute(description = "Formatter show short exchange ID")
    void setFormatterShowShortExchangeId(boolean showShortExchangeId);

    @ManagedAttribute(description = "Formatter node length")
    int getFormatterNodeLength();

    @ManagedAttribute(description = "Formatter node length")
    void setFormatterNodeLength(int nodeLength);

    @ManagedAttribute(description = "Formatter max chars")
    int getFormatterMaxChars();

    @ManagedAttribute(description = "Formatter max chars")
    void setFormatterMaxChars(int maxChars);

    @ManagedAttribute(description = "Should trace events be sent as JMX notifications")
    boolean isJmxTraceNotifications();

    @ManagedAttribute(description = "Should trace events be sent as JMX notifications")
    void setJmxTraceNotifications(boolean jmxTraceNotifications);

    @ManagedAttribute(description = "Maximum size of a message body for trace notification")
    int getTraceBodySize();

    @ManagedAttribute(description = "Maximum size of a message body for trace notification")
    void setTraceBodySize(int traceBodySize);

}