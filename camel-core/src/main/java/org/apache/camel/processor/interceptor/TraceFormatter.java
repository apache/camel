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
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.ObjectHelper;

/**
 * @version $Revision$
 */
public class TraceFormatter {
    private boolean showBreadCrumb = true;
    private boolean showNode = true;
    private boolean showExchangeId;
    private boolean showExchangePattern = true;
    private boolean showProperties = true;
    private boolean showHeaders = true;
    private boolean showBody = true;
    private boolean showBodyType = true;
    private boolean showException = true;

    public Object format(TraceInterceptor interceptor, Exchange exchange) {
        Message in = exchange.getIn();
        Throwable exception = exchange.getException();
        StringBuilder sb = new StringBuilder();
        if (showBreadCrumb || showExchangeId) {
            sb.append(getBreadCrumbID(exchange)).append(" ");
        }
        if (showNode) {
            sb.append("-> ").append(getNodeMessage(interceptor)).append(" ");
        }
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

    // Implementation methods
    //-------------------------------------------------------------------------
    protected Object getBreadCrumbID(Exchange exchange) {
        UnitOfWork unitOfWork = exchange.getUnitOfWork();
        return unitOfWork.getId();
    }

    protected Object getBodyAsString(Message in) {
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
        String answer = ObjectHelper.classCanonicalName(message.getBody());
        if (answer != null && answer.startsWith("java.lang.")) {
            return answer.substring(10);
        }
        return answer;
    }

    protected String getNodeMessage(TraceInterceptor interceptor) {
        String message = interceptor.getNode().getShortName() + "(" + interceptor.getNode().getLabel() + ")";
        return String.format("%1$-25s", message);
    }

}
