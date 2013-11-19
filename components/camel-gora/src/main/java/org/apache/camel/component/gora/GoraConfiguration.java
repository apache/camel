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
import org.apache.hadoop.conf.Configuration;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;


/**
 * Gora Configuration.
 *
 * @author ipolyzos
 */
public class GoraConfiguration {

    /**
     * key type
     */
    private String keyClass;

    /**
     * configuration
     */
    private Configuration hadoopConfiguration;

    /**
     * value type
     */
    private String valueClass;

    /**
     *  dataStore type
     */
    private String dataStoreClass;

    /** Consumer only properties! */

    /**
     *  Gora Query Start Time attribute
     */
    private long startTime;

    /**
     * Gora Query End Time attribute
     */
    private long endTime;

    /**
     * Gora Query Time Range From attribute
     */
    private long timeRangeFrom;

    /**
     * Gora Query Key Range To attribute
     */
    private long timeRangeTo;

    /**
     * Gora Query Limit attribute
     */
    private long limit;

    /**
     * Gora Query Timestamp attribute
     */
    private long timestamp;

    /**
     * Gora Query Start Key attribute
     */
    private Object startKey;

    /**
     * Gora Query End Key attribute
     */
    private Object endKey;

    /**
     * Gora Query Key Range From attribute
     */
    private Object keyRangeFrom;

    /**
     * Gora Query Key Range To attribute
     */
    private Object keyRangeTo;


    /**
     * Gora Query Fields attribute
     */
    private Strings fields;

    /**
     * Concurrent Consumers
     *
     * <b>NOTE:<b/> used only by consumer
     */
    private int concurrentConsumers = 1;

    /**
     * Flush on every operation
     *
     * <b>NOTE:<b/> used only by producer
     */
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
     *
     * @param keyClass
     */
    public void setKeyClass(final String keyClass) {

        if (isNullOrEmpty(keyClass)){

            throw new IllegalArgumentException("Key class could not be null or empty!");
        }

        this.keyClass = keyClass;
    }

    /**
     * Get type of the value
     *
     * @return
     */
    public String getValueClass() {

        return valueClass;
    }

    /**
     * Set type of the value
     *
     * @param valueClass
     */
    public void setValueClass(final String valueClass) {

        if (isNullOrEmpty(valueClass)){

            throw new IllegalArgumentException("Value class  could not be null or empty!");
        }

        this.valueClass = valueClass;
    }

    /**
     * Get type of the dataStore
     *
     * @return  DataStore class
     */
    public String getDataStoreClass() {

        return dataStoreClass;
    }

    /**
     * Set type of the dataStore
     *
     * @param dataStoreClass
     */
    public void setDataStoreClass(String dataStoreClass) {


        if (isNullOrEmpty(dataStoreClass)){

            throw new IllegalArgumentException("DataStore class could not be null or empty!");
        }

        this.dataStoreClass = dataStoreClass;
    }

    /**
     * Get Hadoop Configuration
     *
     * @return
     */
    public Configuration getHadoopConfiguration() {

        return hadoopConfiguration;
    }

    /**
     * Get Start Time
     *
     * @return
     */
    public long getStartTime() {

        return startTime;
    }

    /**
     * Set Start Time
     *
     * @return
     */
    public void setStartTime(long startTime) {

        this.startTime = startTime;
    }

    /**
     * Get End Time
     *
     * @return
     */
    public long getEndTime() {

        return endTime;
    }

    /**
     * Set End Time
     *
     * @return
     */
    public void setEndTime(long endTime) {

        this.endTime = endTime;
    }

    /**
     * Get Time Range From
     *
     * @return
     */
    public long getTimeRangeFrom() {

        return timeRangeFrom;
    }

    /**
     * Set Time Range From
     *
     * @return
     */
    public void setTimeRangeFrom(long timeRangeFrom) {

        this.timeRangeFrom = timeRangeFrom;
    }

    /**
     * Get Time Range To
     *
     * @return
     */
    public long getTimeRangeTo() {

        return timeRangeTo;
    }

    /**
     * Set Time Range To
     *
     * @return
     */
    public void setTimeRangeTo(long timeRangeTo) {

        this.timeRangeTo = timeRangeTo;
    }

    /**
     * Get Limit
     *
     * @return
     */
    public long getLimit() {

        return limit;
    }

    /**
     * Set Limit
     *
     * @param limit
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }

    /**
     * Get Timestamp
     *
     * @return
     */
    public long getTimestamp() {

        return timestamp;
    }

    /**
     * Set Timestamp
     *
     * @param timestamp
     */
    public void setTimestamp(long timestamp) {

        this.timestamp = timestamp;
    }

    /**
     * Get Start Key
     *
     * @return
     */
    public Object getStartKey() {
        return startKey;
    }

    /**
     * Set Start Key
     *
     * @param startKey
     */
    public void setStartKey(Object startKey) {
        this.startKey = startKey;
    }

    /**
     * Get End Key
     *
     * @return
     */
    public Object getEndKey() {
        return endKey;
    }

    /**
     * Set End Key
     *
     * @param endKey
     */
    public void setEndKey(Object endKey) {
        this.endKey = endKey;
    }

    /**
     * Get Key Range From
     * @return
     */
    public Object getKeyRangeFrom() {
        return keyRangeFrom;
    }

    /**
     * Set Key Range From
     *
     * @param keyRangeFrom
     */
    public void setKeyRangeFrom(Object keyRangeFrom) {
        this.keyRangeFrom = keyRangeFrom;
    }

    /**
     * Get Key Range To
     * @return
     */
    public Object getKeyRangeTo() {
        return keyRangeTo;
    }

    /**
     * Set Key Range To
     *
     * @param keyRangeTo
     */
    public void setKeyRangeTo(Object keyRangeTo) {
        this.keyRangeTo = keyRangeTo;
    }

    /**
     * Get Fields
     *
     * @return
     */
    public Strings getFields() {

        return fields;
    }

    /**
     * Set Fields
     *
     * @param fields
     */
    public void setFields(Strings fields) {

        this.fields = fields;
    }

    /**
     * Get Concurrent Consumers
     * @return
     */
    public int getConcurrentConsumers() {

        return concurrentConsumers;
    }

    /**
     * Set Concurrent Consumers
     *
     * @param concurrentConsumers
     */
    public void setConcurrentConsumers(int concurrentConsumers) {

        this.concurrentConsumers = concurrentConsumers;
    }

    /**
     * Get flush on every operation
     *
     * @return
     */
    public boolean isFlushOnEveryOperation() {
        return flushOnEveryOperation;
    }

    /**
     * Set flush on every operation
     *
     * @param flushOnEveryOperation
     */
    public void setFlushOnEveryOperation(boolean flushOnEveryOperation) {
        this.flushOnEveryOperation = flushOnEveryOperation;
    }

    /**
     * Set Hadoop Configuration
     *
     * @param hadoopConfiguration
     */
    public void setHadoopConfiguration(Configuration hadoopConfiguration) {

        checkNotNull(hadoopConfiguration,"Hadoop Configuration could not be null!");
        this.hadoopConfiguration = hadoopConfiguration;
    }
}
