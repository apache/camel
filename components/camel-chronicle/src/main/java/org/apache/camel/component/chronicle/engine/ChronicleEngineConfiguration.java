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
package org.apache.camel.component.chronicle.engine;

import net.openhft.chronicle.wire.WireType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class ChronicleEngineConfiguration implements CamelContextAware {

    @UriParam(defaultValue = "BINARY", javaType = "java.lang.String")
    private WireType wireType = WireType.BINARY;

    @UriParam(defaultValue = "true")
    private boolean subscribeMapEvents = true;

    @UriParam(javaType = "java.lang.String")
    private String[] filteredMapEvents;

    @UriParam
    private boolean subscribeTopologicalEvents;

    @UriParam
    private boolean subscribeTopicEvents;

    @UriParam
    private String action;

    @UriParam(defaultValue = "true")
    private boolean persistent = true;

    private CamelContext camelContext;
    private String[] addresses;
    private String path;

    // ****************************
    //
    // ****************************

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String[] getAddresses() {
        return addresses;
    }

    /**
     * Description
     */
    public void setAddresses(String addresses) {
        setAddresses(addresses.split(","));
    }

    /**
     * Description
     */
    public void setAddresses(String[] addresses) {
        this.addresses = addresses;
    }

    public String getPath() {
        return path;
    }

    /**
     * Description
     */
    public void setPath(String path) {
        this.path = path;

        if (!this.path.startsWith("/")) {
            this.path = "/" + this.path;
        }
    }

    // ****************************
    // CLIENT OPTIONS
    // ****************************

    public WireType getWireType() {
        return wireType;
    }

    /**
     * Description
     */
    public void setWireType(String wireType) {
        setWireType(WireType.valueOf(wireType));
    }

    /**
     * Description
     */
    public void setWireType(WireType wireType) {
        this.wireType = wireType;
    }

    // ****************************
    // MAP EVENTS OPTIONS
    // ****************************

    public boolean isSubscribeMapEvents() {
        return subscribeMapEvents;
    }

    /**
     * Description
     */
    public void setSubscribeMapEvents(boolean subscribeMapEvents) {
        this.subscribeMapEvents = subscribeMapEvents;
    }

    public String[] getFilteredMapEvents() {
        return filteredMapEvents;
    }

    /**
     * Description
     */
    public void setFilteredMapEvents(String filteredMapEvents) {
        setFilteredMapEvents(filteredMapEvents.split(","));
    }

    /**
     * Description
     */
    public void setFilteredMapEvents(String[] filteredMapEvents) {
        this.filteredMapEvents = filteredMapEvents;
    }

    // ****************************
    // TOPOLOGICAL EVENTS OPTIONS
    // ****************************

    public boolean isSubscribeTopologicalEvents() {
        return subscribeTopologicalEvents;
    }

    /**
     * Description
     */
    public void setSubscribeTopologicalEvents(boolean subscribeTopologicalEvents) {
        this.subscribeTopologicalEvents = subscribeTopologicalEvents;
    }

    // ****************************
    // TOPIC EVENTS OPTIONS
    // ****************************

    public boolean isSubscribeTopicEvents() {
        return subscribeTopicEvents;
    }

    /**
     * Description
     */
    public void setSubscribeTopicEvents(boolean subscribeTopicEvents) {
        this.subscribeTopicEvents = subscribeTopicEvents;
    }

    // ****************************
    // Misc
    // ****************************

    public String getAction() {
        return action;
    }

    /**
     * Description
     */
    public void setAction(String action) {
        this.action = action;
    }

    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Description
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }
}
