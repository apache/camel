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

/**
 * Removes message headers whose name matches a specified pattern
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "removeHeaders")
@XmlAccessorType(XmlAccessType.FIELD)
public class RemoveHeadersDefinition extends NoOutputDefinition<RemoveHeadersDefinition> {

    // in XML we cannot use String[] for attributes, so we provide a single
    // attribute instead
    @XmlTransient
    private String[] excludePatterns;

    @XmlAttribute(required = true)
    private String pattern;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String excludePattern;

    public RemoveHeadersDefinition() {
    }

    protected RemoveHeadersDefinition(RemoveHeadersDefinition source) {
        super(source);
        this.excludePatterns = source.excludePatterns;
        this.pattern = source.pattern;
        this.excludePattern = source.excludePattern;
    }

    public RemoveHeadersDefinition(String pattern) {
        setPattern(pattern);
    }

    public RemoveHeadersDefinition(String pattern, String... excludePatterns) {
        setPattern(pattern);
        setExcludePatterns(excludePatterns);
    }

    @Override
    public RemoveHeadersDefinition copyDefinition() {
        return new RemoveHeadersDefinition(this);
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

    /**
     * Name or pattern of headers to remove. The pattern is matched in the following order: 1 = exact match 2 = wildcard
     * (pattern ends with a * and the name starts with the pattern) 3 = regular expression (all of above is case
     * in-sensitive).
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
     * Name or pattern of headers to not remove. The pattern is matched in the following order: 1 = exact match 2 =
     * wildcard (pattern ends with a * and the name starts with the pattern) 3 = regular expression (all of above is
     * case in-sensitive).
     */
    public void setExcludePatterns(String[] excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    /**
     * Name or patter of headers to not remove. The pattern is matched in the following order: 1 = exact match 2 =
     * wildcard (pattern ends with a * and the name starts with the pattern) 3 = regular expression (all of above is
     * case in-sensitive).
     */
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }
}
