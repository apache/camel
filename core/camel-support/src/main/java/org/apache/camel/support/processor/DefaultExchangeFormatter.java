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
package org.apache.camel.support.processor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Default {@link ExchangeFormatter} that have fine grained options to configure what to include in the output.
 */
@UriParams
@Configurer
public class DefaultExchangeFormatter implements ExchangeFormatter {

    protected static final String LS = System.lineSeparator();
    private static final String SEPARATOR = "###REPLACE_ME###";

    public enum OutputStyle {
        Default,
        Tab,
        Fixed
    }

    @UriParam(label = "formatting", description = "Show the unique exchange ID.")
    private boolean showExchangeId;
    @UriParam(label = "formatting",
              description = "Shows the Message Exchange Pattern (or MEP for short).")
    private boolean showExchangePattern;
    @UriParam(label = "formatting", description = "Show the exchange properties (only custom).")
    private boolean showProperties;
    @UriParam(label = "formatting", description = "Show all the exchange properties (both internal and custom).")
    private boolean showAllProperties;
    @UriParam(label = "formatting", description = "Show the message headers.")
    private boolean showHeaders;
    @UriParam(label = "formatting", defaultValue = "true",
              description = "Whether to skip line separators when logging the message body."
                            + " This allows to log the message body in one line, setting this option to false will preserve any line separators from the body, which then will log the body as is.")
    private boolean skipBodyLineSeparator = true;
    @UriParam(label = "formatting", defaultValue = "true", description = "Show the message body.")
    private boolean showBody = true;
    @UriParam(label = "formatting", defaultValue = "true", description = "Show the body Java type.")
    private boolean showBodyType = true;
    @UriParam(label = "formatting",
              description = "If the exchange has an exception, show the exception message (no stacktrace)")
    private boolean showException;
    @UriParam(label = "formatting",
              description = "f the exchange has a caught exception, show the exception message (no stack trace)."
                            + " A caught exception is stored as a property on the exchange (using the key org.apache.camel.Exchange#EXCEPTION_CAUGHT) and for instance a doCatch can catch exceptions.")
    private boolean showCaughtException;
    @UriParam(label = "formatting",
              description = "Show the stack trace, if an exchange has an exception. Only effective if one of showAll, showException or showCaughtException are enabled.")
    private boolean showStackTrace;
    @UriParam(label = "formatting",
              description = "Quick option for turning all options on. (multiline, maxChars has to be manually set if to be used)")
    private boolean showAll;
    @UriParam(label = "formatting", description = "If enabled then each information is outputted on a newline.")
    private boolean multiline;
    @UriParam(label = "formatting",
              description = "If enabled Camel will on Future objects wait for it to complete to obtain the payload to be logged.")
    private boolean showFuture;
    @UriParam(label = "formatting", defaultValue = "true",
              description = "Whether Camel should show cached stream bodies or not (org.apache.camel.StreamCache).")
    private boolean showCachedStreams = true;
    @UriParam(label = "formatting",
              description = "Whether Camel should show stream bodies or not (eg such as java.io.InputStream). Beware if you enable this option then "
                            + "you may not be able later to access the message body as the stream have already been read by this logger. To remedy this you will have to use Stream Caching.")
    private boolean showStreams;
    @UriParam(label = "formatting", description = "If enabled Camel will output files")
    private boolean showFiles;
    @UriParam(label = "formatting", defaultValue = "10000", description = "Limits the number of characters logged per line.")
    private int maxChars = 10000;
    @UriParam(label = "formatting", enums = "Default,Tab,Fixed", defaultValue = "Default",
              description = "Sets the outputs style to use.")
    private OutputStyle style = OutputStyle.Default;
    @UriParam(defaultValue = "false", description = "If enabled only the body will be printed out")
    private boolean plain;

    private StringBuilder style(StringBuilder sb, String label) {
        if (style == OutputStyle.Default) {
            if (multiline) {
                sb.append("  ").append(label).append(": ");
            } else {
                sb.append(", ").append(label).append(": ");
            }
        } else if (style == OutputStyle.Tab) {
            sb.append("\t").append(label).append(": ");
        } else {
            String s = String.format("\t%-20s", label);
            sb.append(s);
        }
        return sb;
    }

