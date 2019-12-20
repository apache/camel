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
package org.apache.camel.component.log;

import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.processor.CamelLogProcessor;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.support.processor.DefaultMaskingFormatter;
import org.apache.camel.support.processor.ThroughputLogger;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;

/**
 * The log component logs message exchanges to the underlying logging mechanism.
 *
 * Camel uses sfl4j which allows you to configure logging to the actual logging system.
 */
@UriEndpoint(firstVersion = "1.1.0", scheme = "log", title = "Log",
        syntax = "log:loggerName", producerOnly = true, label = "core,monitoring")
public class LogEndpoint extends ProcessorEndpoint {

    private volatile Processor logger;
    private Logger providedLogger;
    private ExchangeFormatter localFormatter;

    @UriPath(description = "Name of the logging category to use") @Metadata(required = true)
    private String loggerName;
    @UriParam(defaultValue = "INFO", enums = "ERROR,WARN,INFO,DEBUG,TRACE,OFF")
    private String level;
    @UriParam
    private String marker;
    @UriParam
    private Integer groupSize;
    @UriParam
    private Long groupInterval;
    @UriParam(defaultValue = "true")
    private Boolean groupActiveOnly;
    @UriParam
    private Long groupDelay;
    @UriParam
    private Boolean logMask;
    @UriParam(label = "advanced")
    private ExchangeFormatter exchangeFormatter;
    @UriParam(label = "formatting", description = "Show the unique exchange ID.")
    private boolean showExchangeId;
    @UriParam(label = "formatting", defaultValue = "true", description = "Shows the Message Exchange Pattern (or MEP for short).")
    private boolean showExchangePattern = true;
    @UriParam(label = "formatting", description = "Show the exchange properties.")
    private boolean showProperties;
    @UriParam(label = "formatting", description = "Show the message headers.")
    private boolean showHeaders;
    @UriParam(label = "formatting", defaultValue = "true", description = "Whether to skip line separators when logging the message body."
            + " This allows to log the message body in one line, setting this option to false will preserve any line separators from the body, which then will log the body as is.")
    private boolean skipBodyLineSeparator = true;
    @UriParam(label = "formatting", defaultValue = "true", description = "Show the message body.")
    private boolean showBody = true;
    @UriParam(label = "formatting", defaultValue = "true", description = "Show the body Java type.")
    private boolean showBodyType = true;
    @UriParam(label = "formatting", description = "If the exchange has an exception, show the exception message (no stacktrace)")
    private boolean showException;
    @UriParam(label = "formatting", description = "If the exchange has a caught exception, show the exception message (no stack trace)."
            + " A caught exception is stored as a property on the exchange (using the key org.apache.camel.Exchange#EXCEPTION_CAUGHT) and for instance a doCatch can catch exceptions.")
    private boolean showCaughtException;
    @UriParam(label = "formatting", description = "Show the stack trace, if an exchange has an exception. Only effective if one of showAll, showException or showCaughtException are enabled.")
    private boolean showStackTrace;
    @UriParam(label = "formatting", description = "Quick option for turning all options on. (multiline, maxChars has to be manually set if to be used)")
    private boolean showAll;
    @UriParam(label = "formatting", description = "If enabled then each information is outputted on a newline.")
    private boolean multiline;
    @UriParam(label = "formatting", description = "If enabled Camel will on Future objects wait for it to complete to obtain the payload to be logged.")
    private boolean showFuture;
    @UriParam(label = "formatting", description = "Whether Camel should show stream bodies or not (eg such as java.io.InputStream). Beware if you enable this option then "
            + "you may not be able later to access the message body as the stream have already been read by this logger. To remedy this you will have to use Stream Caching.")
    private boolean showStreams;
    @UriParam(label = "formatting", description = "If enabled Camel will output files")
    private boolean showFiles;
    @UriParam(label = "formatting", defaultValue = "10000", description = "Limits the number of characters logged per line.")
    private int maxChars = 10000;
    @UriParam(label = "formatting", enums = "Default,Tab,Fixed", defaultValue = "Default", description = "Sets the outputs style to use.")
    private DefaultExchangeFormatter.OutputStyle style = DefaultExchangeFormatter.OutputStyle.Default;

