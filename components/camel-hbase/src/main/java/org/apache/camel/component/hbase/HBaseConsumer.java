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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.hbase.mapping.CellMappingStrategy;
import org.apache.camel.component.hbase.mapping.CellMappingStrategyFactory;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseData;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The HBase consumer.
 */
public class HBaseConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseConsumer.class);

    private final HBaseEndpoint endpoint;
    private HBaseRow rowModel;

    public HBaseConsumer(HBaseEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.rowModel = endpoint.getRowModel();
    }

    @Override
    protected int poll() throws Exception {
        try (Table table = endpoint.getTable()) {
            shutdownRunningTask = null;
            pendingExchanges = 0;

            Queue<Exchange> queue = new LinkedList<>();

            Scan scan = new Scan();
            List<Filter> filters = new LinkedList<>();
            if (endpoint.getFilters() != null) {
                filters.addAll(endpoint.getFilters());
            }

            if (maxMessagesPerPoll > 0) {
                filters.add(new PageFilter(maxMessagesPerPoll));
            }

            if (!filters.isEmpty()) {
                Filter compoundFilter = new FilterList(filters);
                scan.setFilter(compoundFilter);
            }

            if (rowModel != null && rowModel.getCells() != null) {
                Set<HBaseCell> cellModels = rowModel.getCells();
                for (HBaseCell cellModel : cellModels) {
                    scan.addColumn(HBaseHelper.getHBaseFieldAsBytes(cellModel.getFamily()), HBaseHelper.getHBaseFieldAsBytes(cellModel.getQualifier()));
                }
            }

            ResultScanner scanner = table.getScanner(scan);
            int exchangeCount = 0;
            // The next three statements are used just to get a reference to the BodyCellMappingStrategy instance.
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(CellMappingStrategyFactory.STRATEGY, CellMappingStrategyFactory.BODY);
            CellMappingStrategy mappingStrategy = endpoint.getCellMappingStrategyFactory().getStrategy(exchange.getIn());
            for (Result result = scanner.next(); (exchangeCount < maxMessagesPerPoll || maxMessagesPerPoll <= 0) && result != null; result = scanner.next()) {
                HBaseData data = new HBaseData();
                HBaseRow resultRow = new HBaseRow();
                resultRow.apply(rowModel);
                byte[] row = result.getRow();
                resultRow.setId(endpoint.getCamelContext().getTypeConverter().convertTo(rowModel.getRowType(), row));

                List<Cell> cells = result.listCells();
                if (cells != null) {
                    Set<HBaseCell> cellModels = rowModel.getCells();
                    if (cellModels.size() > 0) {
                        for (HBaseCell modelCell : cellModels) {
                            HBaseCell resultCell = new HBaseCell();
                            String family = modelCell.getFamily();
                            String column = modelCell.getQualifier();
                            resultCell.setValue(endpoint.getCamelContext().getTypeConverter().convertTo(
                                modelCell.getValueType(),
                                result.getValue(HBaseHelper.getHBaseFieldAsBytes(family), HBaseHelper.getHBaseFieldAsBytes(column)))
                            );
                            resultCell.setFamily(modelCell.getFamily());
                            resultCell.setQualifier(modelCell.getQualifier());
                            resultRow.getCells().add(resultCell);
                        }
                    } else {
                        // just need to put every key value into the result Cells
                        for (Cell cell : cells) {
                            String qualifier = new String(CellUtil.cloneQualifier(cell));
                            String family = new String(CellUtil.cloneFamily(cell));
                            HBaseCell resultCell = new HBaseCell();
                            resultCell.setFamily(family);
                            resultCell.setQualifier(qualifier);
                            resultCell.setValue(endpoint.getCamelContext().getTypeConverter().convertTo(String.class, CellUtil.cloneValue(cell)));
                            resultRow.getCells().add(resultCell); 
                        }
                    }
               
                    data.getRows().add(resultRow);
                    exchange = endpoint.createExchange();
                    // Probably overkill but kept it here for consistency.
                    exchange.getIn().setHeader(CellMappingStrategyFactory.STRATEGY, CellMappingStrategyFactory.BODY);
                    mappingStrategy.applyScanResults(exchange.getIn(), data);
                    //Make sure that there is a header containing the marked row ids, so that they can be deleted.
                    exchange.getIn().setHeader(HBaseAttribute.HBASE_MARKED_ROW_ID.asHeader(), result.getRow());
                    queue.add(exchange);
                    exchangeCount++;
                }
            }
            scanner.close();
            return queue.isEmpty() ? 0 : processBatch(CastUtils.cast(queue));
        }
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            LOG.debug("Limiting to maximum messages to poll {} as there were {} messages in this poll.", maxMessagesPerPoll, total);
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            LOG.trace("Processing exchange [{}]...", exchange);
            getProcessor().process(exchange);
            if (exchange.getException() != null) {
                // if we failed then throw exception
                throw exchange.getException();
            }

            if (endpoint.isRemove()) {
                remove((byte[]) exchange.getIn().getHeader(HBaseAttribute.HBASE_MARKED_ROW_ID.asHeader()));
            }
        }

        return total;
    }

    /**
     * Delegates to the {@link HBaseRemoveHandler}.
     */
    private void remove(byte[] row) throws IOException {
        try (Table table = endpoint.getTable()) {
            endpoint.getRemoveHandler().remove(table, row);
        }
    }

    public HBaseRow getRowModel() {
        return rowModel;
    }

    public void setRowModel(HBaseRow rowModel) {
        this.rowModel = rowModel;
    }
}
