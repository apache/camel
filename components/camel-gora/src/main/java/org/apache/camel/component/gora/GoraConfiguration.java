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
package org.apache.camel.component.gora;

import com.google.common.base.Strings;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.hadoop.conf.Configuration;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Gora Configuration.
 */
@UriParams
public class GoraConfiguration {

    @UriPath @Metadata(required = true)
    private String name;
    @UriParam
    private String keyClass;
    @UriParam
    private String valueClass;
    @UriParam
    private String dataStoreClass;
    @UriParam(label = "advanced")
    private Configuration hadoopConfiguration;

    @UriParam(label = "consumer")
    private long startTime;
    @UriParam(label = "consumer")
    private long endTime;
    @UriParam(label = "consumer")
    private long timeRangeFrom;
    @UriParam(label = "consumer")
    private long timeRangeTo;
    @UriParam(label = "consumer")
    private long limit;
    @UriParam(label = "consumer")
    private long timestamp;
    @UriParam(label = "consumer")
    private Object startKey;
    @UriParam(label = "consumer")
    private Object endKey;
    @UriParam(label = "consumer")
    private Object keyRangeFrom;
    @UriParam(label = "consumer")
    private Object keyRangeTo;
    @UriParam(label = "consumer")
    private Strings fields;
    @UriParam(label = "consumer", defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean flushOnEveryOperation = true;

    public GoraConfiguration() {
        this.hadoopConfiguration = new Configuration();
    }

    public String getKeyClass() {
        return keyClass;
    }

    /**
     * The type class of the key
     */
    public void setKeyClass(final String keyClass) {
        if (isNullOrEmpty(keyClass)) {
            throw new IllegalArgumentException("Key class could not be null or empty!");
        }

        this.keyClass = keyClass;
    }

    public String getValueClass() {
        return valueClass;
    }

    /**
     * The type of the value
     */
    public void setValueClass(final String valueClass) {
        if (isNullOrEmpty(valueClass)) {
            throw new IllegalArgumentException("Value class  could not be null or empty!");
        }
        this.valueClass = valueClass;
    }

    public String getDataStoreClass() {
        return dataStoreClass;
    }

    /**
     * The type of the dataStore
     */
    public void setDataStoreClass(String dataStoreClass) {
        if (isNullOrEmpty(dataStoreClass)) {
            throw new IllegalArgumentException("DataStore class could not be null or empty!");
        }
        this.dataStoreClass = dataStoreClass;
    }

    public Configuration getHadoopConfiguration() {
        return hadoopConfiguration;
    }

    public long getStartTime() {
        return startTime;
    }

    /**
     * The Start Time
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    /**
     * The End Time
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getTimeRangeFrom() {
        return timeRangeFrom;
    }

    /**
     * The Time Range From
     */
    public void setTimeRangeFrom(long timeRangeFrom) {
        this.timeRangeFrom = timeRangeFrom;
    }

    public long getTimeRangeTo() {
        return timeRangeTo;
    }

    /**
     * The Time Range To
     */
    public void setTimeRangeTo(long timeRangeTo) {
        this.timeRangeTo = timeRangeTo;
    }

    public long getLimit() {
        return limit;
    }

    /**
     * The Limit
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * The Timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getStartKey() {
        return startKey;
    }

    /**
     * The Start Key
     */
    public void setStartKey(Object startKey) {
        this.startKey = startKey;
    }

    public Object getEndKey() {
        return endKey;
    }

    /**
     * The End Key
     */
    public void setEndKey(Object endKey) {
        this.endKey = endKey;
    }

    public Object getKeyRangeFrom() {
        return keyRangeFrom;
    }

    /**
     * The Key Range From
     */
    public void setKeyRangeFrom(Object keyRangeFrom) {
        this.keyRangeFrom = keyRangeFrom;
    }

    public Object getKeyRangeTo() {
        return keyRangeTo;
    }

    /**
     * The Key Range To
     */
    public void setKeyRangeTo(Object keyRangeTo) {
        this.keyRangeTo = keyRangeTo;
    }

    public Strings getFields() {
        return fields;
    }

    /**
     * The Fields
     */
    public void setFields(Strings fields) {
        this.fields = fields;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * Number of concurrent consumers
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public boolean isFlushOnEveryOperation() {
        return flushOnEveryOperation;
    }

    /**
     * Flush on every operation
     */
    public void setFlushOnEveryOperation(boolean flushOnEveryOperation) {
        this.flushOnEveryOperation = flushOnEveryOperation;
    }

    /**
     * Hadoop Configuration
     */
    public void setHadoopConfiguration(Configuration hadoopConfiguration) {
        checkNotNull(hadoopConfiguration, "Hadoop Configuration could not be null!");
        this.hadoopConfiguration = hadoopConfiguration;
    }

    public String getName() {
        return name;
    }

    /**
     * Instance name
     */
    public void setName(String name) {
        this.name = name;
    }
}
