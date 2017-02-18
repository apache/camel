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
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class ChronicleEngineConfiguration {

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
    @UriParam(enums = "PUBLISH,PUBLISH_AND_INDEX,PPUT,PGET_AND_PUT,PPUT_ALL,PPUT_IF_ABSENT,PGET,PGET_AND_REMOVE,PREMOVE,PIS_EMPTY,PSIZE")
    private String action;
    @UriParam(defaultValue = "true")
    private boolean persistent = true;
    @UriParam
    private String clusterName;

    // ****************************
    // CLIENT OPTIONS
    // ****************************

    public WireType getWireType() {
        return wireType;
    }

    /**
     * The Wire type to use, default to binary wire.
     */
    public void setWireType(String wireType) {
        setWireType(WireType.valueOf(wireType));
    }

    /**
     * The Wire type to use, default to binary wire.
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
     * Set if consumer should subscribe to Map events, default true.
     */
    public void setSubscribeMapEvents(boolean subscribeMapEvents) {
        this.subscribeMapEvents = subscribeMapEvents;
    }

    public String[] getFilteredMapEvents() {
        return filteredMapEvents;
    }

    /**
     * A comma separated list of Map event type to filer, valid values are: INSERT, UPDATE, REMOVE.
     */
    public void setFilteredMapEvents(String filteredMapEvents) {
        setFilteredMapEvents(filteredMapEvents.split(","));
    }

    /**
     * The list of Map event type to filer, valid values are: INSERT, UPDATE, REMOVE.
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
     * Set if consumer should subscribe to TopologicalEvents,d efault false.
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
     * Set if consumer should subscribe to TopicEvents,d efault false.
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
     * The default action to perform, valid values are:
     * - PUBLISH
     * - PPUBLISH_AND_INDEX
     * - PPUT
     * - PGET_AND_PUT
     * - PPUT_ALL
     * - PPUT_IF_ABSENT
     * - PGET
     * - PGET_AND_REMOVE
     * - PREMOVE
     * - PIS_EMPTY
     * - PSIZE
     */
    public void setAction(String action) {
        this.action = action;
    }

    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Enable/disable data persistence
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public String getClusterName() {
        return clusterName;
    }

    /**
     * Cluster name for queue
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
}
