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

import org.apache.camel.spi.UriParam;

public abstract class InfinispanConfiguration {
    @UriParam(label = "producer", defaultValue = "PUT")
    private InfinispanOperation operation = InfinispanOperation.PUT;
    @UriParam(label = "producer")
    private Object key;
    @UriParam(label = "producer")
    private Object value;
    @UriParam(label = "producer")
    private Object oldValue;
    @UriParam(label = "producer")
    private Object defaultValue;
    @Deprecated
    @UriParam(label = "consumer", defaultValue = "PUT")
    private String command = "PUT";
    @UriParam(label = "advanced")
    private String configurationUri;
    @UriParam(label = "advanced")
    private String resultHeader;
    @UriParam(label = "advanced")
    private BiFunction remappingFunction;
    @UriParam
    private InfinispanQueryBuilder queryBuilder;

    public String getCommand() {
        return operation.toString();
    }

    /**
     * The operation to perform.
     *
     * @deprecated replaced by @{link setOperation}
     */
    @Deprecated
    public void setCommand(String command) {
        if (command.startsWith(InfinispanConstants.OPERATION)) {
            command = command.substring(InfinispanConstants.OPERATION.length()).toUpperCase();
        }

        setOperation(InfinispanOperation.valueOf(command));
    }

    public InfinispanOperation getOperation() {
        return operation;
    }

    /**
     * The operation to perform.
     */
    public void setOperation(InfinispanOperation operation) {
        this.operation = operation;
    }

    public InfinispanOperation getOperationOrDefault() {
        return this.operation != null ? operation : InfinispanOperation.PUT;
    }

    /**
     * An implementation specific URI for the CacheManager
     */
    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    public String getResultHeader() {
        return resultHeader;
    }

    /**
     * Store the operation result in a header instead of the message body. By default, resultHeader == null and the
     * query result is stored in the message body, any existing content in the message body is discarded. If
     * resultHeader is set, the value is used as the name of the header to store the query result and the original
     * message body is preserved. This value can be overridden by an in message header named:
     * CamelInfinispanOperationResultHeader
     */
    public void setResultHeader(String resultHeader) {
        this.resultHeader = resultHeader;
    }

    public BiFunction getRemappingFunction() {
        return remappingFunction;
    }

    /**
     * Set a specific remappingFunction to use in a compute operation
     */
    public void setRemappingFunction(BiFunction remappingFunction) {
        this.remappingFunction = remappingFunction;
    }

    public Object getKey() {
        return key;
    }

    /**
     * Set a specific key for producer operations
     */
    public void setKey(Object key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    /**
     * Set a specific value for producer operations
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public Object getOldValue() {
        return oldValue;
    }

    /**
     * Set a specific old value for some producer operations
     */
    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Set a specific default value for some producer operations
     */
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public InfinispanQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Specifies the query builder.
     */
    public void setQueryBuilder(InfinispanQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public boolean hasQueryBuilder() {
        return queryBuilder != null;
    }

}
