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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTablePool;

/**
 * Represents the component that manages {@link HBaseEndpoint}.
 */
public class HBaseComponent extends UriEndpointComponent {

    private Configuration configuration;
    private HTablePool tablePool;
    private int poolMaxSize = 10;

    public HBaseComponent() {
        super(HBaseEndpoint.class);
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
        }
        tablePool = new HTablePool(configuration, poolMaxSize);
    }

    @Override
    protected void doStop() throws Exception {
        if (tablePool != null) {
            tablePool.close();
        }
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String tableName = remaining;

        HBaseEndpoint endpoint = new HBaseEndpoint(uri, this, tablePool, tableName);
        HBaseRow parameterRowModel = createRowModel(parameters);
        setProperties(endpoint, parameters);
        if (endpoint.getRowModel() == null) {
            endpoint.setRowModel(parameterRowModel);
        }
        return endpoint;
    }

    /**
     * Creates an {@link HBaseRow} model from the specified endpoint parameters.
     */
    public HBaseRow createRowModel(Map<String, Object> parameters) {
        HBaseRow rowModel = new HBaseRow();
        if (parameters.containsKey(HbaseAttribute.HBASE_ROW_TYPE.asOption())) {
            String rowType = String.valueOf(parameters.remove(HbaseAttribute.HBASE_ROW_TYPE.asOption()));
            if (rowType != null && !rowType.isEmpty()) {
                rowModel.setRowType(getCamelContext().getClassResolver().resolveClass(rowType));
            }
        }
        for (int i = 1; parameters.get(HbaseAttribute.HBASE_FAMILY.asOption(i)) != null
                && parameters.get(HbaseAttribute.HBASE_QUALIFIER.asOption(i)) != null; i++) {
            HBaseCell cellModel = new HBaseCell();
            cellModel.setFamily(String.valueOf(parameters.remove(HbaseAttribute.HBASE_FAMILY.asOption(i))));
            cellModel.setQualifier(String.valueOf(parameters.remove(HbaseAttribute.HBASE_QUALIFIER.asOption(i))));
            cellModel.setValue(String.valueOf(parameters.remove(HbaseAttribute.HBASE_VALUE.asOption(i))));
            if (parameters.containsKey(HbaseAttribute.HBASE_VALUE_TYPE.asOption(i))) {
                String valueType = String.valueOf(parameters.remove(HbaseAttribute.HBASE_VALUE_TYPE.asOption(i)));
                if (valueType != null && !valueType.isEmpty()) {
                    rowModel.setRowType(getCamelContext().getClassResolver().resolveClass(valueType));
                }
            }
            rowModel.getCells().add(cellModel);
        }
        return rowModel;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public int getPoolMaxSize() {
        return poolMaxSize;
    }

    /**
     * Maximum number of references to keep for each table in the HTable pool.
     * The default value is 10.
     */
    public void setPoolMaxSize(int poolMaxSize) {
        this.poolMaxSize = poolMaxSize;
    }
}
