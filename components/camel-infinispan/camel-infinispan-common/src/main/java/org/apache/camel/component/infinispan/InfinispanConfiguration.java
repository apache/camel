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
package org.apache.camel.component.infinispan;

import java.util.function.BiFunction;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;

public abstract class InfinispanConfiguration {
    @UriParam(label = "producer", defaultValue = "PUT",
              description = "The operation that the producer performs. The CamelInfinispanOperation header replaces this"
                            + " option.",
              enums = "PUT,PUTASYNC,PUTALL,PUTALLASYNC,PUTIFABSENT,PUTIFABSENTASYNC,GET,GETORDEFAULT,CONTAINSKEY,CONTAINSVALUE,REMOVE,REMOVEASYNC,REPLACE,REPLACEASYNC,SIZE,CLEAR,CLEARASYNC,QUERY,STATS,COMPUTE,COMPUTEASYNC")
    private InfinispanOperation operation = InfinispanOperation.PUT;

    @UriParam(label = "producer",
              description = "The key of the entry for the producer operations that take a key, such as PUT, GET, REMOVE and"
                            + " COMPUTE. When set, it takes precedence over the CamelInfinispanKey header.")
    private Object key;

    @UriParam(label = "producer",
              description = "The value of the entry for the PUT, PUTIFABSENT and REPLACE operations and their ASYNC variants,"
                            + " the value to look for with CONTAINSVALUE, and the value that REMOVE and REMOVEASYNC compare with"
                            + " the current value, to remove the entry only if they are equal. The CamelInfinispanValue header"
                            + " replaces this option.")
    private Object value;

    @UriParam(label = "producer",
              description = "The value that the REPLACE and REPLACEASYNC operations compare with the current value of the"
                            + " entry, to replace the entry only if they are equal. If not set, an existing entry is replaced"
                            + " whatever its value. The CamelInfinispanOldValue header replaces this option.")
    private Object oldValue;

    @UriParam(label = "producer",
              description = "The value that the GETORDEFAULT operation returns when the key is not in the cache. The"
                            + " CamelInfinispanDefaultValue header replaces this option.")
    private Object defaultValue;

    @UriParam(label = "advanced",
              description = "The location of the configuration file of the cache manager that Camel creates, read from the"
                            + " classpath unless it has a prefix such as file:. Not used when cacheContainer or"
                            + " cacheContainerConfiguration is set. For infinispan-embedded it is an Infinispan XML"
                            + " configuration file, read on the component only; for infinispan it is a Hot Rod client"
                            + " properties file.")
    @Metadata(supportFileReference = true)
    private String configurationUri;

    @UriParam(label = "advanced",
              description = "The name of the header in which the producer stores the result of the operation, keeping the"
                            + " message body. If not set, the result replaces the message body. The"
                            + " CamelInfinispanOperationResultHeader header replaces this option.")
    private String resultHeader;

    @UriParam(label = "advanced",
              description = "The java.util.function.BiFunction that the COMPUTE and COMPUTEASYNC operations call with the key"
                            + " and the current value of the entry, or null if there is none, to compute its new value. If the"
                            + " function returns null, the entry is removed.")
    private BiFunction remappingFunction;

    @UriParam(description = "To use a custom org.apache.camel.component.infinispan.InfinispanQueryBuilder that builds the query"
                            + " of the QUERY operation of the producer, which the CamelInfinispanQueryBuilder header replaces."
                            + " When set, the consumer registers a continuous query instead of a listener, and receives the"
                            + " CacheEntryJoining, CacheEntryUpdated and CacheEntryLeaving events of the entries that match the"
                            + " query.")
    private InfinispanQueryBuilder queryBuilder;

    public InfinispanOperation getOperation() {
        return operation;
    }

    public void setOperation(InfinispanOperation operation) {
        this.operation = operation;
    }

    public InfinispanOperation getOperationOrDefault() {
        return this.operation != null ? operation : InfinispanOperation.PUT;
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    public String getResultHeader() {
        return resultHeader;
    }

    public void setResultHeader(String resultHeader) {
        this.resultHeader = resultHeader;
    }

    public BiFunction getRemappingFunction() {
        return remappingFunction;
    }

    public void setRemappingFunction(BiFunction remappingFunction) {
        this.remappingFunction = remappingFunction;
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public InfinispanQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public void setQueryBuilder(InfinispanQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public boolean hasQueryBuilder() {
        return queryBuilder != null;
    }
}
