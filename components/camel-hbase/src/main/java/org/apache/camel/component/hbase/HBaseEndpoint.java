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
package org.apache.camel.component.hbase;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hbase.mapping.CellMappingStrategyFactory;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.security.UserGroupInformation;

/**
 * For reading/writing from/to an HBase store (Hadoop database).
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "hbase", title = "HBase", syntax = "hbase:tableName", label = "hadoop")
public class HBaseEndpoint extends DefaultEndpoint {

    @UriPath(description = "The name of the table") @Metadata(required = true)
    private final String tableName;
    private transient TableName tableNameObj;
    @UriParam(label = "producer", defaultValue = "100")
    private int maxResults = 100;
    @UriParam
    private List<Filter> filters;
    @UriParam(label = "consumer", enums = "CamelHBasePut,CamelHBaseGet,CamelHBaseScan,CamelHBaseDelete")
    private String operation;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean remove = true;
    @UriParam(enums = "header,body")
    private String mappingStrategyName;
    @UriParam
    private String mappingStrategyClassName;
    @UriParam
    private CellMappingStrategyFactory cellMappingStrategyFactory = new CellMappingStrategyFactory();
    @UriParam(label = "consumer")
    private HBaseRemoveHandler removeHandler = new HBaseDeleteHandler();
    @UriParam
    private HBaseRow rowModel;
    @UriParam(label = "consumer")
    private int maxMessagesPerPoll;
    @UriParam
    private UserGroupInformation userGroupInformation;
    @UriParam(prefix = "row.", multiValue = true)
    private Map<String, Object> rowMapping;

    public HBaseEndpoint(String uri, HBaseComponent component, String tableName) {
        super(uri, component);
        this.tableName = tableName;
        if (this.tableName == null) {
            throw new IllegalArgumentException("Table name can not be null");
        }
        tableNameObj = TableName.valueOf(tableName);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HBaseProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HBaseConsumer consumer = new HBaseConsumer(this, processor);
        configureConsumer(consumer);
        consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return consumer;
    }

    @Override
    public HBaseComponent getComponent() {
        return (HBaseComponent) super.getComponent();
    }

    public int getMaxResults() {
        return maxResults;
    }

    /**
     * The maximum number of rows to scan.
     */
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    /**
     * A list of filters to use.
     */
    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * The HBase operation to perform
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public CellMappingStrategyFactory getCellMappingStrategyFactory() {
        return cellMappingStrategyFactory;
    }

    /**
     * To use a custom CellMappingStrategyFactory that is responsible for mapping cells.
     */
    public void setCellMappingStrategyFactory(CellMappingStrategyFactory cellMappingStrategyFactory) {
        this.cellMappingStrategyFactory = cellMappingStrategyFactory;
    }

    public String getMappingStrategyName() {
        return mappingStrategyName;
    }

    /**
     * The strategy to use for mapping Camel messages to HBase columns. Supported values: header, or body.
     */
    public void setMappingStrategyName(String mappingStrategyName) {
        this.mappingStrategyName = mappingStrategyName;
    }

    public String getMappingStrategyClassName() {
        return mappingStrategyClassName;
    }

    /**
     * The class name of a custom mapping strategy implementation.
     */
    public void setMappingStrategyClassName(String mappingStrategyClassName) {
        this.mappingStrategyClassName = mappingStrategyClassName;
    }

    public HBaseRow getRowModel() {
        return rowModel;
    }

    /**
     * An instance of org.apache.camel.component.hbase.model.HBaseRow which describes how each row should be modeled
     */
    public void setRowModel(HBaseRow rowModel) {
        this.rowModel = rowModel;
    }

    public boolean isRemove() {
        return remove;
    }

    /**
     * If the option is true, Camel HBase Consumer will remove the rows which it processes.
     */
    public void setRemove(boolean remove) {
        this.remove = remove;
    }

    public HBaseRemoveHandler getRemoveHandler() {
        return removeHandler;
    }

    /**
     * To use a custom HBaseRemoveHandler that is executed when a row is to be removed.
     */
    public void setRemoveHandler(HBaseRemoveHandler removeHandler) {
        this.removeHandler = removeHandler;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as unlimited.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public UserGroupInformation getUserGroupInformation() {
        return userGroupInformation;
    }

    /**
     * Defines privileges to communicate with HBase such as using kerberos.
     */
    public void setUserGroupInformation(UserGroupInformation userGroupInformation) {
        this.userGroupInformation = userGroupInformation;
    }

    public Map<String, Object> getRowMapping() {
        return rowMapping;
    }

    /**
     * To map the key/values from the Map to a {@link HBaseRow}.
     * <p/>
     * The following keys is supported:
     * <ul>
     *     <li>rowId - The id of the row. This has limited use as the row usually changes per Exchange.</li>
     *     <li>rowType - The type to covert row id to. Supported operations: CamelHBaseScan.</li>
     *     <li>family - The column family. Supports a number suffix for referring to more than one columns.</li>
     *     <li>qualifier - The column qualifier. Supports a number suffix for referring to more than one columns.</li>
     *     <li>value - The value. Supports a number suffix for referring to more than one columns</li>
     *     <li>valueType - The value type. Supports a number suffix for referring to more than one columns. Supported operations: CamelHBaseGet, and CamelHBaseScan.</li>
     * </ul>
     */
    public void setRowMapping(Map<String, Object> rowMapping) {
        this.rowMapping = rowMapping;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (rowModel == null && rowMapping != null) {
            rowModel = createRowModel(rowMapping);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    /**
     * Gets connection to the table (secured or not, depends on the object initialization)
     * please remember to close the table after use
     * @return table, remember to close!
     */
    public Table getTable() throws IOException {
        if (userGroupInformation != null) {
            return userGroupInformation.doAs(new PrivilegedAction<Table>() {
                @Override
                public Table run() {
                    try {
                        return getComponent().getConnection().getTable(tableNameObj);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } else {
            return getComponent().getConnection().getTable(tableNameObj);
        }
    }

    /**
     * Creates an {@link HBaseRow} model from the specified endpoint parameters.
     */
    private HBaseRow createRowModel(Map<String, Object> parameters) {
        HBaseRow rowModel = new HBaseRow();
        if (parameters.containsKey(HBaseAttribute.HBASE_ROW_TYPE.asOption())) {
            String rowType = String.valueOf(parameters.remove(HBaseAttribute.HBASE_ROW_TYPE.asOption()));
            if (rowType != null && !rowType.isEmpty()) {
                rowModel.setRowType(getCamelContext().getClassResolver().resolveClass(rowType));
            }
        }
        for (int i = 1; parameters.get(HBaseAttribute.HBASE_FAMILY.asOption(i)) != null
                && parameters.get(HBaseAttribute.HBASE_QUALIFIER.asOption(i)) != null; i++) {
            HBaseCell cellModel = new HBaseCell();
            cellModel.setFamily(String.valueOf(parameters.remove(HBaseAttribute.HBASE_FAMILY.asOption(i))));
            cellModel.setQualifier(String.valueOf(parameters.remove(HBaseAttribute.HBASE_QUALIFIER.asOption(i))));
            cellModel.setValue(String.valueOf(parameters.remove(HBaseAttribute.HBASE_VALUE.asOption(i))));
            if (parameters.containsKey(HBaseAttribute.HBASE_VALUE_TYPE.asOption(i))) {
                String valueType = String.valueOf(parameters.remove(HBaseAttribute.HBASE_VALUE_TYPE.asOption(i)));
                if (valueType != null && !valueType.isEmpty()) {
                    cellModel.setValueType(getCamelContext().getClassResolver().resolveClass(valueType));
                }
            }
            rowModel.getCells().add(cellModel);
        }
        return rowModel;
    }

}
