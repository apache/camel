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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.MessageHistory;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;

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
    public String dumpTracedMessagesAsXml(String nodeId, boolean includeExchangeProperties) {
        String messageAsXml = backlogDebugger.dumpTracedMessagesAsXml(nodeId);
        if (messageAsXml != null && includeExchangeProperties) {
            String closingTag = "</" + BacklogTracerEventMessage.ROOT_TAG + ">";
            String exchangePropertiesAsXml = dumpExchangePropertiesAsXml(nodeId);
            messageAsXml = messageAsXml.replace(closingTag, exchangePropertiesAsXml) + "\n" + closingTag;
        }
        return messageAsXml;
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
    public String evaluateExpressionAtBreakpoint(String nodeId, String language, String expression) {
        return evaluateExpressionAtBreakpoint(nodeId, language, expression, "java.lang.String").toString();
    }

    @Override
    public void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value) {
        Exchange suspendedExchange = backlogDebugger.getSuspendedExchange(nodeId);
        if (suspendedExchange != null) {
            suspendedExchange.setProperty(exchangePropertyName, value);
        }
    }

    @Override
    public void removeExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName) {
        Exchange suspendedExchange = backlogDebugger.getSuspendedExchange(nodeId);
        if (suspendedExchange != null) {
            suspendedExchange.removeProperty(exchangePropertyName);
        }
    }

    @Override
    public void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value, String type) {
        try {
            Class<?> classType = camelContext.getClassResolver().resolveMandatoryClass(type);
            if (type != null) {
                Exchange suspendedExchange = backlogDebugger.getSuspendedExchange(nodeId);
                if (suspendedExchange != null) {
                    value = suspendedExchange.getContext().getTypeConverter().mandatoryConvertTo(classType, suspendedExchange,
                            value);
                    suspendedExchange.setProperty(exchangePropertyName, value);
                }
            } else {
                this.setExchangePropertyOnBreakpoint(nodeId, exchangePropertyName, value);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public Object evaluateExpressionAtBreakpoint(String nodeId, String language, String expression, String resultType) {
        Exchange suspendedExchange;
        try {
            Language lan = camelContext.resolveLanguage(language);
            suspendedExchange = backlogDebugger.getSuspendedExchange(nodeId);
            if (suspendedExchange != null) {
                Object result;
                Class<?> resultClass = camelContext.getClassResolver().resolveMandatoryClass(resultType);
                if (!Boolean.class.isAssignableFrom(resultClass)) {
                    Expression expr = lan.createExpression(expression);
                    expr.init(camelContext);
                    result = expr.evaluate(suspendedExchange, resultClass);
                } else {
                    Predicate pred = lan.createPredicate(expression);
                    pred.init(camelContext);
                    result = pred.matches(suspendedExchange);
                }
                //Test if result is serializable
                if (!isSerializable(result)) {
                    String resultStr = suspendedExchange.getContext().getTypeConverter().tryConvertTo(String.class, result);
                    if (resultStr != null) {
                        result = resultStr;
                    }
                }
                return result;
            }
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;
    }

    @Override
    public String messageHistoryOnBreakpointAsXml(String nodeId) {
        StringBuilder messageHistoryBuilder = new StringBuilder();
        messageHistoryBuilder.append("<messageHistory>\n");

        Exchange suspendedExchange = backlogDebugger.getSuspendedExchange(nodeId);
        if (suspendedExchange != null) {
            List<MessageHistory> list = suspendedExchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
            if (list != null) {
                // add incoming origin of message on the top
                String routeId = suspendedExchange.getFromRouteId();
                Route route = suspendedExchange.getContext().getRoute(routeId);
                String loc = route != null ? route.getSourceLocationShort() : "";
                String id = routeId;
                String label = "";
                if (suspendedExchange.getFromEndpoint() != null) {
                    label = "from["
                            + URISupport
                                    .sanitizeUri(
                                            StringHelper.limitLength(suspendedExchange.getFromEndpoint().getEndpointUri(), 100))
                            + "]";
                }

                long elapsed = TimeUtils.elapsedMillisSince(suspendedExchange.getCreated());

                messageHistoryBuilder
                        .append("    <messageHistoryEntry")
                        .append(" location=\"").append(StringHelper.xmlEncode(loc)).append("\"")
                        .append(" routeId=\"").append(StringHelper.xmlEncode(routeId)).append("\"")
                        .append(" processorId=\"").append(StringHelper.xmlEncode(id)).append("\"")
                        .append(" processor=\"").append(StringHelper.xmlEncode(label)).append("\"")
                        .append(" elapsed=\"").append(elapsed).append("\"")
                        .append("/>\n");

                for (MessageHistory history : list) {
                    // and then each history
                    loc = LoggerHelper.getLineNumberLoggerName(history.getNode());
                    if (loc == null) {
                        loc = "";
                    }
                    routeId = history.getRouteId() != null ? history.getRouteId() : "";
                    id = history.getNode().getId();
                    // we need to avoid leak the sensible information here
                    // the sanitizeUri takes a very long time for very long string
                    // and the format cuts this to
                    // 78 characters, anyway. Cut this to 100 characters. This will
                    // give enough space for removing
                    // characters in the sanitizeUri method and will be reasonably
                    // fast
                    label = URISupport.sanitizeUri(StringHelper.limitLength(history.getNode().getLabel(), 100));
                    elapsed = history.getElapsed();

                    messageHistoryBuilder
                            .append("    <messageHistoryEntry")
                            .append(" location=\"").append(StringHelper.xmlEncode(loc)).append("\"")
                            .append(" routeId=\"").append(StringHelper.xmlEncode(routeId)).append("\"")
                            .append(" processorId=\"").append(StringHelper.xmlEncode(id)).append("\"")
                            .append(" processor=\"").append(StringHelper.xmlEncode(label)).append("\"")
                            .append(" elapsed=\"").append(elapsed).append("\"")
                            .append("/>\n");
                }
            }
        }
        messageHistoryBuilder.append("</messageHistory>\n");
        return messageHistoryBuilder.toString();
    }

    @Override
    public void attach() {
        backlogDebugger.attach();
    }

    @Override
    public void detach() {
        backlogDebugger.detach();
    }

    private String dumpExchangePropertiesAsXml(String id) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <exchangeProperties>\n");
        Exchange suspendedExchange = backlogDebugger.getSuspendedExchange(id);
        if (suspendedExchange != null) {
            Map<String, Object> properties = suspendedExchange.getAllProperties();
            properties.forEach((propertyName, propertyValue) -> {
                String type = ObjectHelper.classCanonicalName(propertyValue);
                sb.append("    <exchangeProperty name=\"").append(propertyName).append("\"");
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
                    } catch (Exception e) {
                        // ignore as the body is for logging purpose
                    }
                }
                sb.append("</exchangeProperty>\n");
            });
        }
        sb.append("  </exchangeProperties>");
        return sb.toString();
    }

    private static boolean isSerializable(Object obj) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
            out.writeObject(obj);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
