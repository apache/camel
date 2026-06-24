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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslArg;
import org.slf4j.Logger;

/**
 * Used for printing custom messages to the logger.
 */
@Metadata(label = "eip,routing", title = "Logger",
          description = "Logs a message using an expression that can include message body, headers, and other exchange data")
@XmlRootElement(name = "log")
@XmlAccessorType(XmlAccessType.FIELD)
public class LogDefinition extends NoOutputDefinition<LogDefinition> {

    @XmlTransient
    private Logger loggerBean;

    @XmlAttribute(required = true)
    @DslArg(position = 1)
    @Metadata(description = "The log message to output. Supports simple language expressions.")
    private String message;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel", defaultValue = "INFO", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF",
              description = "Sets the logging level to use for the log message. Available levels: TRACE, DEBUG, INFO, WARN, ERROR, OFF.")
    @DslArg(position = 0, renderType = "enumString", typeName = "LoggingLevel")
    private String loggingLevel;
    @XmlAttribute
    @Metadata(description = "The logger name to use. By default the route id is used.")
    private String logName;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "An optional SLF4J marker to use with the log statement.")
    private String marker;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.slf4j.Logger",
              description = "Reference to a custom SLF4J logger instance to use.")
    private String logger;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "The language to use for evaluating the log message, such as simple, groovy, or ognl.")
    private String logLanguage;

    public LogDefinition() {
    }

    protected LogDefinition(LogDefinition source) {
        super(source);
        this.loggerBean = source.loggerBean;
        this.message = source.message;
        this.loggingLevel = source.loggingLevel;
        this.logName = source.logName;
        this.marker = source.marker;
        this.logger = source.logger;
    }

    public LogDefinition(String message) {
        this();
        this.message = message;
    }

    @Override
    public LogDefinition copyDefinition() {
        return new LogDefinition(this);
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
        String m = message;
        if (m != null) {
            // log messages may have new lines so replace them with a space as we want the label to be in a single line
            m = m.replace('\n', ' ');
        }
        return "log[" + m + "]";
    }

    public Logger getLoggerBean() {
        return loggerBean;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLogName() {
        return logName;
    }

    public void setLogName(String logName) {
        this.logName = logName;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public void setLogger(Logger logger) {
        this.loggerBean = logger;
    }

    public String getLogger() {
        return logger;
    }

    public String getLogLanguage() {
        return logLanguage;
    }

    public void setLogLanguage(String logLanguage) {
        this.logLanguage = logLanguage;
    }
}
