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
package org.apache.camel.component.infinispan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.infinispan.commons.api.BasicCacheContainer;

@UriParams
public class InfinispanConfiguration {
    private BasicCacheContainer cacheContainer;
    @UriPath @Metadata(required = "true")
    private String host;
    @UriParam
    private String cacheName;
    @UriParam(label = "producer", enums = "put,putAll,putIfAbsent,putAsync,putAllAsync,putIfAbsentAsync,get,containsKey,containsValue,remove,removeAsync," 
           + "replace,replaceAsync,clear,size")
    private String command;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean sync = true;
    @UriParam(label = "consumer")
    private Set<String> eventTypes;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Specifies the host of the cache on Infinispan instance
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Specifies the cache Container to connect
     */   
    public BasicCacheContainer getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(BasicCacheContainer cacheContainer) {
        this.cacheContainer = cacheContainer;
    }


    /**
     * Specifies the cache name
     */  
    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
    * If true, the consumer will receive notifications synchronously
    */
    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    /**
    * Specifies the set of event types to register by the consumer 
    */
    public Set<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(Set<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = new HashSet<String>(Arrays.asList(eventTypes.split(",")));
    }
}