    @Override
    public String format(Exchange exchange) {
        Message in = exchange.getIn();

        StringBuilder sb = new StringBuilder();

        if (plain) {
            return getBodyAsString(in);
        }

        if (showAll || showExchangeId) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            style(sb, "Id").append(exchange.getExchangeId());
        }
        if (showAll || showExchangePattern) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            style(sb, "ExchangePattern").append(exchange.getPattern());
        }

        if (showAll || showAllProperties) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            style(sb, "Properties").append(sortMap(filterHeaderAndProperties(exchange.getAllProperties())));
        } else if (showProperties) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            style(sb, "Properties").append(sortMap(filterHeaderAndProperties(exchange.getProperties())));
        }
        if (showAll || showHeaders) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            style(sb, "Headers").append(sortMap(filterHeaderAndProperties(in.getHeaders())));
        }
        if (showAll || showBodyType) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            style(sb, "BodyType").append(getBodyTypeAsString(in));
        }
        if (showAll || showBody) {
            if (multiline) {
                sb.append(SEPARATOR);
            }
            String body = getBodyAsString(in);
            if (skipBodyLineSeparator && body != null) {
                body = body.replace(LS, "");
            }
            style(sb, "Body").append(body);
        }

        if (showAll || showException || showCaughtException) {

            // try exception on exchange first
            Exception exception = exchange.getException();
            boolean caught = false;
            if ((showAll || showCaughtException) && exception == null) {
                // fallback to caught exception
                exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
                caught = true;
            }

            if (exception != null) {
                if (multiline) {
                    sb.append(SEPARATOR);
                }
                if (caught) {
                    style(sb, "CaughtExceptionType").append(exception.getClass().getCanonicalName());
                    style(sb, "CaughtExceptionMessage").append(exception.getMessage());
                } else {
                    style(sb, "ExceptionType").append(exception.getClass().getCanonicalName());
                    style(sb, "ExceptionMessage").append(exception.getMessage());
                }
                if (showAll || showStackTrace) {
                    StringWriter sw = new StringWriter();
                    exception.printStackTrace(new PrintWriter(sw));
                    style(sb, "StackTrace").append(sw);
                }
            }
        }

        // only cut if we hit max-chars limit (or are using multiline
        if (multiline || maxChars > 0 && sb.length() > maxChars) {
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

        if (!multiline) {
            // get rid of the leading comma space if needed
            if (sb.length() > 1 && sb.charAt(0) == ',' && sb.charAt(1) == ' ') {
                sb.replace(0, 2, "");
            }

        }
        sb.insert(0, "Exchange[");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Filters the headers or properties before formatting them. No default behavior, but can be overridden.
     */
    protected Map<String, Object> filterHeaderAndProperties(Map<String, Object> map) {
        return map;
    }

    public boolean isShowExchangeId() {
        return showExchangeId;
    }

    /**
     * Show the unique exchange ID.
     */
    public void setShowExchangeId(boolean showExchangeId) {
        this.showExchangeId = showExchangeId;
    }

    public boolean isShowProperties() {
        return showProperties;
    }

    /**
     * Show the exchange properties (only custom). Use showAllProperties to show both internal and custom properties.
     */
    public void setShowProperties(boolean showProperties) {
        this.showProperties = showProperties;
    }

    public boolean isShowAllProperties() {
        return showAllProperties;
    }

    /**
     * Show all of the exchange properties (both internal and custom).
     */
    public void setShowAllProperties(boolean showAllProperties) {
        this.showAllProperties = showAllProperties;
    }

    public boolean isShowHeaders() {
        return showHeaders;
    }

    /**
     * Show the message headers.
     */
    public void setShowHeaders(boolean showHeaders) {
        this.showHeaders = showHeaders;
    }

    public boolean isSkipBodyLineSeparator() {
        return skipBodyLineSeparator;
    }

    /**
     * Whether to skip line separators when logging the message body. This allows to log the message body in one line,
     * setting this option to false will preserve any line separators from the body, which then will log the body as is.
     */
    public void setSkipBodyLineSeparator(boolean skipBodyLineSeparator) {
        this.skipBodyLineSeparator = skipBodyLineSeparator;
    }

    public boolean isShowBodyType() {
        return showBodyType;
    }

    /**
     * Show the body Java type.
     */
    public void setShowBodyType(boolean showBodyType) {
        this.showBodyType = showBodyType;
    }

    public boolean isShowBody() {
        return showBody;
    }

    /*
     * Show the message body.
     */
    public void setShowBody(boolean showBody) {
        this.showBody = showBody;
    }

    public boolean isShowAll() {
        return showAll;
    }

    /**
     * Quick option for turning all options on. (multiline, maxChars has to be manually set if to be used)
     */
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }

    public boolean isShowException() {
        return showException;
    }

    /**
     * If the exchange has an exception, show the exception message (no stacktrace)
     */
    public void setShowException(boolean showException) {
        this.showException = showException;
    }

    public boolean isShowStackTrace() {
        return showStackTrace;
    }

    /**
     * Show the stack trace, if an exchange has an exception. Only effective if one of showAll, showException or
     * showCaughtException are enabled.
     */
    public void setShowStackTrace(boolean showStackTrace) {
        this.showStackTrace = showStackTrace;
    }

    public boolean isShowCaughtException() {
        return showCaughtException;
    }

    /**
     * If the exchange has a caught exception, show the exception message (no stack trace). A caught exception is stored
     * as a property on the exchange (using the key {@link org.apache.camel.Exchange#EXCEPTION_CAUGHT} and for instance
     * a doCatch can catch exceptions.
     */
    public void setShowCaughtException(boolean showCaughtException) {
        this.showCaughtException = showCaughtException;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public int getMaxChars() {
        return maxChars;
    }

    /**
     * Limits the number of characters logged per line.
     */
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
     */
    public void setShowFuture(boolean showFuture) {
        this.showFuture = showFuture;
    }

    public boolean isShowExchangePattern() {
        return showExchangePattern;
    }

    /**
     * Shows the Message Exchange Pattern (or MEP for short).
     */
    public void setShowExchangePattern(boolean showExchangePattern) {
        this.showExchangePattern = showExchangePattern;
    }

    public boolean isShowCachedStreams() {
        return showCachedStreams;
    }

    /**
     * Whether Camel should show cached stream bodies or not (org.apache.camel.StreamCache).
     */
    public void setShowCachedStreams(boolean showCachedStreams) {
        this.showCachedStreams = showCachedStreams;
    }

    public boolean isShowStreams() {
        return showStreams;
    }

    /**
     * Whether Camel should show stream bodies or not (eg such as java.io.InputStream). Beware if you enable this option
     * then you may not be able later to access the message body as the stream have already been read by this logger. To
     * remedy this you will have to use Stream Caching.
     */
    public void setShowStreams(boolean showStreams) {
        this.showStreams = showStreams;
    }

    public boolean isShowFiles() {
        return showFiles;
    }

    /**
     * If enabled Camel will output files
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

    public boolean isPlain() {
        return plain;
    }

    /**
     * If enabled only the body will be printed out
     */
    public void setPlain(boolean plain) {
        this.plain = plain;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected String getBodyAsString(Message message) {
        if (message.getBody() instanceof Future) {
            if (!isShowFuture()) {
                // just use to string of the future object
                return message.getBody().toString();
            }
        }

        return MessageHelper.extractBodyForLogging(message, null, isShowCachedStreams(), isShowStreams(), isShowFiles(),
                getMaxChars(message));
    }

    private int getMaxChars(Message message) {
        int maxChars = getMaxChars();
        if (message.getExchange() != null) {
            String globalOption = message.getExchange().getContext().getGlobalOption(Exchange.LOG_DEBUG_BODY_MAX_CHARS);
            if (globalOption != null) {
                maxChars = message.getExchange().getContext().getTypeConverter().convertTo(Integer.class, globalOption);
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
        Map<String, Object> answer = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        answer.putAll(map);
        return answer;
    }

}
