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
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.converter.stream.StreamCache;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.ObjectHelper;

/**
 * @version $Revision$
 */
public class TraceFormatter {
    private int breadCrumbLength;
    private int nodeLength;
    private boolean showBreadCrumb = true;
    private boolean showNode = true;
    private boolean showExchangeId;
    private boolean showShortExchangeId;
    private boolean showExchangePattern = true;
    private boolean showProperties = true;
    private boolean showHeaders = true;
    private boolean showBody = true;
    private boolean showBodyType = true;
    private boolean showOutBody;
    private boolean showOutBodyType;
    private boolean showException = true;

    public Object format(TraceInterceptor interceptor, Exchange exchange) {
        Message in = exchange.getIn();
       
        // false because we don't want to introduce side effects
        Message out = exchange.getOut(false); 
        
        Throwable exception = exchange.getException();
        StringBuilder sb = new StringBuilder();
        sb.append(getExchangeAndNode(interceptor, exchange));
        
        if (showExchangePattern) {
            sb.append(", Pattern:").append(exchange.getPattern()).append(" ");
        }
        // only show properties if we have any
        if (showProperties && !exchange.getProperties().isEmpty()) {
            sb.append(", Properties:").append(exchange.getProperties()).append(" ");
        }
        // only show headers if we have any
        if (showHeaders && !in.getHeaders().isEmpty()) {
            sb.append(", Headers:").append(in.getHeaders()).append(" ");
        }
        if (showBodyType) {
            sb.append(", BodyType:").append(getBodyTypeAsString(in)).append(" ");
        }
        if (showBody) {
            sb.append(", Body:").append(getBodyAsString(in)).append(" ");
        }
        if (showOutBodyType && out != null) {
            sb.append(", OutBodyType:").append(getBodyTypeAsString(out)).append(" ");
        }
        if (showOutBody && out != null) {
            sb.append(", OutBody:").append(getBodyAsString(out)).append(" ");
        }        
        if (showException && exception != null) {
            sb.append(", Exception:").append(exception);
        }

        return sb.toString();
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

    // Implementation methods
    //-------------------------------------------------------------------------
    protected Object getBreadCrumbID(Exchange exchange) {
        UnitOfWork unitOfWork = exchange.getUnitOfWork();
        return unitOfWork.getId();
    }

    protected Object getBodyAsString(Message in) {
        if (in == null) {
            return null;
        }
        
        StreamCache newBody = null;
        try {
            newBody = in.getBody(StreamCache.class);
            if (newBody != null) {
                in.setBody(newBody);
            }
        } catch (NoTypeConversionAvailableException ex) {
            // ignore, in not of StreamCache type
        }
        
        Object answer = null;
        try {
            answer = in.getBody(String.class);
        } catch (NoTypeConversionAvailableException ex) {
            answer = in.getBody();
        }
        
        if (newBody != null) {
            // Reset the InputStreamCache
            newBody.reset();
        }
        return answer;
    }

    protected Object getBodyTypeAsString(Message message) {
        if (message == null) {
            return null;
        }
        String answer = ObjectHelper.classCanonicalName(message.getBody());
        if (answer != null && answer.startsWith("java.lang.")) {
            return answer.substring(10);
        }
        return answer;
    }

    protected String getNodeMessage(TraceInterceptor interceptor) {
        String message = interceptor.getNode().getShortName() + "(" + interceptor.getNode().getLabel() + ")";
        if (nodeLength > 0) {
            return String.format("%1$-" + nodeLength + "." + nodeLength + "s", message);
        } else {
            return message;
        }
    }

    /**
     * Returns the exchange id and node, ordered based on whether this was a trace of
     * an exchange coming out of or into a processing step. For example, 
     * <br/><tt>transform(body) -> ID-mojo/39713-1225468755256/2-0</tt>
     * <br/>or
     * <br/><tt>ID-mojo/39713-1225468755256/2-0 -> transform(body)</tt>
     */
    protected String getExchangeAndNode(TraceInterceptor interceptor, Exchange exchange) {
        String id = "";
        String node = "";
        String result;
        
        if (!showBreadCrumb && !showExchangeId && !showShortExchangeId && !showNode) {
            return "";
        }
        
        if (showBreadCrumb) {
            id = getBreadCrumbID(exchange).toString();
        } else if (showExchangeId || showShortExchangeId) {
            id = getBreadCrumbID(exchange).toString();
            if (showShortExchangeId) {
                // skip hostname for short exchange id
                id = id.substring(id.indexOf("/") + 1);
            }
        }

        if (showNode) {
            node = getNodeMessage(interceptor);
        }

        if (interceptor.shouldTraceOutExchanges() && exchange.getOut(false) != null) {
            result = node.trim() + " -> " + id.trim();
        } else {
            result = id.trim() + " -> " + node.trim();
        }

        if (breadCrumbLength > 0) {
            // we want to ensure text coming after this is aligned for readability
            return String.format("%1$-" + breadCrumbLength + "." + breadCrumbLength + "s", result).trim();
        } else {
            return result.trim();
        }

    }
}
