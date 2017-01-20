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
package org.apache.camel.example.transformer.demo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The OrderResponse.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderResponse {
    @XmlAttribute
    private String orderId;

    @XmlAttribute
    private boolean accepted;

    @XmlAttribute
    private String description;

    public String getOrderId() {
        return orderId;
    }

    public OrderResponse setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public OrderResponse setAccepted(boolean accepted) {
        this.accepted = accepted;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public OrderResponse setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public String toString() {
        return String.format("OrderResponse[orderId='%s', accepted='%s', description='%s']", orderId, accepted, description);
    }
}
