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
import org.slf4j.Logger;

/**
 * Used for printing custom messages to the logger.
 */
@Metadata(label = "eip,routing", title = "Logger")
@XmlRootElement(name = "log")
@XmlAccessorType(XmlAccessType.FIELD)
public class LogDefinition extends NoOutputDefinition<LogDefinition> {

    @XmlTransient
    private Logger loggerBean;

    @XmlAttribute(required = true)
    private String message;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel", defaultValue = "INFO", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private String loggingLevel;
    @XmlAttribute
    private String logName;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String marker;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.slf4j.Logger")
    private String logger;
    @XmlAttribute
    @Metadata(label = "advanced")
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
        return "log";
    }

    public Logger getLoggerBean() {
        return loggerBean;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Sets the logging level.
     * <p/>
     * The default value is INFO
     */
    public void setLoggingLevel(String loggingLevel) {
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
     * Sets the name of the logger.
     *
     * The name is default the routeId or the source:line if source location is enabled. You can also specify the name
     * using tokens:
     *
     * <br/>
     * ${class} - the logger class name (org.apache.camel.processor.LogProcessor) <br/>
     * ${contextId} - the camel context id <br/>
     * ${routeId} - the route id <br/>
     * ${groupId} - the route group id <br/>
     * ${nodeId} - the node id <br/>
     * ${nodePrefixId} - the node prefix id <br/>
     * ${source} - the source:line (source location must be enabled) <br/>
     * ${source.name} - the source filename (source location must be enabled) <br/>
     * ${source.line} - the source line number (source location must be enabled)
     *
     * For example to use the route and node id you can specify the name as: ${routeId}/${nodeId}
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

    /**
     * To refer to a custom logger instance to lookup from the registry.
     */
    public void setLogger(String logger) {
        this.logger = logger;
    }

    /**
     * To use a custom logger instance
     */
    public void setLogger(Logger logger) {
        this.loggerBean = logger;
    }

    public String getLogger() {
        return logger;
    }

    public String getLogLanguage() {
        return logLanguage;
    }

    /**
     * To configure the language to use. By default, the simple language is used. However, Camel also supports other
     * languages such as groovy.
     */
    public void setLogLanguage(String logLanguage) {
        this.logLanguage = logLanguage;
    }
}
