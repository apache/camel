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
package org.apache.camel.component.flatpack;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.sf.flatpack.DataError;
import net.sf.flatpack.DataSet;
import net.sf.flatpack.Record;
import net.sf.flatpack.ordering.OrderBy;

/**
 * A read-only {@link DataSet} with one row: the row the parsed {@link DataSet} is on when this is created. With
 * splitRows, every message gets its own {@link DataSetRow}, so a message keeps its row while the next rows are sent. It
 * starts on its row, like the parsed {@link DataSet} at that row.
 */
public class DataSetRow implements DataSet {
    private final Record row;
    private final List<DataError> errors;
    private int pointer;
    private Record current;

    public DataSetRow(DataSet dataSet) {
        this.row = dataSet.getRecord()
                .orElseThrow(() -> new IllegalArgumentException("The DataSet is not on a row"));
        this.errors = new ArrayList<>(dataSet.getErrors());
        this.current = row;
    }

    private Record current() {
        if (current == null) {
            throw new IllegalStateException("The DataSet is not on a row");
        }
        return current;
    }

    // cursor over the one row
    // --------------------------------------------------------------

    @Override
    public boolean next() {
        if (pointer < 0) {
            pointer = 0;
            current = row;
            return true;
        }
        current = null;
        return false;
    }

    @Override
    public boolean previous() {
        current = null;
        return false;
    }

    @Override
    public void goTop() {
        pointer = -1;
        current = null;
    }

    @Override
    public void goBottom() {
        pointer = 0;
        current = row;
    }

    @Override
    public void absolute(int localPointer) {
        if (localPointer != 0) {
            throw new IndexOutOfBoundsException("INVALID POINTER LOCATION: " + localPointer);
        }
        pointer = 0;
        current = row;
    }

    @Override
    public int getIndex() {
        return pointer;
    }

    @Override
    public int getRowCount() {
        return 1;
    }

    @Override
    public Optional<Record> getRecord() {
        return Optional.ofNullable(current);
    }

    @Override
    public void orderRows(OrderBy ob) {
        // one row is always in order
        goTop();
    }

    @Override
    public List<DataError> getErrors() {
        return errors;
    }

    @Override
    public int getErrorCount() {
        return errors.size();
    }

    @Override
    public boolean isAnError(int lineNo) {
        for (DataError error : errors) {
            if (error.getLineNo() == lineNo && error.getErrorLevel() > 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearErrors() {
        errors.clear();
    }

    // read-only
    // --------------------------------------------------------------

    @Override
    public void remove() {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void setLowerCase() {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void setUpperCase() {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void setStrictNumericParse(boolean strictNumericParse) {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void setPZConvertProps(Properties props) {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void setValue(String column, String value) {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void clearRows() {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    @Override
    public void clearAll() {
        throw new UnsupportedOperationException("A row of a split DataSet is read-only");
    }

    // the values of the row
    // --------------------------------------------------------------

    @Override
    public String[] getColumns() {
        return row.getColumns();
    }

    @Override
    public String[] getColumns(String recordID) {
        return row.getColumns(recordID);
    }

    @Override
    public int getRowNo() {
        return current().getRowNo();
    }

    @Override
    public boolean isRecordID(String recordID) {
        return current().isRecordID(recordID);
    }

    @Override
    public String getRecordID() {
        return current().getRecordID();
    }

    @Override
    public boolean contains(String column) {
        return current().contains(column);
    }

    @Override
    public boolean isRowEmpty() {
        return current().isRowEmpty();
    }

    @Override
    public String getRawData() {
        return current().getRawData();
    }

    @Override
    public String getString(String column) {
        return current().getString(column);
    }

    @Override
    public String getString(String column, Supplier<String> defaultSupplier) {
        return current().getString(column, defaultSupplier);
    }

    @Override
    public double getDouble(String column) {
        return current().getDouble(column);
    }

    @Override
    public double getDouble(String column, DoubleSupplier defaultSupplier) {
        return current().getDouble(column, defaultSupplier);
    }

    @Override
    public BigDecimal getBigDecimal(String column) {
        return current().getBigDecimal(column);
    }

    @Override
    public BigDecimal getBigDecimal(String column, Supplier<BigDecimal> defaultSupplier) {
        return current().getBigDecimal(column, defaultSupplier);
    }

    @Override
    public int getInt(String column) {
        return current().getInt(column);
    }

    @Override
    public int getInt(String column, IntSupplier defaultSupplier) {
        return current().getInt(column, defaultSupplier);
    }

    @Override
    public long getLong(String column) {
        return current().getLong(column);
    }

    @Override
    public long getLong(String column, LongSupplier defaultSupplier) {
        return current().getLong(column, defaultSupplier);
    }

    @Override
    public Date getDate(String column) throws ParseException {
        return current().getDate(column);
    }

    @Override
    public Date getDate(String column, SimpleDateFormat sdf) throws ParseException {
        return current().getDate(column, sdf);
    }

    @Override
    public Date getDate(String column, Supplier<Date> defaultSupplier) throws ParseException {
        return current().getDate(column, defaultSupplier);
    }

    @Override
    public Date getDate(String column, SimpleDateFormat sdf, Supplier<Date> defaultSupplier)
            throws ParseException {
        return current().getDate(column, sdf, defaultSupplier);
    }

    @Override
    public LocalDate getLocalDate(String column) throws ParseException {
        return current().getLocalDate(column);
    }

    @Override
    public LocalDate getLocalDate(String column, Supplier<LocalDate> defaultSupplier) throws ParseException {
        return current().getLocalDate(column, defaultSupplier);
    }

    @Override
    public LocalDate getLocalDate(String column, DateTimeFormatter formatter) throws ParseException {
        return current().getLocalDate(column, formatter);
    }

    @Override
    public LocalDate getLocalDate(String column, String dateFormat) throws ParseException {
        return current().getLocalDate(column, dateFormat);
    }

    @Override
    public LocalDate getLocalDate(String column, String dateFormat, Supplier<LocalDate> defaultSupplier)
            throws ParseException {
        return current().getLocalDate(column, dateFormat, defaultSupplier);
    }

    @Override
    public Object getObject(String column, Class<?> classToConvertTo) {
        return current().getObject(column, classToConvertTo);
    }

    @Override
    public String toString() {
        return "DataSetRow[rowNo=" + row.getRowNo() + ", errors=" + errors.size() + "]";
    }
}
