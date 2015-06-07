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
package org.apache.camel.processor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Default {@link ExchangeFormatter} that have fine grained options to configure what to include in the output.
 */
public class DefaultExchangeFormatter implements ExchangeFormatter {

    protected static final String LS = System.getProperty("line.separator");
    private static final String SEPARATOR = "###REPLACE_ME###";

    public enum OutputStyle { Default, Tab, Fixed }

    private boolean showExchangeId;
    private boolean showExchangePattern = true;
    private boolean showProperties;
    private boolean showHeaders;
    private boolean skipBodyLineSeparator = true;
    private boolean showBodyType = true;
    private boolean showBody = true;
    private boolean showOut;
    private boolean showException;
    private boolean showCaughtException;
    private boolean showStackTrace;
    private boolean showAll;
    private boolean multiline;
    private boolean showFuture;
    private boolean showStreams;
    private boolean showFiles;
    private int maxChars = 10000;
    private OutputStyle style = OutputStyle.Default;

    private String style(String label) {
        if (style == OutputStyle.Default) {
            return String.format(", %s: ", label);
        } 
        if (style == OutputStyle.Tab) {
            return String.format("\t%s: ", label);
        } else {
            return String.format("\t%-20s", label);
        }
    }

    public String format(Exchange exchange) {
        Message in = exchange.getIn();

        StringBuilder sb = new StringBuilder();
        if (showAll || showExchangeId) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            sb.append(style("Id")).append(exchange.getExchangeId());
        }
        if (showAll || showExchangePattern) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            sb.append(style("ExchangePattern")).append(exchange.getPattern());
        }

        if (showAll || showProperties) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            sb.append(style("Properties")).append(sortMap(exchange.getProperties()));
        }
        if (showAll || showHeaders) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            sb.append(style("Headers")).append(sortMap(in.getHeaders()));
        }
        if (showAll || showBodyType) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            sb.append(style("BodyType")).append(getBodyTypeAsString(in));
        }
        if (showAll || showBody) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            String body = getBodyAsString(in);
            if (skipBodyLineSeparator) {
                body = StringHelper.replaceAll(body, LS, "");
            }
            sb.append(style("Body")).append(body);
        }

        if (showAll || showException || showCaughtException) {

            // try exception on exchange first
            Exception exception = exchange.getException();
            boolean caught = false;
            if ((showAll || showCaughtException) && exception == null) {
                // fallback to caught exception
                exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                caught = true;
            }

            if (exception != null) {
                if (multiline) {
                    sb.append(SEPARATOR);
                }
                if (caught) {
                    sb.append(style("CaughtExceptionType")).append(exception.getClass().getCanonicalName());
                    sb.append(style("CaughtExceptionMessage")).append(exception.getMessage());
                } else {
                    sb.append(style("ExceptionType")).append(exception.getClass().getCanonicalName());
                    sb.append(style("ExceptionMessage")).append(exception.getMessage());
                }
                if (showAll || showStackTrace) {
                    StringWriter sw = new StringWriter();
                    exception.printStackTrace(new PrintWriter(sw));
                    sb.append(style("StackTrace")).append(sw.toString());
                }
            }
        }

        if (showAll || showOut) {
            if (exchange.hasOut()) {
                Message out = exchange.getOut();
                if (showAll || showHeaders) {
                    if (multiline) {
                        sb.append(SEPARATOR);
                    }
                    sb.append(style("OutHeaders")).append(sortMap(out.getHeaders()));
                }
                if (showAll || showBodyType) {
                    if (multiline) {
                        sb.append(SEPARATOR);
                    }
                    sb.append(style("OutBodyType")).append(getBodyTypeAsString(out));
                }
                if (showAll || showBody) {
                    if (multiline) {
                        sb.append(SEPARATOR);
                    }
                    String body = getBodyAsString(out);
                    if (skipBodyLineSeparator) {
                        body = StringHelper.replaceAll(body, LS, "");
                    }
                    sb.append(style("OutBody")).append(body);
                }
            } else {
                if (multiline) {
                    sb.append(SEPARATOR);
                }
                sb.append(style("Out: null"));
            }
        }

        if (maxChars > 0) {
            StringBuilder answer = new StringBuilder();
            for (String s : sb.toString().split(SEPARATOR)) {
                if (s != null) {
                    if (s.length() > maxChars) {
                        s = s.substring(0, maxChars);
                        answer.append(s).append("...");
                    } else {
                        answer.append(s);
                    }
                    if (multiline) {
                        answer.append(LS);
                    }
                }
            }

            // switch string buffer
            sb = answer;
        }

        if (multiline) {
            sb.insert(0, "Exchange[");
            sb.append("]");
            return sb.toString();
        } else {
            // get rid of the leading space comma if needed
            if (sb.length() > 0 && sb.charAt(0) == ',' && sb.charAt(1) == ' ') {
                sb.replace(0, 2, "");
            }
            sb.insert(0, "Exchange[");
            sb.append("]");

            return sb.toString();
        }
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

    public boolean isSkipBodyLineSeparator() {
        return skipBodyLineSeparator;
    }

    public void setSkipBodyLineSeparator(boolean skipBodyLineSeparator) {
        this.skipBodyLineSeparator = skipBodyLineSeparator;
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

    public boolean isShowStackTrace() {
        return showStackTrace;
    }

    public void setShowStackTrace(boolean showStackTrace) {
        this.showStackTrace = showStackTrace;
    }

    public boolean isShowCaughtException() {
        return showCaughtException;
    }

    public void setShowCaughtException(boolean showCaughtException) {
        this.showCaughtException = showCaughtException;
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

    public boolean isShowFuture() {
        return showFuture;
    }

    /**
     * If enabled Camel will on Future objects wait for it to complete to obtain the payload to be logged.
     * <p/>
     * Is default disabled.
     */
    public void setShowFuture(boolean showFuture) {
        this.showFuture = showFuture;
    }

    public boolean isShowExchangePattern() {
        return showExchangePattern;
    }

    public void setShowExchangePattern(boolean showExchangePattern) {
        this.showExchangePattern = showExchangePattern;
    }

    public boolean isShowStreams() {
        return showStreams;
    }

    /**
     * If enabled Camel will output stream objects
     * <p/>
     * Is default disabled.
     */
    public void setShowStreams(boolean showStreams) {
        this.showStreams = showStreams;
    }

    public boolean isShowFiles() {
        return showFiles;
    }

    /**
     * If enabled Camel will output files
     * <p/>
     * Is default disabled.
     */
    public void setShowFiles(boolean showFiles) {
        this.showFiles = showFiles;
    }

    public OutputStyle getStyle() {
        return style;
    }

    /**
     * Sets the outputs style to use.
     */
    public void setStyle(OutputStyle style) {
        this.style = style;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected String getBodyAsString(Message message) {
        if (message.getBody() instanceof Future) {
            if (!isShowFuture()) {
                // just use a to string of the future object
                return message.getBody().toString();
            }
        }

        return MessageHelper.extractBodyForLogging(message, "", isShowStreams(), isShowFiles(), getMaxChars(message));
    }

    private int getMaxChars(Message message) {
        int maxChars = getMaxChars();
        if (message.getExchange() != null) {
            String property = message.getExchange().getContext().getProperty(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
            if (property != null) {
                maxChars = message.getExchange().getContext().getTypeConverter().convertTo(Integer.class, property);
            }
        }
        return maxChars;
    }

    protected String getBodyTypeAsString(Message message) {
        String answer = ObjectHelper.classCanonicalName(message.getBody());
        if (answer != null && answer.startsWith("java.lang.")) {
            return answer.substring(10);
        }
        return answer;
    }

    private static Map<String, Object> sortMap(Map<String, Object> map) {
        Map<String, Object> answer = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        answer.putAll(map);
        return answer;
    }

}
