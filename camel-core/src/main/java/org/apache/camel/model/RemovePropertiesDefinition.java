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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.RemovePropertiesProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Removes message exchange properties whose name matches a specified pattern
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "removeProperties")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemovePropertiesDefinition extends NoOutputDefinition<RemovePropertiesDefinition> {
    @XmlAttribute(required = true)
    private String pattern;
    @XmlAttribute
    private String excludePattern;
    // in XML we cannot use String[] for attributes, so we provide a single attribute instead
    @XmlTransient
    private String[] excludePatterns;

    public RemovePropertiesDefinition() {
    }

    public RemovePropertiesDefinition(String pattern) {
        setPattern(pattern);
    }
    
    public RemovePropertiesDefinition(String pattern, String... excludePatterns) {
        setPattern(pattern);
        setExcludePatterns(excludePatterns);
    }

    @Override
    public String toString() {
        return "removeProperties[" + getPattern() + "]";
    }

    @Override
    public String getLabel() {
        return "removeProperties[" + getPattern() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ObjectHelper.notNull(getPattern(), "patterns", this);
        if (getExcludePatterns() != null) {
            return new RemovePropertiesProcessor(getPattern(), getExcludePatterns());
        } else if (getExcludePattern() != null) {
            return new RemovePropertiesProcessor(getPattern(), new String[]{getExcludePattern()});
        } else {
            return new RemovePropertiesProcessor(getPattern(), null);
        }
    }

    /**
     * Name or pattern of properties to remove
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public String[] getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * Name or pattern of properties to not remove
     */
    public void setExcludePatterns(String[] excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    /**
     * Name or pattern of properties to not remove
     */
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }
}