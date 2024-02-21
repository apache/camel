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
    @UriParam(label = "producer", defaultValue = "PUT", description = "The operation to perform")
    private InfinispanOperation operation = InfinispanOperation.PUT;

    @UriParam(label = "producer", description = "Set a specific key for producer operations")
    private Object key;

    @UriParam(label = "producer", description = "Set a specific value for producer operations")
    private Object value;

    @UriParam(label = "producer", description = "Set a specific old value for some producer operations")
    private Object oldValue;

    @UriParam(label = "producer", description = "Set a specific default value for some producer operations")
    private Object defaultValue;

    @UriParam(label = "advanced", description = "An implementation specific URI for the CacheManager")
    @Metadata(supportFileReference = true)
    private String configurationUri;

    @UriParam(label = "advanced",
              description = "Store the operation result in a header instead of the message body. By default,\n" +
                            "resultHeader == null and the query result is stored in the message body, any \n" +
                            "existing content in the message body is discarded. If resultHeader is set, the \n" +
                            "value is used as the name of the header to store the query result and the original \n" +
                            "message body is preserved. This value can be overridden by an in message header \n" +
                            "named: CamelInfinispanOperationResultHeader")
    private String resultHeader;

    @UriParam(label = "advanced", description = "Set a specific remappingFunction to use in a compute operation.")
    private BiFunction remappingFunction;

    @UriParam(description = "Specifies the query builder.")
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
