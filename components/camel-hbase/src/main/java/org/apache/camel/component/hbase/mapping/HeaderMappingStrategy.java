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
package org.apache.camel.component.hbase.mapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.hbase.HBaseAttribute;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseData;
import org.apache.camel.component.hbase.model.HBaseRow;

/**
 * A default {@link CellMappingStrategy} implementation.
 * It distinguishes between multiple cell, by reading headers with index suffix.
 * <p/>
 * In case of multiple headers:
 * <ul>
 * <li>First header is expected to have no suffix</li>
 * <li>Suffixes start from number 2</li>
 * <li>Suffixes need to be sequential</li>
 * </ul>
 */
public class HeaderMappingStrategy implements CellMappingStrategy {

    /**
     * Resolves the cell that the {@link Exchange} refers to.
     */
    private HBaseRow resolveRow(Message message, int index) {
        HBaseRow hRow = new HBaseRow();
        HBaseCell hCell = new HBaseCell();

        if (message != null) {
            Object id =  message.getHeader(HBaseAttribute.HBASE_ROW_ID.asHeader(index));
            String rowClassName = message.getHeader(HBaseAttribute.HBASE_ROW_TYPE.asHeader(index), String.class);
            Class<?> rowClass = rowClassName == null || rowClassName.isEmpty() ? String.class : message.getExchange().getContext().getClassResolver().resolveClass(rowClassName);
            String columnFamily = (String) message.getHeader(HBaseAttribute.HBASE_FAMILY.asHeader(index));
            String columnName = (String) message.getHeader(HBaseAttribute.HBASE_QUALIFIER.asHeader(index));
            Object value =  message.getHeader(HBaseAttribute.HBASE_VALUE.asHeader(index));

            String valueClassName = message.getHeader(HBaseAttribute.HBASE_VALUE_TYPE.asHeader(index), String.class);
            Class<?> valueClass = valueClassName == null || valueClassName.isEmpty() ? String.class : message.getExchange().getContext().getClassResolver().resolveClass(valueClassName);

            //Id can be accepted as null when using get, scan etc.
            if (id == null && columnFamily == null && columnName == null) {
                return null;
            }

            hRow.setId(id);
            hRow.setRowType(rowClass);
            if (columnFamily != null && columnName != null) {
                hCell.setQualifier(columnName);
                hCell.setFamily(columnFamily);
                hCell.setValue(value);
                // String is the default value type
                hCell.setValueType((valueClass != null) ? valueClass : String.class);
                hRow.getCells().add(hCell);
            }
        }
        return hRow;
    }

    /**
     * Resolves the cells that the {@link org.apache.camel.Exchange} refers to.
     */
    @Override
    public HBaseData resolveModel(Message message) {
        int index = 1;
        HBaseData data = new HBaseData();
        //We use a LinkedHashMap to preserve the order.
        Map<Object, HBaseRow> rows = new LinkedHashMap<>();
        HBaseRow hRow = new HBaseRow();
        while (hRow != null) {
            hRow = resolveRow(message, index++);
            if (hRow != null) {
                if (rows.containsKey(hRow.getId())) {
                    rows.get(hRow.getId()).getCells().addAll(hRow.getCells());
                } else {
                    rows.put(hRow.getId(), hRow);
                }
            }
        }
        for (Map.Entry<Object, HBaseRow> rowEntry : rows.entrySet()) {
            data.getRows().add(rowEntry.getValue());
        }
        return data;
    }

    /**
     * Applies the cells to the {@link org.apache.camel.Exchange}.
     */
    @Override
    public void applyGetResults(Message message, HBaseData data) {
        message.setHeaders(message.getExchange().getIn().getHeaders());
        int index = 1;
        if (data == null || data.getRows() == null) {
            return;
        }

        for (HBaseRow hRow : data.getRows()) {
            if (hRow.getId() != null) {
                Set<HBaseCell> cells = hRow.getCells();
                for (HBaseCell cell : cells) {
                    message.setHeader(HBaseAttribute.HBASE_VALUE.asHeader(index++), getValueForColumn(cells, cell.getFamily(), cell.getQualifier()));
                }
            }
        }
    }


    /**
     * Applies the cells to the {@link org.apache.camel.Exchange}.
     */
    @Override
    public void applyScanResults(Message message, HBaseData data) {
        message.setHeaders(message.getExchange().getIn().getHeaders());
        int index = 1;
        if (data == null || data.getRows() == null) {
            return;
        }

        for (HBaseRow hRow : data.getRows()) {
            Set<HBaseCell> cells = hRow.getCells();
            for (HBaseCell cell : cells) {
                message.setHeader(HBaseAttribute.HBASE_ROW_ID.asHeader(index), hRow.getId());
                message.setHeader(HBaseAttribute.HBASE_FAMILY.asHeader(index), cell.getFamily());
                message.setHeader(HBaseAttribute.HBASE_QUALIFIER.asHeader(index), cell.getQualifier());
                message.setHeader(HBaseAttribute.HBASE_VALUE.asHeader(index), cell.getValue());
            }
            index++;
        }
    }

    /**
     * Searches a list of cells and returns the value, if family/column matches with the specified.
     */
    private Object getValueForColumn(Set<HBaseCell> cells, String family, String qualifier) {
        if (cells != null) {
            for (HBaseCell cell : cells) {
                if (cell.getQualifier().equals(qualifier) && cell.getFamily().equals(family)) {
                    return cell.getValue();
                }
            }
        }
        return null;
    }
}