    public LogEndpoint() {
    }

    public LogEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public LogEndpoint(String endpointUri, Component component, Processor logger) {
        super(endpointUri, component);
        setLogger(logger);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        this.localFormatter = exchangeFormatter;
        if (this.localFormatter == null) {
            DefaultExchangeFormatter def = new DefaultExchangeFormatter();
            def.setShowExchangeId(showExchangeId);
            def.setShowExchangePattern(showExchangePattern);
            def.setShowProperties(showProperties);
            def.setShowHeaders(showHeaders);
            def.setSkipBodyLineSeparator(skipBodyLineSeparator);
            def.setShowBody(showBody);
            def.setShowBodyType(showBodyType);
            def.setShowException(showException);
            def.setShowStackTrace(showStackTrace);
            def.setShowAll(showAll);
            def.setMultiline(multiline);
            def.setShowFuture(showFuture);
            def.setShowStreams(showStreams);
            def.setShowFiles(showFiles);
            def.setMaxChars(maxChars);
            def.setStyle(style);
            this.localFormatter = def;
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (logger == null) {
            logger = createLogger();
        }
        ServiceHelper.startService(logger);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(logger);
    }

    public void setLogger(Processor logger) {
        this.logger = logger;
        // the logger is the processor
        setProcessor(this.logger);
    }

    public Processor getLogger() {
        return logger;
    }

    @Override
    public Producer createProducer() throws Exception {
        // ensure logger is created and started first
        if (logger == null) {
            logger = createLogger();
        }
        ServiceHelper.startService(logger);
        return new LogProducer(this, logger);
    }

    @Override
    protected String createEndpointUri() {
        return "log:" + logger.toString();
    }

    /**
     * Creates the logger {@link Processor} to be used.
     */
    protected Processor createLogger() throws Exception {
        Processor answer;
        // setup a new logger here
        CamelLogger camelLogger;
        LoggingLevel loggingLevel = LoggingLevel.INFO;
        if (level != null) {
            loggingLevel = LoggingLevel.valueOf(level);
        }
        if (providedLogger == null) {
            camelLogger = new CamelLogger(loggerName, loggingLevel, getMarker());
        } else {
            camelLogger = new CamelLogger(providedLogger, loggingLevel, getMarker());
        }
        if (getGroupSize() != null) {
            answer = new ThroughputLogger(camelLogger, getGroupSize());
        } else if (getGroupInterval() != null) {
            Boolean groupActiveOnly = getGroupActiveOnly() != null ? getGroupActiveOnly() : Boolean.TRUE;
            Long groupDelay = getGroupDelay();
            answer = new ThroughputLogger(camelLogger, this.getCamelContext(), getGroupInterval(), groupDelay, groupActiveOnly);
        } else {
            answer = new CamelLogProcessor(camelLogger, localFormatter, getMaskingFormatter(), getCamelContext().adapt(ExtendedCamelContext.class).getLogListeners());
        }
        // the logger is the processor
        setProcessor(answer);
        return answer;
    }

    private MaskingFormatter getMaskingFormatter() {
        if (logMask != null ? logMask : getCamelContext().isLogMask()) {
            MaskingFormatter formatter = getCamelContext().getRegistry().lookupByNameAndType(MaskingFormatter.CUSTOM_LOG_MASK_REF, MaskingFormatter.class);
            if (formatter == null) {
                formatter = new DefaultMaskingFormatter();
            }
            return formatter;
        }
        return null;
    }

    /**
     * Logging level to use.
     * <p/>
     * The default value is INFO.
     */
    public String getLevel() {
        return level;
    }

    /**
     * Logging level to use.
     * <p/>
     * The default value is INFO.
     */
    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * An optional Marker name to use.
     */
    public String getMarker() {
        return marker;
    }

    /**
     * An optional Marker name to use.
     */
    public void setMarker(String marker) {
        this.marker = marker;
    }

    /**
     * An integer that specifies a group size for throughput logging.
     */
    public Integer getGroupSize() {
        return groupSize;
    }

    /**
     * An integer that specifies a group size for throughput logging.
     */
    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    /**
     * If specified will group message stats by this time interval (in millis)
     */
    public Long getGroupInterval() {
        return groupInterval;
    }

    /**
     * If specified will group message stats by this time interval (in millis)
     */
    public void setGroupInterval(Long groupInterval) {
        this.groupInterval = groupInterval;
    }

    /**
     * If true, will hide stats when no new messages have been received for a time interval, if false, show stats regardless of message traffic.
     */
    public Boolean getGroupActiveOnly() {
        return groupActiveOnly;
    }

    /**
     * If true, will hide stats when no new messages have been received for a time interval, if false, show stats regardless of message traffic.
     */
    public void setGroupActiveOnly(Boolean groupActiveOnly) {
        this.groupActiveOnly = groupActiveOnly;
    }

    /**
     * Set the initial delay for stats (in millis)
     */
    public Long getGroupDelay() {
        return groupDelay;
    }

    /**
     * Set the initial delay for stats (in millis)
     */
    public void setGroupDelay(Long groupDelay) {
        this.groupDelay = groupDelay;
    }

    public ExchangeFormatter getLocalFormatter() {
        return localFormatter;
    }

    public void setLocalFormatter(ExchangeFormatter localFormatter) {
        this.localFormatter = localFormatter;
    }

    public Logger getProvidedLogger() {
        return providedLogger;
    }

    public void setProvidedLogger(Logger providedLogger) {
        this.providedLogger = providedLogger;
    }

    /**
     * The logger name to use
     */
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * The logger name to use
     */
    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public Boolean getLogMask() {
        return logMask;
    }

    /**
     * If true, mask sensitive information like password or passphrase in the log.
     */
    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    /**
     * To use a custom exchange formatter
     */
    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }

