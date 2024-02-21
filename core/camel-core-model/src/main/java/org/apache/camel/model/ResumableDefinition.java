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

import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.ResumeStrategyConfigurationBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Resume EIP to support resuming processing from last known offset.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "resumable")
@XmlAccessorType(XmlAccessType.FIELD)
public class ResumableDefinition extends NoOutputDefinition<ResumableDefinition> {

    @XmlTransient
    private ResumeStrategy resumeStrategyBean;

    @XmlAttribute(required = true)
    @Metadata(required = true, javaType = "org.apache.camel.resume.ResumeStrategy")
    private String resumeStrategy;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.LoggingLevel", defaultValue = "ERROR",
              enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private String loggingLevel;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String intermittent;

    @XmlTransient
    private ResumeStrategyConfiguration resumeStrategyConfiguration;

    @Override
    public String getShortName() {
        return "resumable";
    }

    @Override
    public String getLabel() {
        return "resumable";
    }

    public ResumeStrategy getResumeStrategyBean() {
        return resumeStrategyBean;
    }

    public String getResumeStrategy() {
        return resumeStrategy;
    }

    public void setResumeStrategy(String resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    public void setResumeStrategy(ResumeStrategy resumeStrategyBean) {
        this.resumeStrategyBean = resumeStrategyBean;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(String loggingLevelRef) {
        this.loggingLevel = loggingLevelRef;
    }

    public String getIntermittent() {
        return intermittent;
    }

    public void setIntermittent(String intermitent) {
        this.intermittent = intermitent;
    }

    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return resumeStrategyConfiguration;
    }

    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.resumeStrategyConfiguration = resumeStrategyConfiguration;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the resume strategy to use
     */
    public ResumableDefinition resumeStrategy(String resumeStrategyRef) {
        setResumeStrategy(resumeStrategyRef);
        return this;
    }

    /**
     * Sets the resume strategy to use
     */
    public ResumableDefinition resumeStrategy(String resumeStrategyRef, String loggingLevelRef) {
        setResumeStrategy(resumeStrategyRef);
        setLoggingLevel(loggingLevelRef);
        return this;
    }

    /**
     * Sets the resume strategy to use
     */
    public ResumableDefinition resumeStrategy(ResumeStrategy resumeStrategy) {
        setResumeStrategy(resumeStrategy);
        return this;
    }

    /**
     * Sets the resume strategy to use
     */
    public ResumableDefinition resumeStrategy(ResumeStrategy resumeStrategy, String loggingLevelRef) {
        setResumeStrategy(resumeStrategy);
        setLoggingLevel(loggingLevelRef);
        return this;
    }

    /***
     * Uses a configuration builder to auto-instantiate the resume strategy
     */
    public ResumableDefinition configuration(
            ResumeStrategyConfigurationBuilder<? extends ResumeStrategyConfigurationBuilder, ? extends ResumeStrategyConfiguration> builder) {
        setResumeStrategyConfiguration(builder.build());
        return this;
    }

    /**
     * Sets whether the offsets will be intermittently present or whether they must be present in every exchange
     */
    public ResumableDefinition intermittent(boolean intermittent) {
        setIntermittent(Boolean.toString(intermittent));

        return this;
    }
}
