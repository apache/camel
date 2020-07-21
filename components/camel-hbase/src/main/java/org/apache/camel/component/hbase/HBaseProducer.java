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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.hbase.filters.ModelAwareFilter;
import org.apache.camel.component.hbase.mapping.CellMappingStrategy;
import org.apache.camel.component.hbase.mapping.CellMappingStrategyFactory;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseData;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * The HBase producer.
 */
public class HBaseProducer extends DefaultProducer {

    private HBaseEndpoint endpoint;
    private HBaseRow rowModel;

    public HBaseProducer(HBaseEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.rowModel = endpoint.getRowModel();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try (Table table = endpoint.getTable()) {
            updateHeaders(exchange);
            String operation = (String) exchange.getIn().getHeader(HBaseConstants.OPERATION);

            Integer maxScanResult = exchange.getIn().getHeader(HBaseConstants.HBASE_MAX_SCAN_RESULTS, Integer.class);
            String fromRowId = (String) exchange.getIn().getHeader(HBaseConstants.FROM_ROW);
            String stopRowId = (String) exchange.getIn().getHeader(HBaseConstants.STOP_ROW);
            CellMappingStrategy mappingStrategy = endpoint.getCellMappingStrategyFactory().getStrategy(exchange.getIn());

            HBaseData data = mappingStrategy.resolveModel(exchange.getIn());

            List<Put> putOperations = new LinkedList<>();
            List<Delete> deleteOperations = new LinkedList<>();
            List<HBaseRow> getOperationResult = new LinkedList<>();
            List<HBaseRow> scanOperationResult = new LinkedList<>();

            for (HBaseRow hRow : data.getRows()) {
                hRow.apply(rowModel);
                if (HBaseConstants.PUT.equals(operation)) {
                    putOperations.add(createPut(hRow));
                } else if (HBaseConstants.GET.equals(operation)) {
                    HBaseRow getResultRow = getCells(table, hRow);
                    getOperationResult.add(getResultRow);
                } else if (HBaseConstants.DELETE.equals(operation)) {
                    deleteOperations.add(createDeleteRow(hRow));
                } else if (HBaseConstants.SCAN.equals(operation)) {
                    scanOperationResult = scanCells(table, hRow, fromRowId, stopRowId, maxScanResult, endpoint.getFilters());
                }
            }

            //Check if we have something to add.
            if (!putOperations.isEmpty()) {
                table.put(putOperations);
            } else if (!deleteOperations.isEmpty()) {
                table.delete(deleteOperations);
            } else if (!getOperationResult.isEmpty()) {
                mappingStrategy.applyGetResults(exchange.getOut(), new HBaseData(getOperationResult));
            } else if (!scanOperationResult.isEmpty()) {
                mappingStrategy.applyScanResults(exchange.getOut(), new HBaseData(scanOperationResult));
            }
        }
    }

    /**
     * Creates an HBase {@link Put} on a specific row, using a collection of values (family/column/value pairs).
     */
    private Put createPut(HBaseRow hRow) throws Exception {
        ObjectHelper.notNull(hRow, "HBase row");
        ObjectHelper.notNull(hRow.getId(), "HBase row id");
        ObjectHelper.notNull(hRow.getCells(), "HBase cells");

        Put put = new Put(endpoint.getCamelContext().getTypeConverter().convertTo(byte[].class, hRow.getId()));
        Set<HBaseCell> cells = hRow.getCells();
        for (HBaseCell cell : cells) {
            String family = cell.getFamily();
            String column = cell.getQualifier();
            Object value = cell.getValue();

            ObjectHelper.notNull(family, "HBase column family", cell);
            ObjectHelper.notNull(column, "HBase column", cell);
            put.addColumn(
                    HBaseHelper.getHBaseFieldAsBytes(family),
                    HBaseHelper.getHBaseFieldAsBytes(column),
                    endpoint.getCamelContext().getTypeConverter().convertTo(byte[].class, value)
            );
        }
        return put;
    }

    /**
     * Performs an HBase {@link Get} on a specific row, using a collection of values (family/column/value pairs).
     * The result is <p>the most recent entry</p> for each column.
     */
    private HBaseRow getCells(Table table, HBaseRow hRow) throws Exception {
        HBaseRow resultRow = new HBaseRow();
        ObjectHelper.notNull(hRow, "HBase row");
        ObjectHelper.notNull(hRow.getId(), "HBase row id");
        ObjectHelper.notNull(hRow.getCells(), "HBase cells");

        resultRow.setId(hRow.getId());
        Get get = new Get(endpoint.getCamelContext().getTypeConverter().convertTo(byte[].class, hRow.getId()));
        Set<HBaseCell> cellModels = hRow.getCells();
        for (HBaseCell cellModel : cellModels) {
            String family = cellModel.getFamily();
            String column = cellModel.getQualifier();

            ObjectHelper.notNull(family, "HBase column family", cellModel);
            ObjectHelper.notNull(column, "HBase column", cellModel);
            get.addColumn(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(column));
        }

        Result result = table.get(get);

        if (!result.isEmpty()) {
            resultRow.setTimestamp(result.rawCells()[0].getTimestamp());
        }

        for (HBaseCell cellModel : cellModels) {
            HBaseCell resultCell = new HBaseCell();
            String family = cellModel.getFamily();
            String column = cellModel.getQualifier();
            resultCell.setFamily(family);
            resultCell.setQualifier(column);

            List<Cell> kvs = result.getColumnCells(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(column));
            if (kvs != null && !kvs.isEmpty()) {
                //Return the most recent entry.
                resultCell.setValue(endpoint.getCamelContext().getTypeConverter().convertTo(cellModel.getValueType(), CellUtil.cloneValue(kvs.get(0))));
                resultCell.setTimestamp(kvs.get(0).getTimestamp());
            }
            resultRow.getCells().add(resultCell);
        }
        return resultRow;
    }

