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
package org.apache.camel.model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.Metadata;
import org.slf4j.Logger;

/**
 * Logs the defined message to the logger
 */
@Metadata(label = "eip,configuration")
@XmlRootElement(name = "log")
@XmlAccessorType(XmlAccessType.FIELD)
public class LogDefinition extends NoOutputDefinition<LogDefinition> implements PropertyPlaceholderAware {

    @XmlAttribute(required = true)
    private String message;
    @XmlAttribute @Metadata(defaultValue = "INFO")
    private LoggingLevel loggingLevel;
    @XmlAttribute
    private String logName;
    @XmlAttribute
    private String marker;
    @XmlAttribute
    private String loggerRef;
    @XmlTransient
    private Logger logger;

    private final Map<String, Supplier<String>> readPlaceholders = new HashMap<>();
    private final Map<String, Consumer<String>> writePlaceholders = new HashMap<>();

    public LogDefinition() {
        readPlaceholders.put("id", this::getId);
        readPlaceholders.put("message", this::getMessage);
        readPlaceholders.put("logName", this::getLogName);
        readPlaceholders.put("marker", this::getMarker);
        readPlaceholders.put("loggerRef", this::getLoggerRef);
        writePlaceholders.put("id", this::setId);
        writePlaceholders.put("message", this::setMessage);
        writePlaceholders.put("logName", this::setLogName);
        writePlaceholders.put("marker", this::setMarker);
        writePlaceholders.put("loggerRef", this::setLoggerRef);
    }

    public LogDefinition(String message) {
        this();
        this.message = message;
    }

    @Override
    public String toString() {
        return "Log[" + message + "]";
    }
    
    @Override
    public String getShortName() {
        return "log";
    }

    @Override
    public String getLabel() {
        return "log";
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Sets the logging level.
     * <p/>
     * The default value is INFO
     */
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Sets the log message (uses simple language)
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public String getLogName() {
        return logName;
    }

    /**
     * Sets the name of the logger
     */
    public void setLogName(String logName) {
        this.logName = logName;
    }

    public String getMarker() {
        return marker;
    }

    /**
     * To use slf4j marker
     */
    public void setMarker(String marker) {
        this.marker = marker;
    }

    public String getLoggerRef() {
        return loggerRef;
    }

    /**
     * To refer to a custom logger instance to lookup from the registry.
     */
    public void setLoggerRef(String loggerRef) {
        this.loggerRef = loggerRef;
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * To use a custom logger instance
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Map<String, Supplier<String>> getReadPropertyPlaceholderOptions(CamelContext camelContext) {
        return readPlaceholders;
    }

    @Override
    public Map<String, Consumer<String>> getWritePropertyPlaceholderOptions(CamelContext camelContext) {
        return writePlaceholders;
    }
}
