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
package org.apache.camel.component.flatpack;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.Record;
import net.sf.flatpack.ordering.OrderBy;

/**
 * The {@link DataSetList} wraps the {@link DataSet} as a Java {@link List} type so the data can easily be iterated.
 * You can access the {@link DataSet} API from this {@link DataSetList} as it implements {@link DataSet}.
 */
public class DataSetList extends AbstractList<Map<String, Object>> implements DataSet {
    private final DataSet dataSet;

    public DataSetList(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public Map<String, Object> get(int index) {
        dataSet.absolute(index);
        return FlatpackConverter.toMap(dataSet);
    }

    public int size() {
        return dataSet.getRowCount();
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        dataSet.goTop();
        return new Iterator<Map<String, Object>>() {
            private boolean hasNext = dataSet.next();

            public boolean hasNext() {
                return hasNext;
            }

            public Map<String, Object> next() {
                // because of a limitation in split() we need to create an object for the current position
                // otherwise strangeness occurs when the same object is used to represent each row
                Map<String, Object> result = FlatpackConverter.toMap(dataSet);
                hasNext = dataSet.next();
                return result;
            }

            public void remove() {
                throw new UnsupportedOperationException("remove() not supported");
            }
        };
    }

    // delegate methods
    // --------------------------------------------------------------

    @Override
    public void goTop() {
        dataSet.goTop();
    }

    @Override
    public void goBottom() {
        dataSet.goBottom();
    }

    @Override
    public boolean previous() {
        return dataSet.previous();
    }

    @Override
    public List getErrors() {
        return dataSet.getErrors();
    }

    @Override
    public void remove() {
        dataSet.remove();
    }

    @Override
    public int getIndex() {
        return dataSet.getIndex();
    }

    @Override
    public int getRowCount() {
        return dataSet.getRowCount();
    }

    @Override
    public int getErrorCount() {
        return dataSet.getErrorCount();
    }

    @Override
    public boolean isAnError(int lineNo) {
        return dataSet.isAnError(lineNo);
    }

    @Override
    public void orderRows(OrderBy ob) {
        dataSet.orderRows(ob);
    }

    @Override
    public void setLowerCase() {
        dataSet.setLowerCase();
    }

    @Override
    public void setUpperCase() {
        dataSet.setUpperCase();
    }

    @Override
    public void absolute(int localPointer) {
        dataSet.absolute(localPointer);
    }

    @Override
    public void setStrictNumericParse(boolean strictNumericParse) {
        dataSet.setStrictNumericParse(strictNumericParse);
    }

    @Override
    public void setPZConvertProps(Properties props) {
        dataSet.setPZConvertProps(props);
    }

    @Override
    public void setValue(String column, String value) {
        dataSet.setValue(column, value);
    }

    @Override
    public void clearRows() {
        dataSet.clearRows();
    }

    @Override
    public void clearErrors() {
        dataSet.clearErrors();
    }

    @Override
    public void clearAll() {
        dataSet.clearAll();
    }

    @Override
    public String getString(String column) {
        return dataSet.getString(column);
    }

    @Override
    public double getDouble(String column) {
        return dataSet.getDouble(column);
    }

    @Override
    public BigDecimal getBigDecimal(String column) {
        return dataSet.getBigDecimal(column);
    }

    @Override
    public int getInt(String column) {
        return dataSet.getInt(column);
    }

    @Override
    public long getLong(String column) {
        return dataSet.getLong(column);
    }

    @Override
    public Date getDate(String column) throws ParseException {
        return dataSet.getDate(column);
    }

    @Override
    public Date getDate(String column, SimpleDateFormat sdf) throws ParseException {
        return dataSet.getDate(column, sdf);
    }

    @Override
    public Object getObject(String column, Class<?> classToConvertTo) {
        return dataSet.getObject(column, classToConvertTo);
    }

    @Override
    public String[] getColumns() {
        return dataSet.getColumns();
    }

    @Override
    public String[] getColumns(String recordID) {
        return dataSet.getColumns(recordID);
    }

    @Override
    public int getRowNo() {
        return dataSet.getRowNo();
    }

    @Override
    public boolean isRecordID(String recordID) {
        return dataSet.isRecordID(recordID);
    }

    @Override
    public boolean contains(String column) {
        return dataSet.contains(column);
    }

    @Override
    public boolean isRowEmpty() {
        return dataSet.isRowEmpty();
    }

    @Override
    public String getRawData() {
        return dataSet.getRawData();
    }

    @Override
    public boolean next() {
        return dataSet.next();
    }

    @Override
    public Record getRecord() {
        return dataSet.getRecord();
    }
}