    /**
     * Creates an HBase {@link Delete} on a specific row, using a collection of values (family/column/value pairs).
     */
    private Delete createDeleteRow(HBaseRow hRow) throws Exception {
        ObjectHelper.notNull(hRow, "HBase row");
        ObjectHelper.notNull(hRow.getId(), "HBase row id");
        return new Delete(endpoint.getCamelContext().getTypeConverter().convertTo(byte[].class, hRow.getId()));
    }

    /**
     * Performs an HBase {@link Get} on a specific row, using a collection of values (family/column/value pairs).
     * The result is <p>the most recent entry</p> for each column.
     */
    private List<HBaseRow> scanCells(Table table, HBaseRow model, String start, String stop, Integer maxRowScan, List<Filter> filters)
            throws Exception {
        List<HBaseRow> rowSet = new LinkedList<>();

        HBaseRow startRow = new HBaseRow(model.getCells());
        startRow.setId(start);

        Scan scan = new Scan();
        if (start != null) {
            scan.withStartRow(Bytes.toBytes(start));
        }
        if (ObjectHelper.isNotEmpty(stop)) {
            scan.withStopRow(Bytes.toBytes(stop));
        }

        if (filters != null && !filters.isEmpty()) {
            for (int i = 0; i < filters.size(); i++) {
                ((ModelAwareFilter<?>) filters.get(i)).apply(endpoint.getCamelContext(), model);
                scan.setFilter(new FilterList(FilterList.Operator.MUST_PASS_ALL, ((ModelAwareFilter<?>) filters.get(i)).getFilteredList()));
            }
        }

        Set<HBaseCell> cellModels = model.getCells();
        for (HBaseCell cellModel : cellModels) {
            String family = cellModel.getFamily();
            String column = cellModel.getQualifier();

            if (ObjectHelper.isNotEmpty(family) && ObjectHelper.isNotEmpty(column)) {
                scan.addColumn(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(column));
            }
        }

        ResultScanner resultScanner = table.getScanner(scan);
        int count = 0;
        Result result = resultScanner.next();

        while (result != null && count < maxRowScan) {
            HBaseRow resultRow = new HBaseRow();
            resultRow.setId(endpoint.getCamelContext().getTypeConverter().convertTo(model.getRowType(), result.getRow()));

            resultRow.setTimestamp(result.rawCells()[0].getTimestamp());
            cellModels = model.getCells();
            for (HBaseCell modelCell : cellModels) {
                HBaseCell resultCell = new HBaseCell();
                String family = modelCell.getFamily();
                String column = modelCell.getQualifier();

                resultRow.setId(endpoint.getCamelContext().getTypeConverter().convertTo(
                        model.getRowType(),
                        result.getRow())
                );
                resultCell.setValue(endpoint.getCamelContext().getTypeConverter().convertTo(
                        modelCell.getValueType(),
                        result.getValue(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(column)))
                );

                resultCell.setFamily(modelCell.getFamily());
                resultCell.setQualifier(modelCell.getQualifier());

                Cell cell = result.getColumnLatestCell(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(column));
                if (cell != null) {
                    resultCell.setTimestamp(cell.getTimestamp());
                }
                resultRow.getCells().add(resultCell);
            }
            rowSet.add(resultRow);
            count++;
            result = resultScanner.next();
        }
        return rowSet;
    }

    /**
     * This methods fill possible gaps in the {@link Exchange} headers, with values passed from the Endpoint.
     */
    private void updateHeaders(Exchange exchange) {
        if (exchange != null && exchange.getIn() != null) {
            if (endpoint.getMaxResults() != 0 && exchange.getIn().getHeader(HBaseConstants.HBASE_MAX_SCAN_RESULTS) == null) {
                exchange.getIn().setHeader(HBaseConstants.HBASE_MAX_SCAN_RESULTS, endpoint.getMaxResults());
            }
            if (endpoint.getMappingStrategyName() != null && exchange.getIn().getHeader(CellMappingStrategyFactory.STRATEGY) == null) {
                exchange.getIn().setHeader(CellMappingStrategyFactory.STRATEGY, endpoint.getMappingStrategyName());
            }

            if (endpoint.getMappingStrategyName() != null
                    && exchange.getIn().getHeader(CellMappingStrategyFactory.STRATEGY_CLASS_NAME) == null) {
                exchange.getIn().setHeader(CellMappingStrategyFactory.STRATEGY_CLASS_NAME, endpoint.getMappingStrategyClassName());
            }

            if (endpoint.getOperation() != null && exchange.getIn().getHeader(HBaseConstants.OPERATION) == null) {
                exchange.getIn().setHeader(HBaseConstants.OPERATION, endpoint.getOperation());
            } else if (endpoint.getOperation() == null && exchange.getIn().getHeader(HBaseConstants.OPERATION) == null) {
                exchange.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.PUT);
            }
        }
    }
}
