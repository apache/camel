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
package org.apache.camel.component.hbase.filters;

import org.apache.camel.CamelContext;
import org.apache.camel.component.hbase.HBaseHelper;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.SingleColumnValueExcludeFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;

/**
 * A {@link FilterList} that contains multiple {@link SingleColumnValueExcludeFilter}s one per column that is part of the model.
 */
public class ModelAwareColumnMatchingFilter implements ModelAwareFilter<FilterList> {
    FilterList fl;

    /**
     * Writable constructor, do not use.
     */
    public ModelAwareColumnMatchingFilter() {
        fl = new FilterList();
    }

    public FilterList getFilteredList() {
        return fl;
    }

    /**
     * Applies the message to {@link org.apache.hadoop.hbase.filter.Filter} to context.
     */
    @Override
    public void apply(CamelContext context, HBaseRow rowModel) {
        fl.getFilters().clear();
        if (rowModel != null) {
            for (HBaseCell cell : rowModel.getCells()) {
                if (cell.getValue() != null) {
                    byte[] family = HBaseHelper.getHBaseFieldAsBytes(cell.getFamily());
                    byte[] qualifier = HBaseHelper.getHBaseFieldAsBytes(cell.getQualifier());
                    byte[] value = context.getTypeConverter().convertTo(byte[].class, cell.getValue());
                    SingleColumnValueFilter columnValueFilter = new SingleColumnValueFilter(family, qualifier, CompareFilter.CompareOp.EQUAL, value);
                    fl.addFilter(columnValueFilter);
                }
            }
        }
    }
}
