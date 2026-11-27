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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.LineNumberAware;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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

import static org.apache.camel.support.LoggerHelper.getLineNumberLoggerName;

/**
 * Log the exchanges that pass through a route, or their throughput, with an SLF4J logger.
 */
@UriEndpoint(firstVersion = "1.1.0", scheme = "log", title = "Log Data",
             remote = false, internal = true, syntax = "log:loggerName", producerOnly = true,
             category = { Category.CORE, Category.MONITORING })
@BodyTypes(producerIn = @BodyType(Carrier.ANY), producerOut = @BodyType(Carrier.UNCHANGED))
public class LogEndpoint extends ProcessorEndpoint implements LineNumberAware {

    private volatile Processor logger;
    private Logger providedLogger;
    private ExchangeFormatter localFormatter;
    private int lineNumber;
    private String location;

    @UriPath(description = "The name of the SLF4J logger, also called the logging category. It is not used when an"
                           + " org.slf4j.Logger is given, by the logger URI parameter or as the only Logger bean in the registry,"
                           + " or when sourceLocationLoggerName resolves a name.")
    @Metadata(required = true)
    private String loggerName;
    @UriParam(defaultValue = "INFO", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF",
              description = "The logging level of the log messages. OFF logs nothing.")
    private String level;
    @UriParam(description = "The name of an SLF4J marker to add to each log message.")
    private String marker;
    @UriParam(description = "Turns on throughput logging: instead of each exchange, the endpoint logs statistics (the number"
                            + " of exchanges and the messages per second) after every groupSize exchanges. It takes precedence"
                            + " over groupInterval, and the formatting options are not used.")
    private Integer groupSize;
    @UriParam(description = "Turns on throughput logging: instead of each exchange, the endpoint logs statistics (the number"
                            + " of new and total exchanges and the messages per second) every interval, in milliseconds, from a"
                            + " background thread. It is not used when groupSize is set.")
    private Long groupInterval;
    @UriParam(defaultValue = "true",
              description = "Whether the groupInterval throughput logging skips an interval in which no new exchanges"
                            + " arrived. If false, it logs the statistics every interval.")
    private boolean groupActiveOnly = true;
    @UriParam(description = "The delay, in milliseconds, before the groupInterval throughput logging logs its first"
                            + " statistics. If not set, 1000 is used.")
    private Long groupDelay;
    @UriParam(description = "Whether to mask sensitive values, such as passwords and passphrases, in the log message. If not"
                            + " set, the logMask setting of the route, or of the CamelContext, is used. A"
                            + " org.apache.camel.spi.MaskingFormatter bean named CamelCustomLogMask in the registry replaces the"
                            + " default masking.")
    private Boolean logMask;
    @UriParam(label = "advanced",
              description = "A custom org.apache.camel.spi.ExchangeFormatter that turns the exchange into the log message."
                            + " When set, the formatting options of the endpoint are not used. If not set, the exchangeFormatter"
                            + " of the component, or a bean named logFormatter in the registry, is used.")
    private ExchangeFormatter exchangeFormatter;
    @UriParam(label = "formatting", description = "Show route ID.")
    private boolean showRouteId;
    @UriParam(label = "formatting", description = "Show the group of the route, if the route has one.")
    private boolean showRouteGroup;
    @UriParam(label = "formatting", description = "Show the unique exchange ID.")
    private boolean showExchangeId;
    @UriParam(label = "formatting",
              description = "Show the exchange pattern (InOnly or InOut). When none of the formatting options is set, the"
                            + " endpoint uses the default output of the component, which shows the exchange pattern anyway.")
    private boolean showExchangePattern;
    @UriParam(label = "formatting",
              description = "Show the exchange properties (only custom). Use showAllProperties to show both internal and custom properties.")
    private boolean showProperties;
    @UriParam(label = "formatting", description = "Show all of the exchange properties (both internal and custom).")
    private boolean showAllProperties;
    @UriParam(label = "formatting", description = "Show the variables of the exchange, if it has any.")
    private boolean showVariables;
    @UriParam(label = "formatting", description = "Show the message headers.")
    private boolean showHeaders;
    @UriParam(label = "formatting", defaultValue = "true",
              description = "Whether to remove the line separators of the operating system from the body, so the body is"
                            + " logged on one line. If false, the body is logged as is.")
    private boolean skipBodyLineSeparator = true;
    @UriParam(label = "formatting", defaultValue = "true", description = "Show the message body.")
    private boolean showBody = true;
    @UriParam(label = "formatting", defaultValue = "true", description = "Show the body Java type.")
    private boolean showBodyType = true;
    @UriParam(label = "formatting",
              description = "If the exchange has an exception, show the type and message of the exception (not the stack"
                            + " trace).")
    private boolean showException;
    @UriParam(label = "formatting",
              description = "Show the type and message of the exception of the exchange or, if it has none, of the caught"
                            + " exception (not the stack trace). Camel stores a caught exception in the CamelExceptionCaught"
                            + " exchange property, for example when doCatch catches it or onException handles it.")
    private boolean showCaughtException;
    @UriParam(label = "formatting",
              description = "Show the stack trace of the exception that showException, showCaughtException or showAll"
                            + " shows. It has no effect without one of them.")
    private boolean showStackTrace;
    @UriParam(label = "formatting",
              description = "Show all parts of the exchange: the ID, the route group and route ID, the exchange pattern, all"
                            + " properties, the variables, the headers, the body type and body, the exception or caught exception,"
                            + " and its stack trace. It does not change multiline, maxChars, or the stream, file and Future"
                            + " options.")
    private boolean showAll;
    @UriParam(label = "formatting", description = "Whether to log each part of the exchange on its own line.")
    private boolean multiline;
    @UriParam(label = "formatting",
              description = "Whether to wait for a java.util.concurrent.Future body to complete and log its result. If false,"
                            + " the toString() of the Future is logged.")
    private boolean showFuture;
    @UriParam(label = "formatting", defaultValue = "true",
              description = "Whether to log a body that is an org.apache.camel.StreamCache. The stream cache is reset after"
                            + " it is read, so the body can be read again later. If false, a placeholder is logged instead,"
                            + " unless showStreams is true.")
    private boolean showCachedStreams = true;
    @UriParam(label = "formatting",
              description = "Whether to log a body that is a stream, such as a java.io.InputStream or a java.io.Reader, or a"
                            + " stream cache. If false, a placeholder is logged instead. Beware that logging reads the stream, so"
                            + " later steps may not be able to read the body; use stream caching to avoid this.")
    private boolean showStreams;
    @UriParam(label = "formatting",
              description = "Whether to log the content of a body that is a file, such as a java.io.File, a java.nio.file.Path"
                            + " or a file of the file or ftp components. If false, a placeholder with the file name is logged"
                            + " instead.")
    private boolean showFiles;
    @UriParam(label = "formatting", defaultValue = "10000",
              description = "The maximum number of characters to log. A longer body is cut at this length, and so is a longer"
                            + " log message, or each line of it with multiline. The global option CamelLogDebugBodyMaxChars, when"
                            + " set, replaces this limit for the body.")
    private int maxChars = 10000;
    @UriParam(label = "formatting", enums = "Default,Tab,Fixed", defaultValue = "Default",
              description = "The layout of the parts of the log message. Default separates them with a comma, as Label:"
                            + " value. Tab separates them with a tab, as Label: value. Fixed puts each value after a tab and the"
                            + " label padded to 20 characters.")
    private DefaultExchangeFormatter.OutputStyle style = DefaultExchangeFormatter.OutputStyle.Default;
    @UriParam(defaultValue = "false",
              description = "Whether to log only the body, without the Exchange[...] wrapper and the other parts. The stream,"
                            + " file and maxChars options still apply to the body.")
    private boolean plain;
    @UriParam(description = "Whether to use the source location of the log endpoint in the route, the file or class name and"
                            + " the line number, as the logger name instead of loggerName. If the source location is not known,"
                            + " for example because source location is not enabled, loggerName is used.")
    private boolean sourceLocationLoggerName;

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
    public boolean isRemote() {
        return false;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // ensure component is injected
        if (getComponent() == null) {
            setComponent(getCamelContext().getComponent("log"));
        }

        this.localFormatter = exchangeFormatter;
        if (this.localFormatter == null) {

            // are any options configured if not we can optimize to use shared default
            boolean changed = showExchangePattern || !skipBodyLineSeparator || !showBody || !showBodyType || maxChars != 10000
                    || style != DefaultExchangeFormatter.OutputStyle.Default || plain;
            changed |= showRouteId || showRouteGroup;
            changed |= showExchangeId || showProperties || showAllProperties || showVariables || showHeaders || showException
                    || showCaughtException
                    || showStackTrace;
            changed |= showAll || multiline || showFuture || !showCachedStreams || showStreams || showFiles;

            if (changed) {
                DefaultExchangeFormatter def = new DefaultExchangeFormatter();
                def.setPlain(plain);
                def.setShowRouteId(showRouteId);
                def.setShowRouteGroup(showRouteGroup);
                def.setShowAll(showAll);
                def.setShowBody(showBody);
                def.setShowBodyType(showBodyType);
                def.setShowCaughtException(showCaughtException);
                def.setShowException(showException);
                def.setShowExchangeId(showExchangeId);
                def.setShowExchangePattern(showExchangePattern);
                def.setShowFiles(showFiles);
                def.setShowFuture(showFuture);
                def.setShowHeaders(showHeaders);
                def.setShowVariables(showVariables);
                def.setShowProperties(showProperties);
                def.setShowAllProperties(showAllProperties);
                def.setShowStackTrace(showStackTrace);
                def.setShowCachedStreams(showCachedStreams);
                def.setShowStreams(showStreams);
                def.setMaxChars(maxChars);
                def.setMultiline(multiline);
                def.setSkipBodyLineSeparator(skipBodyLineSeparator);
                def.setStyle(style);
                this.localFormatter = def;
            } else {
                this.localFormatter = getComponent().getDefaultExchangeFormatter();
            }
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

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public void setLocation(String location) {
        this.location = location;
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

    @Override
    public LogComponent getComponent() {
        return (LogComponent) super.getComponent();
    }

    /**
     * Creates the logger {@link Processor} to be used.
     */
    protected Processor createLogger() {
        Processor answer;
        // setup a new logger here
        CamelLogger camelLogger;
        LoggingLevel loggingLevel = LoggingLevel.INFO;
        if (level != null && !level.equals("INFO")) {
            loggingLevel = LoggingLevel.valueOf(level);
        }
        if (providedLogger == null) {
            String name = loggerName;
            if (sourceLocationLoggerName) {
                name = getLineNumberLoggerName(this);
                if (name == null) {
                    name = loggerName;
                }
            }
            camelLogger = new CamelLogger(name, loggingLevel, getMarker());
        } else {
            camelLogger = new CamelLogger(providedLogger, loggingLevel, getMarker());
        }
        if (getGroupSize() != null) {
            answer = new ThroughputLogger(camelLogger, getGroupSize());
        } else if (getGroupInterval() != null) {
            Long groupDelay = getGroupDelay();
            answer = new ThroughputLogger(camelLogger, this.getCamelContext(), getGroupInterval(), groupDelay, groupActiveOnly);
        } else {
            CamelLogProcessor clp = new CamelLogProcessor(
                    camelLogger, localFormatter, getMaskingFormatter(),
                    getCamelContext().getCamelContextExtension().getLogListeners());
            clp.setRouteLogMaskAware(true);
            clp.setEndpointLogMask(logMask);
            answer = clp;
        }
        // the logger is the processor
        setProcessor(answer);
        return answer;
    }

    private MaskingFormatter getMaskingFormatter() {
        if (logMask != null ? logMask : getCamelContext().isLogMask()) {
            MaskingFormatter formatter = getCamelContext().getRegistry()
                    .lookupByNameAndType(MaskingFormatter.CUSTOM_LOG_MASK_REF, MaskingFormatter.class);
            if (formatter == null) {
                formatter = new DefaultMaskingFormatter();
            }
            return formatter;
        }
        return null;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    public Long getGroupInterval() {
        return groupInterval;
    }

    public void setGroupInterval(Long groupInterval) {
        this.groupInterval = groupInterval;
    }

    public boolean isGroupActiveOnly() {
        return groupActiveOnly;
    }

    public void setGroupActiveOnly(boolean groupActiveOnly) {
        this.groupActiveOnly = groupActiveOnly;
    }

    public Long getGroupDelay() {
        return groupDelay;
    }

    public void setGroupDelay(Long groupDelay) {
        this.groupDelay = groupDelay;
    }

    public Logger getProvidedLogger() {
        return providedLogger;
    }

    public void setProvidedLogger(Logger providedLogger) {
        this.providedLogger = providedLogger;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public Boolean getLogMask() {
        return logMask;
    }

    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }

    public boolean isShowRouteId() {
        return showRouteId;
    }

    public void setShowRouteId(boolean showRouteId) {
        this.showRouteId = showRouteId;
    }

    public boolean isShowRouteGroup() {
        return showRouteGroup;
    }

    public void setShowRouteGroup(boolean showRouteGroup) {
        this.showRouteGroup = showRouteGroup;
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

    public boolean isShowAllProperties() {
        return showAllProperties;
    }

    public void setShowAllProperties(boolean showAllProperties) {
        this.showAllProperties = showAllProperties;
    }

    public boolean isShowVariables() {
        return showVariables;
    }

    public void setShowVariables(boolean showVariables) {
        this.showVariables = showVariables;
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

    public boolean isShowCachedStreams() {
        return showCachedStreams;
    }

    public void setShowCachedStreams(boolean showCachedStreams) {
        this.showCachedStreams = showCachedStreams;
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

    public boolean isPlain() {
        return plain;
    }

    public void setPlain(boolean plain) {
        this.plain = plain;
    }

    public boolean isSourceLocationLoggerName() {
        return sourceLocationLoggerName;
    }

    public void setSourceLocationLoggerName(boolean sourceLocationLoggerName) {
        this.sourceLocationLoggerName = sourceLocationLoggerName;
    }
}
