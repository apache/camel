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
package org.apache.camel.component.log;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.util.ObjectHelper;

/**
 * Log formatter to format the logging output.
 */
public class LogFormatter implements ExchangeFormatter {

    private boolean showExchangeId;
    private boolean showProperties;
    private boolean showHeaders;
    private boolean showBodyType = true;
    private boolean showBody = true;
    private boolean showOut;
    private boolean showException;
    private boolean showAll;
    private boolean multiline;
    private int maxChars;

    public Object format(Exchange exchange) {
        Message in = exchange.getIn();

        StringBuilder sb = new StringBuilder("");
        if (showAll || showExchangeId) {
            if (multiline) {
                sb.append('\n');
            }
            sb.append(", Id:").append(exchange.getExchangeId());
        }
        if (showAll || showProperties) {
            if (multiline) {
                sb.append('\n');
            }
            sb.append(", Properties:").append(exchange.getProperties());
        }
        if (showAll || showHeaders) {
            if (multiline) {
                sb.append('\n');
            }
            sb.append(", Headers:").append(in.getHeaders());
        }
        if (showAll || showBodyType) {
            if (multiline) {
                sb.append('\n');
            }
            sb.append(", BodyType:").append(getBodyTypeAsString(in));
        }
        if (showAll || showBody) {
            if (multiline) {
                sb.append('\n');
            }
            sb.append(", Body:").append(getBodyAsString(in));
        }
        if (exchange.getException() != null && (showAll || showException)) {
            if (multiline) {
                sb.append('\n');
            }
            sb.append(", Exception:").append(exchange.getException().getMessage());
        }

        if (showAll || showOut) {
            if (exchange.hasOut()) {
                Message out = exchange.getOut();
                if (showAll || showHeaders) {
                    if (multiline) {
                        sb.append('\n');
                    }
                    sb.append(", OutHeaders:").append(out.getHeaders());
                }
                if (showAll || showBodyType) {
                    if (multiline) {
                        sb.append('\n');
                    }
                    sb.append(", OutBodyType:").append(getBodyTypeAsString(out));
                }
                if (showAll || showBody) {
                    if (multiline) {
                        sb.append('\n');
                    }
                    sb.append(", OutBody:").append(getBodyAsString(out));
                }
            } else {
                if (multiline) {
                    sb.append('\n');
                }
                sb.append(", Out: null");
            }
        }

        if (maxChars > 0) {
            StringBuilder answer = new StringBuilder();
            for (String s : sb.toString().split("\n")) {
                if (s != null) {
                    if (s.length() > maxChars) {
                        s = s.substring(0, maxChars);
                        answer.append(s).append("...");
                    } else {
                        answer.append(s);
                    }
                    if (multiline) {
                        answer.append("\n");
                    }
                }
            }

            // get rid of the leading space comma if needed
            return "Exchange[" + (multiline ? answer.append(']').toString() : answer.toString().substring(2) + "]");
        }

        // get rid of the leading space comma if needed
        return "Exchange[" + (multiline ? sb.append(']').toString() : sb.toString().substring(2) + "]");
    }

    public boolean isShowExchangeId() {
        return showExchangeId;
    }

    public void setShowExchangeId(boolean showExchangeId) {
        this.showExchangeId = showExchangeId;
    }

    public boolean isShowProperties() {
        return showProperties;
    }

    public void setShowProperties(boolean showProperties) {
        this.showProperties = showProperties;
    }

    public boolean isShowHeaders() {
        return showHeaders;
    }

    public void setShowHeaders(boolean showHeaders) {
        this.showHeaders = showHeaders;
    }

    public boolean isShowBodyType() {
        return showBodyType;
    }

    public void setShowBodyType(boolean showBodyType) {
        this.showBodyType = showBodyType;
    }

    public boolean isShowBody() {
        return showBody;
    }

    public void setShowBody(boolean showBody) {
        this.showBody = showBody;
    }

    public boolean isShowOut() {
        return showOut;
    }

    public void setShowOut(boolean showOut) {
        this.showOut = showOut;
    }

    public boolean isShowAll() {
        return showAll;
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }

    public boolean isShowException() {
        return showException;
    }

    public void setShowException(boolean showException) {
        this.showException = showException;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    /**
     * If enabled then each information is outputted on a newline.
     */
    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected Object getBodyAsString(Message message) {
        StreamCache newBody = message.getBody(StreamCache.class);
        if (newBody != null) {
            message.setBody(newBody);
        }

        Object answer = message.getBody(String.class);
        if (answer == null) {
            answer = message.getBody();
        }

        if (newBody != null) {
            // Reset the StreamCache
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

}
