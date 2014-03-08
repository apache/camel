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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;

/**
 * The JAXB type class for the configuration of stream caching
 *
 * @version 
 */
@XmlRootElement(name = "streamCaching")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelStreamCachingStrategyDefinition extends IdentifiedType {

    @XmlAttribute
    private String enabled;

    @XmlAttribute
    private String spoolDirectory;

    @XmlAttribute
    private String spoolChiper;

    @XmlAttribute
    private String spoolThreshold;

    @XmlAttribute
    private String spoolUsedHeapMemoryThreshold;

    @XmlAttribute
    private String spoolUsedHeapMemoryLimit;

    @XmlAttribute
    private String spoolRules;

    @XmlAttribute
    private String bufferSize;

    @XmlAttribute
    private String removeSpoolDirectoryWhenStopping;

    @XmlAttribute
    private String statisticsEnabled;

    @XmlAttribute
    private String anySpoolRules;

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public String getSpoolDirectory() {
        return spoolDirectory;
    }

    public void setSpoolDirectory(String spoolDirectory) {
        this.spoolDirectory = spoolDirectory;
    }

    public String getSpoolChiper() {
        return spoolChiper;
    }

    public void setSpoolChiper(String spoolChiper) {
        this.spoolChiper = spoolChiper;
    }

    public String getSpoolThreshold() {
        return spoolThreshold;
    }

    public void setSpoolThreshold(String spoolThreshold) {
        this.spoolThreshold = spoolThreshold;
    }

    public String getSpoolUsedHeapMemoryThreshold() {
        return spoolUsedHeapMemoryThreshold;
    }

    public void setSpoolUsedHeapMemoryThreshold(String spoolUsedHeapMemoryThreshold) {
        this.spoolUsedHeapMemoryThreshold = spoolUsedHeapMemoryThreshold;
    }

    public String getSpoolUsedHeapMemoryLimit() {
        return spoolUsedHeapMemoryLimit;
    }

    public void setSpoolUsedHeapMemoryLimit(String spoolUsedHeapMemoryLimit) {
        this.spoolUsedHeapMemoryLimit = spoolUsedHeapMemoryLimit;
    }

    public String getSpoolRules() {
        return spoolRules;
    }

    public void setSpoolRules(String spoolRules) {
        this.spoolRules = spoolRules;
    }

    public String getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(String bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getRemoveSpoolDirectoryWhenStopping() {
        return removeSpoolDirectoryWhenStopping;
    }

    public void setRemoveSpoolDirectoryWhenStopping(String removeSpoolDirectoryWhenStopping) {
        this.removeSpoolDirectoryWhenStopping = removeSpoolDirectoryWhenStopping;
    }

    public String getStatisticsEnabled() {
        return statisticsEnabled;
    }

    public void setStatisticsEnabled(String statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    public String getAnySpoolRules() {
        return anySpoolRules;
    }

    public void setAnySpoolRules(String anySpoolRules) {
        this.anySpoolRules = anySpoolRules;
    }
}