    public boolean isShowExchangeId() {
        return showExchangeId;
    }

    public void setShowExchangeId(boolean showExchangeId) {
        this.showExchangeId = showExchangeId;
    }

    public boolean isShowExchangePattern() {
        return showExchangePattern;
    }

    public void setShowExchangePattern(boolean showExchangePattern) {
        this.showExchangePattern = showExchangePattern;
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

    public boolean isShowException() {
        return showException;
    }

    public void setShowException(boolean showException) {
        this.showException = showException;
    }

    public boolean isShowCaughtException() {
        return showCaughtException;
    }

    public void setShowCaughtException(boolean showCaughtException) {
        this.showCaughtException = showCaughtException;
    }

    public boolean isShowStackTrace() {
        return showStackTrace;
    }

    public void setShowStackTrace(boolean showStackTrace) {
        this.showStackTrace = showStackTrace;
    }

    public boolean isShowAll() {
        return showAll;
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
    }

    public boolean isShowFuture() {
        return showFuture;
    }

    public void setShowFuture(boolean showFuture) {
        this.showFuture = showFuture;
    }

    public boolean isShowStreams() {
        return showStreams;
    }

    public void setShowStreams(boolean showStreams) {
        this.showStreams = showStreams;
    }

    public boolean isShowFiles() {
        return showFiles;
    }

    public void setShowFiles(boolean showFiles) {
        this.showFiles = showFiles;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public DefaultExchangeFormatter.OutputStyle getStyle() {
        return style;
    }

    public void setStyle(DefaultExchangeFormatter.OutputStyle style) {
        this.style = style;
    }
}
