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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;log/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "log")
@XmlAccessorType(XmlAccessType.FIELD)
public class LogDefinition extends NoOutputDefinition {

    @XmlAttribute
    private String message;
    @XmlAttribute
    private LoggingLevel loggingLevel;
    @XmlAttribute
    private String logName;

    public LogDefinition() {
    }

    public LogDefinition(String message) {
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
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ObjectHelper.notEmpty(message, "message", this);

        // use simple language for the message string to give it more power
        Expression exp = routeContext.getCamelContext().resolveLanguage("simple").createExpression(message);

        String name = getLogName();
        if (name == null) {
            name = routeContext.getRoute().getId();
        }
        // should be INFO by default
        LoggingLevel level = getLoggingLevel() != null ? getLoggingLevel() : LoggingLevel.INFO;
        CamelLogger logger = new CamelLogger(name, level);

        return new LogProcessor(exp, logger);
    }

    @Override
    public void addOutput(ProcessorDefinition output) {
        // add outputs on parent as this log does not support outputs
        getParent().addOutput(output);
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(LoggingLevel loggingLevel) {
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

}