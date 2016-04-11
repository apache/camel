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

    @UriPath @Metadata(required = "true")
    private String name;

    /**
     * key type
     */
    @UriParam
    private String keyClass;

    /**
     * configuration
     */
    @UriParam
    private Configuration hadoopConfiguration;

    /**
     * value type
     */
    @UriParam
    private String valueClass;

    /**
     *  dataStore type
     */
    @UriParam
    private String dataStoreClass;

    /** Consumer only properties! */

    /**
     *  Gora Query Start Time attribute
     */
    @UriParam
    private long startTime;

    /**
     * Gora Query End Time attribute
     */
    @UriParam
    private long endTime;

    /**
     * Gora Query Time Range From attribute
     */
    @UriParam
    private long timeRangeFrom;

    /**
     * Gora Query Key Range To attribute
     */
    @UriParam
    private long timeRangeTo;

    /**
     * Gora Query Limit attribute
     */
    @UriParam
    private long limit;

    /**
     * Gora Query Timestamp attribute
     */
    @UriParam
    private long timestamp;

    /**
     * Gora Query Start Key attribute
     */
    @UriParam
    private Object startKey;

    /**
     * Gora Query End Key attribute
     */
    @UriParam
    private Object endKey;

    /**
     * Gora Query Key Range From attribute
     */
    @UriParam
    private Object keyRangeFrom;

    /**
     * Gora Query Key Range To attribute
     */
    @UriParam
    private Object keyRangeTo;

    /**
     * Gora Query Fields attribute
     */
    @UriParam
    private Strings fields;

    /**
     * Concurrent Consumers
     *
     * <b>NOTE:<b/> used only by consumer
     */
    @UriParam(defaultValue = "1")
    private int concurrentConsumers = 1;

    /**
     * Flush on every operation
     *
     * <b>NOTE:<b/> used only by producer
     */
    @UriParam(defaultValue = "true")
    private boolean flushOnEveryOperation = true;

    /**
     * Default Constructor
     */
    public GoraConfiguration() {
        this.hadoopConfiguration = new Configuration();
    }

    /**
     * Get type of the key (i.e clients)
     *
     * @return key class
     */
    public String getKeyClass() {
        return keyClass;
    }

    /**
     * Set type class of the key
     */
    public void setKeyClass(final String keyClass) {
        if (isNullOrEmpty(keyClass)) {
            throw new IllegalArgumentException("Key class could not be null or empty!");
        }

        this.keyClass = keyClass;
    }

    /**
     * Get type of the value
     */
    public String getValueClass() {
        return valueClass;
    }

    /**
     * Set type of the value
     */
    public void setValueClass(final String valueClass) {
        if (isNullOrEmpty(valueClass)) {
            throw new IllegalArgumentException("Value class  could not be null or empty!");
        }
        this.valueClass = valueClass;
    }

    /**
     * Get type of the dataStore
     */
    public String getDataStoreClass() {
        return dataStoreClass;
    }

    /**
     * Set type of the dataStore
     */
    public void setDataStoreClass(String dataStoreClass) {
        if (isNullOrEmpty(dataStoreClass)) {
            throw new IllegalArgumentException("DataStore class could not be null or empty!");
        }
        this.dataStoreClass = dataStoreClass;
    }

    /**
     * Get Hadoop Configuration
     */
    public Configuration getHadoopConfiguration() {
        return hadoopConfiguration;
    }

    /**
     * Get Start Time
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Set Start Time
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Get End Time
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Set End Time
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    /**
     * Get Time Range From
     */
    public long getTimeRangeFrom() {
        return timeRangeFrom;
    }

    /**
     * Set Time Range From
     */
    public void setTimeRangeFrom(long timeRangeFrom) {
        this.timeRangeFrom = timeRangeFrom;
    }

    /**
     * Get Time Range To
     */
    public long getTimeRangeTo() {
        return timeRangeTo;
    }

    /**
     * Set Time Range To
     */
    public void setTimeRangeTo(long timeRangeTo) {
        this.timeRangeTo = timeRangeTo;
    }

    /**
     * Get Limit
     */
    public long getLimit() {
        return limit;
    }

    /**
     * Set Limit
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }

    /**
     * Get Timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Set Timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Get Start Key
     */
    public Object getStartKey() {
        return startKey;
    }

    /**
     * Set Start Key
     */
    public void setStartKey(Object startKey) {
        this.startKey = startKey;
    }

    /**
     * Get End Key
     */
    public Object getEndKey() {
        return endKey;
    }

    /**
     * Set End Key
     */
    public void setEndKey(Object endKey) {
        this.endKey = endKey;
    }

    /**
     * Get Key Range From
     */
    public Object getKeyRangeFrom() {
        return keyRangeFrom;
    }

    /**
     * Set Key Range From
     */
    public void setKeyRangeFrom(Object keyRangeFrom) {
        this.keyRangeFrom = keyRangeFrom;
    }

    /**
     * Get Key Range To
     */
    public Object getKeyRangeTo() {
        return keyRangeTo;
    }

    /**
     * Set Key Range To
     */
    public void setKeyRangeTo(Object keyRangeTo) {
        this.keyRangeTo = keyRangeTo;
    }

    /**
     * Get Fields
     */
    public Strings getFields() {
        return fields;
    }

    /**
     * Set Fields
     */
    public void setFields(Strings fields) {
        this.fields = fields;
    }

    /**
     * Get Concurrent Consumers
     */
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * Set Concurrent Consumers
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    /**
     * Get flush on every operation
     */
    public boolean isFlushOnEveryOperation() {
        return flushOnEveryOperation;
    }

    /**
     * Set flush on every operation
     */
    public void setFlushOnEveryOperation(boolean flushOnEveryOperation) {
        this.flushOnEveryOperation = flushOnEveryOperation;
    }

    /**
     * Set Hadoop Configuration
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
