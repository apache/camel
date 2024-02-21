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

import java.util.function.Predicate;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.resume.ConsumerListener;
import org.apache.camel.spi.Metadata;

/**
 * Pausable EIP to support resuming processing from last known offset.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "pausable")
@XmlAccessorType(XmlAccessType.FIELD)
public class PausableDefinition extends NoOutputDefinition<PausableDefinition> {

    @XmlTransient
    private ConsumerListener<?, ?> consumerListenerBean;

    @XmlAttribute(required = true)
    @Metadata(required = true, javaType = "org.apache.camel.resume.ConsumerListener")
    private String consumerListener;

    @XmlTransient
    private Predicate<?> untilCheckBean;

    @XmlAttribute(required = true)
    @Metadata(required = true, javaType = "java.util.function.Predicate")
    private String untilCheck;

    @Override
    public String getShortName() {
        return "pausable";
    }

    @Override
    public String getLabel() {
        return "pausable";
    }

    public ConsumerListener<?, ?> getConsumerListenerBean() {
        return consumerListenerBean;
    }

    public void setConsumerListener(ConsumerListener<?, ?> consumerListenerBean) {
        this.consumerListenerBean = consumerListenerBean;
    }

    public String getConsumerListener() {
        return consumerListener;
    }

    public void setConsumerListener(String consumerListener) {
        this.consumerListener = consumerListener;
    }

    public Predicate<?> getUntilCheckBean() {
        return untilCheckBean;
    }

    public void setUntilCheck(Predicate<?> untilCheckBean) {
        this.untilCheckBean = untilCheckBean;
    }

    public String getUntilCheck() {
        return untilCheck;
    }

    public void setUntilCheck(String untilCheck) {
        this.untilCheck = untilCheck;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the consumer listener to use
     */
    public PausableDefinition consumerListener(String consumerListenerRef) {
        setConsumerListener(consumerListenerRef);
        return this;
    }

    /**
     * Sets the consumer listener to use
     */
    public PausableDefinition consumerListener(ConsumerListener<?, ?> consumerListener) {
        setConsumerListener(consumerListener);
        return this;
    }

    /**
     * References to a java.util.function.Predicate to use for until checks.
     *
     * The predicate is responsible for evaluating whether the processing can resume or not. Such predicate should
     * return true if the consumption can resume, or false otherwise. The exact point of when the predicate is called is
     * dependent on the component, and it may be called on either one of the available events. Implementations should
     * not assume the predicate to be called at any specific point.
     */
    public PausableDefinition untilCheck(String untilCheck) {
        setUntilCheck(untilCheck);
        return this;
    }

    /**
     * The java.util.function.Predicate to use for until checks.
     *
     * The predicate is responsible for evaluating whether the processing can resume or not. Such predicate should
     * return true if the consumption can resume, or false otherwise. The exact point of when the predicate is called is
     * dependent on the component, and it may be called on either one of the available events. Implementations should
     * not assume the predicate to be called at any specific point.
     */
    public PausableDefinition untilCheck(Predicate<?> untilCheck) {
        setUntilCheck(untilCheck);
        return this;
    }

}
