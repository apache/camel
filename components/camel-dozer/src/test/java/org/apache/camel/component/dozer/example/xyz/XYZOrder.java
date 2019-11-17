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
package org.apache.camel.component.dozer.example.xyz;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * XYZOrder
 * <p>
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "custId",
    "priority",
    "orderId",
    "lineItems"
})
public class XYZOrder {

    @JsonProperty("custId")
    private String custId;
    @JsonProperty("priority")
    private String priority;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("lineItems")
    private List<LineItem> lineItems = new ArrayList<>();

    /**
     * 
     * @return
     *     The custId
     */
    @JsonProperty("custId")
    public String getCustId() {
        return custId;
    }

    /**
     * 
     * @param custId
     *     The custId
     */
    @JsonProperty("custId")
    public void setCustId(String custId) {
        this.custId = custId;
    }

    /**
     * 
     * @return
     *     The priority
     */
    @JsonProperty("priority")
    public String getPriority() {
        return priority;
    }

    /**
     * 
     * @param priority
     *     The priority
     */
    @JsonProperty("priority")
    public void setPriority(String priority) {
        this.priority = priority;
    }

    /**
     * 
     * @return
     *     The orderId
     */
    @JsonProperty("orderId")
    public String getOrderId() {
        return orderId;
    }

    /**
     * 
     * @param orderId
     *     The orderId
     */
    @JsonProperty("orderId")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /**
     * 
     * @return
     *     The lineItems
     */
    @JsonProperty("lineItems")
    public List<LineItem> getLineItems() {
        return lineItems;
    }

    /**
     * 
     * @param lineItems
     *     The lineItems
     */
    @JsonProperty("lineItems")
    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

}
