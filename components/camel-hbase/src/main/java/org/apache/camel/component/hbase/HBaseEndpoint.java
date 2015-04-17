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
package org.apache.camel.component.hbase;

import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hbase.mapping.CellMappingStrategyFactory;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.filter.Filter;

/**
 * Represents an HBase endpoint.
 */
@UriEndpoint(scheme = "hbase", title = "HBase", syntax = "hbase:tableName", consumerClass = HBaseConsumer.class, label = "hadoop")
public class HBaseEndpoint extends DefaultEndpoint {

    private Configuration configuration;
    private final HTablePool tablePool;
    private HBaseAdmin admin;

    @UriPath @Metadata(required = "true")
    private final String tableName;
    //Operation properties.
    @UriParam(defaultValue = "100")
    private int maxResults = 100;
    @UriParam
    private List<Filter> filters;
    @UriParam
    private String operation;
    @UriParam(defaultValue = "true")
    private boolean remove = true;
    @UriParam
    private String mappingStrategyName;
    @UriParam
    private String mappingStrategyClassName;
    @UriParam
    private CellMappingStrategyFactory cellMappingStrategyFactory = new CellMappingStrategyFactory();
    @UriParam
    private HBaseRemoveHandler removeHandler = new HBaseDeleteHandler();
    @UriParam
    private HBaseRow rowModel;
    @UriParam
    private int maxMessagesPerPoll;

    public HBaseEndpoint(String uri, HBaseComponent component, HTablePool tablePool, String tableName) {
        super(uri, component);
        this.tableName = tableName;
        this.tablePool = tablePool;
        if (this.tableName == null) {
            throw new IllegalArgumentException("Table name can not be null");
        }
    }

    public Producer createProducer() throws Exception {
        return new HBaseProducer(this, tablePool, tableName);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        HBaseConsumer consumer =  new HBaseConsumer(this, processor, tablePool, tableName);
        configureConsumer(consumer);
        consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return consumer;
    }

    public boolean isSingleton() {
        return true;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public HBaseAdmin getAdmin() {
        return admin;
    }

    public void setAdmin(HBaseAdmin admin) {
        this.admin = admin;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public CellMappingStrategyFactory getCellMappingStrategyFactory() {
        return cellMappingStrategyFactory;
    }

    public void setCellMappingStrategyFactory(CellMappingStrategyFactory cellMappingStrategyFactory) {
        this.cellMappingStrategyFactory = cellMappingStrategyFactory;
    }

    public String getMappingStrategyName() {
        return mappingStrategyName;
    }

    public void setMappingStrategyName(String mappingStrategyName) {
        this.mappingStrategyName = mappingStrategyName;
    }

    public String getMappingStrategyClassName() {
        return mappingStrategyClassName;
    }

    public void setMappingStrategyClassName(String mappingStrategyClassName) {
        this.mappingStrategyClassName = mappingStrategyClassName;
    }

    public HBaseRow getRowModel() {
        return rowModel;
    }

    public void setRowModel(HBaseRow rowModel) {
        this.rowModel = rowModel;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }

    public HBaseRemoveHandler getRemoveHandler() {
        return removeHandler;
    }

    public void setRemoveHandler(HBaseRemoveHandler removeHandler) {
        this.removeHandler = removeHandler;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
}
