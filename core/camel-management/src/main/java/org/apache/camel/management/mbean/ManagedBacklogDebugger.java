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
package org.apache.camel.management.mbean;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;

@ManagedResource(description = "Managed BacklogDebugger")
public class ManagedBacklogDebugger implements ManagedBacklogDebuggerMBean {

    private final CamelContext camelContext;
    private final BacklogDebugger backlogDebugger;

    public ManagedBacklogDebugger(CamelContext camelContext, BacklogDebugger backlogDebugger) {
        this.camelContext = camelContext;
        this.backlogDebugger = backlogDebugger;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public BacklogDebugger getBacklogDebugger() {
        return backlogDebugger;
    }

    @Override
    public String getCamelId() {
        return camelContext.getName();
    }

    @Override
    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    @Override
    public String getLoggingLevel() {
        return backlogDebugger.getLoggingLevel();
    }

    @Override
    public void setLoggingLevel(String level) {
        backlogDebugger.setLoggingLevel(level);
    }

    @Override
    public boolean isEnabled() {
        return backlogDebugger.isEnabled();
    }

    @Override
    public void enableDebugger() {
        backlogDebugger.enableDebugger();
    }

    @Override
    public void disableDebugger() {
        backlogDebugger.disableDebugger();
    }

    @Override
    public void addBreakpoint(String nodeId) {
        backlogDebugger.addBreakpoint(nodeId);
    }

    @Override
    public void addConditionalBreakpoint(String nodeId, String language, String predicate) {
        backlogDebugger.addConditionalBreakpoint(nodeId, language, predicate);
    }

    @Override
    public void removeBreakpoint(String nodeId) {
        backlogDebugger.removeBreakpoint(nodeId);
    }

    @Override
    public void removeAllBreakpoints() {
        backlogDebugger.removeAllBreakpoints();
    }

    @Override
    public Set<String> getBreakpoints() {
        return breakpoints();
    }

    @Override
    public Set<String> breakpoints() {
        return backlogDebugger.getBreakpoints();
    }

    @Override
    public void resumeBreakpoint(String nodeId) {
        backlogDebugger.resumeBreakpoint(nodeId);
    }

    @Override
    public void setMessageBodyOnBreakpoint(String nodeId, Object body) {
        backlogDebugger.setMessageBodyOnBreakpoint(nodeId, body);
    }

    @Override
    public void setMessageBodyOnBreakpoint(String nodeId, Object body, String type) {
        try {
            Class<?> classType = camelContext.getClassResolver().resolveMandatoryClass(type);
            backlogDebugger.setMessageBodyOnBreakpoint(nodeId, body, classType);
        } catch (ClassNotFoundException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void removeMessageBodyOnBreakpoint(String nodeId) {
        backlogDebugger.removeMessageBodyOnBreakpoint(nodeId);
    }

    @Override
    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value) {
        try {
            backlogDebugger.setMessageHeaderOnBreakpoint(nodeId, headerName, value);
        } catch (NoTypeConversionAvailableException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, String type) {
        try {
            Class<?> classType = camelContext.getClassResolver().resolveMandatoryClass(type);
            backlogDebugger.setMessageHeaderOnBreakpoint(nodeId, headerName, value, classType);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void removeMessageHeaderOnBreakpoint(String nodeId, String headerName) {
        backlogDebugger.removeMessageHeaderOnBreakpoint(nodeId, headerName);
    }

    @Override
    public void resumeAll() {
        backlogDebugger.resumeAll();
    }

    @Override
    public void stepBreakpoint(String nodeId) {
        backlogDebugger.stepBreakpoint(nodeId);
    }

    @Override
    public boolean isSingleStepMode() {
        return backlogDebugger.isSingleStepMode();
    }

    @Override
    public void step() {
        backlogDebugger.step();
    }

    @Override
    public Set<String> getSuspendedBreakpointNodeIds() {
        return suspendedBreakpointNodeIds();
    }

    @Override
    public Set<String> suspendedBreakpointNodeIds() {
        return backlogDebugger.getSuspendedBreakpointNodeIds();
    }

    @Override
    public void disableBreakpoint(String nodeId) {
        backlogDebugger.disableBreakpoint(nodeId);
    }

    @Override
    public void enableBreakpoint(String nodeId) {
        backlogDebugger.enableBreakpoint(nodeId);
    }

    @Override
    public int getBodyMaxChars() {
        return backlogDebugger.getBodyMaxChars();
    }

    @Override
    public void setBodyMaxChars(int bodyMaxChars) {
        backlogDebugger.setBodyMaxChars(bodyMaxChars);
    }

    @Override
    public boolean isBodyIncludeStreams() {
        return backlogDebugger.isBodyIncludeStreams();
    }

    @Override
    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        backlogDebugger.setBodyIncludeStreams(bodyIncludeStreams);
    }

    @Override
    public boolean isBodyIncludeFiles() {
        return backlogDebugger.isBodyIncludeFiles();
    }

    @Override
    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        backlogDebugger.setBodyIncludeFiles(bodyIncludeFiles);
    }

    @Override
    public String dumpTracedMessagesAsXml(String nodeId) {
        return backlogDebugger.dumpTracedMessagesAsXml(nodeId);
    }

    @Override
    public long getDebugCounter() {
        return backlogDebugger.getDebugCounter();
    }

    @Override
    public void resetDebugCounter() {
        backlogDebugger.resetDebugCounter();
    }

    @Override
    public String validateConditionalBreakpoint(String language, String predicate) {
        Language lan = null;
        try {
            lan = camelContext.resolveLanguage(language);
            lan.createPredicate(predicate);
            return null;
        } catch (Exception e) {
            if (lan == null) {
                return e.getMessage();
            } else {
                return "Invalid syntax " + predicate + " due: " + e.getMessage();
            }
        }
    }

    @Override
    public long getFallbackTimeout() {
        return backlogDebugger.getFallbackTimeout();
    }

    @Override
    public void setFallbackTimeout(long fallbackTimeout) {
        backlogDebugger.setFallbackTimeout(fallbackTimeout);
    }

    @Override
    public String dumpExchangePropertiesAsXml(String id) {
        StringBuilder sb = new StringBuilder();
        sb.append("<properties>");
        Exchange suspendedExchange = backlogDebugger.getSuspendedExchange(id);
        if (suspendedExchange != null) {
            Map<String, Object> properties = suspendedExchange.getAllProperties();
            properties.forEach((propertyName, propertyValue) -> {
                String type = ObjectHelper.classCanonicalName(propertyValue);
                sb.append("<property name=\"").append(propertyName).append("\"");
                if (type != null) {
                    sb.append(" type=\"").append(type).append("\"");
                }
                sb.append(">");
                // dump property value as XML, use Camel type converter to convert
                // to String
                if (propertyValue != null) {
                    try {
                        String xml = suspendedExchange.getContext().getTypeConverter().tryConvertTo(String.class,
                                suspendedExchange, propertyValue);
                        if (xml != null) {
                            // must always xml encode
                            sb.append(StringHelper.xmlEncode(xml));
                        }
                    } catch (Throwable e) {
                        // ignore as the body is for logging purpose
                    }
                }
                sb.append("</property>\n");
            });
        }
        sb.append("</properties>");
        return sb.toString();
    }

    @Override
    public String evaluateExpressionAtBreakpoint(String id, String language, String expression) {
        return evaluateExpressionAtBreakpoint(id, language, expression, "java.lang.String").toString();
    }

    @Override
    public Object evaluateExpressionAtBreakpoint(String id, String language, String expression, String resultType) {
        Exchange suspendedExchange = null;
        try {
            Language lan = camelContext.resolveLanguage(language);
            suspendedExchange = backlogDebugger.getSuspendedExchange(id);
            if (suspendedExchange != null) {
                Object result = null;
                Class resultClass = Class.forName(resultType);
                if (!resultClass.isAssignableFrom(Boolean.class)) {
                    Expression expr = lan.createExpression(expression);
                    result = expr.evaluate(suspendedExchange, resultClass);
                } else {
                    Predicate pred = lan.createPredicate(expression);
                    result = pred.matches(suspendedExchange);
                }
                //Test if result is serializable
                try {
                    byte[] data = SerializationUtils.serialize((Serializable) result);
                } catch (SerializationException se) {
                    String resultStr = suspendedExchange.getContext().getTypeConverter().tryConvertTo(String.class, result);
                    if (resultStr != null) {
                        result = resultStr;
                    }
                }
                return result;
            }
        } catch (Exception e) {
            return e;
        }
        return null;
    }
}
