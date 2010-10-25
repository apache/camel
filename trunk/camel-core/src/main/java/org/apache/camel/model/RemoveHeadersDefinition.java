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

import org.apache.camel.Processor;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;removeHeaders/&gt; element
 */
@XmlRootElement(name = "removeHeaders")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemoveHeadersDefinition extends OutputDefinition<RemoveHeadersDefinition> {
    @XmlAttribute(required = true)
    private String pattern;

    public RemoveHeadersDefinition() {
    }

    public RemoveHeadersDefinition(String pattern) {
        setPattern(pattern);
    }

    @Override
    public String toString() {
        return "RemoveHeaders[" + getPattern() + "]";
    }

    @Override
    public String getShortName() {
        return "removeHeaders";
    }

    @Override
    public String getLabel() {
        return "removeHeaders[" + getPattern() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ObjectHelper.notNull(getPattern(), "patterns", this);
        return ProcessorBuilder.removeHeaders(getPattern());
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }
}