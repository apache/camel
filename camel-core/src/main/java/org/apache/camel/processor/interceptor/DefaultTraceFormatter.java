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
package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RouteNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.util.MessageHelper;

/**
 * @version 
 */
public class DefaultTraceFormatter implements TraceFormatter {
    private int breadCrumbLength;
    private int nodeLength;
    private boolean showBreadCrumb = true;
    private boolean showNode = true;
    private boolean showExchangeId;
    private boolean showShortExchangeId;
    private boolean showExchangePattern = true;
    private boolean showProperties;
    private boolean showHeaders = true;
    private boolean showBody = true;
    private boolean showBodyType = true;
    private boolean showOutHeaders;
    private boolean showOutBody;
    private boolean showOutBodyType;
    private boolean showException = true;
    private boolean showRouteId = true;
    private int maxChars = 10000;

    public Object format(final TraceInterceptor interceptor, final ProcessorDefinition<?> node, final Exchange exchange) {
        Message in = exchange.getIn();
        Message out = null;
        if (exchange.hasOut()) {
            out = exchange.getOut();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(extractBreadCrumb(interceptor, node, exchange));
        
        if (showExchangePattern) {
            sb.append(", Pattern:").append(exchange.getPattern());
        }
        // only show properties if we have any
        if (showProperties && !exchange.getProperties().isEmpty()) {
            sb.append(", Properties:").append(exchange.getProperties());
        }
        // only show headers if we have any
        if (showHeaders && !in.getHeaders().isEmpty()) {
            sb.append(", Headers:").append(in.getHeaders());
        }
        if (showBodyType) {
            sb.append(", BodyType:").append(MessageHelper.getBodyTypeName(in));
        }
        if (showBody) {
            sb.append(", Body:").append(MessageHelper.extractBodyForLogging(in, ""));
        }
        if (showOutHeaders && out != null) {
            sb.append(", OutHeaders:").append(out.getHeaders());
        }
        if (showOutBodyType && out != null) {
            sb.append(", OutBodyType:").append(MessageHelper.getBodyTypeName(out));
        }
        if (showOutBody && out != null) {
            sb.append(", OutBody:").append(MessageHelper.extractBodyForLogging(out, ""));
        }        
        if (showException && exchange.getException() != null) {
            sb.append(", Exception:").append(exchange.getException());
        }

        // replace ugly <<<, with <<<
        String s = sb.toString();
        s = s.replaceFirst("<<<,", "<<<");

        if (maxChars > 0) {
            if (s.length() > maxChars) {
                s = s.substring(0, maxChars) + "...";
            }
            return s;
        } else {
            return s;
        }
    }

    public boolean isShowBody() {
        return showBody;
    }

    public void setShowBody(boolean showBody) {
        this.showBody = showBody;
    }

    public boolean isShowBodyType() {
        return showBodyType;
    }

    public void setShowBodyType(boolean showBodyType) {
        this.showBodyType = showBodyType;
    }

    public void setShowOutBody(boolean showOutBody) {
        this.showOutBody = showOutBody;
    }

    public boolean isShowOutBody() {
        return showOutBody;
    }    
    
    public void setShowOutBodyType(boolean showOutBodyType) {
        this.showOutBodyType = showOutBodyType;
    }

    public boolean isShowOutBodyType() {
        return showOutBodyType;
    }    
    
    public boolean isShowBreadCrumb() {
        return showBreadCrumb;
    }

    public void setShowBreadCrumb(boolean showBreadCrumb) {
        this.showBreadCrumb = showBreadCrumb;
    }

    public boolean isShowExchangeId() {
        return showExchangeId;
    }

    public void setShowExchangeId(boolean showExchangeId) {
        this.showExchangeId = showExchangeId;
    }

    public boolean isShowHeaders() {
        return showHeaders;
    }

    public void setShowHeaders(boolean showHeaders) {
        this.showHeaders = showHeaders;
    }

    public boolean isShowOutHeaders() {
        return showOutHeaders;
    }

    public void setShowOutHeaders(boolean showOutHeaders) {
        this.showOutHeaders = showOutHeaders;
    }

    public boolean isShowProperties() {
        return showProperties;
    }

    public void setShowProperties(boolean showProperties) {
        this.showProperties = showProperties;
    }

    public boolean isShowNode() {
        return showNode;
    }

    public void setShowNode(boolean showNode) {
        this.showNode = showNode;
    }

    public boolean isShowExchangePattern() {
        return showExchangePattern;
    }

    public void setShowExchangePattern(boolean showExchangePattern) {
        this.showExchangePattern = showExchangePattern;
    }

    public boolean isShowException() {
        return showException;
    }

    public void setShowException(boolean showException) {
        this.showException = showException;
    }

    public boolean isShowRouteId() {
        return showRouteId;
    }

    public void setShowRouteId(boolean showRouteId) {
        this.showRouteId = showRouteId;
    }

    public int getBreadCrumbLength() {
        return breadCrumbLength;
    }

    public void setBreadCrumbLength(int breadCrumbLength) {
        this.breadCrumbLength = breadCrumbLength;
    }

    public boolean isShowShortExchangeId() {
        return showShortExchangeId;
    }

    public void setShowShortExchangeId(boolean showShortExchangeId) {
        this.showShortExchangeId = showShortExchangeId;
    }

    public int getNodeLength() {
        return nodeLength;
    }

    public void setNodeLength(int nodeLength) {
        this.nodeLength = nodeLength;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected String extractRoute(ProcessorDefinition<?> node) {
        RouteDefinition route = ProcessorDefinitionHelper.getRoute(node);
        if (route != null) {
            return route.getId();
        } else {
            return null;
        }
    }

    protected Object getBreadCrumbID(Exchange exchange) {
        return exchange.getExchangeId();
    }

    protected String getNodeMessage(RouteNode entry, Exchange exchange) {
        String message = entry.getLabel(exchange);
        if (nodeLength > 0) {
            return String.format("%1$-" + nodeLength + "." + nodeLength + "s", message);
        } else {
            return message;
        }
    }
    
    /**
     * Creates the breadcrumb based on whether this was a trace of
     * an exchange coming out of or into a processing step. For example, 
     * <br/><tt>transform(body) -> ID-mojo/39713-1225468755256/2-0</tt>
     * <br/>or
     * <br/><tt>ID-mojo/39713-1225468755256/2-0 -> transform(body)</tt>
     */
    protected String extractBreadCrumb(TraceInterceptor interceptor, ProcessorDefinition<?> currentNode, Exchange exchange) {
        String id = "";
        String result;
        
        if (!showBreadCrumb && !showExchangeId && !showShortExchangeId && !showNode) {
            return "";
        }

        // compute breadcrumb id
        if (showBreadCrumb) {
            id = getBreadCrumbID(exchange).toString();
        } else if (showExchangeId || showShortExchangeId) {
            id = getBreadCrumbID(exchange).toString();
            if (showShortExchangeId) {
                // only output last part of id
                id = id.substring(id.lastIndexOf('-') + 1);
            }
        }

        // compute from, to and route
        String from = "";
        String to = "";
        String route = "";
        if (showNode || showRouteId) {
            if (exchange.getUnitOfWork() != null) {
                TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();

                RouteNode traceFrom = traced.getSecondLastNode();
                if (traceFrom != null) {
                    from = getNodeMessage(traceFrom, exchange);
                } else if (exchange.getFromEndpoint() != null) {
                    from = "from(" + exchange.getFromEndpoint().getEndpointUri() + ")";
                }

                RouteNode traceTo = traced.getLastNode();
                if (traceTo != null) {
                    to = getNodeMessage(traceTo, exchange);
                    // if its an abstract dummy holder then we have to get the 2nd last so we can get the real node that has
                    // information which route it belongs to
                    if (traceTo.isAbstract() && traceTo.getProcessorDefinition() == null) {
                        traceTo = traced.getSecondLastNode();
                    }
                    if (traceTo != null) {
                        route = extractRoute(traceTo.getProcessorDefinition());
                    }
                }
            }
        }

        // assemble result with and without the to/from
        if (showNode) {
            if (showRouteId && route != null) {
                result = id.trim() + " >>> (" + route + ") " + from + " --> " + to.trim() + " <<< ";
            } else {
                result = id.trim() + " >>> " + from + " --> " + to.trim() + " <<< ";
            }

            if (interceptor.shouldTraceOutExchanges() && exchange.hasOut()) {
                result += " (OUT) ";
            }
        } else {
            result = id;
        }

        if (breadCrumbLength > 0) {
            // we want to ensure text coming after this is aligned for readability
            return String.format("%1$-" + breadCrumbLength + "." + breadCrumbLength + "s", result.trim());
        } else {
            return result.trim();
        }
    }

}
