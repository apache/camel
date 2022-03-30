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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.ConsumerListener;
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
    @Metadata(required = true, javaType = "org.apache.camel.ConsumerListener")
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
}
