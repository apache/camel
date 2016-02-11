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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;

public class ModelAwareFilterList implements ModelAwareFilter<FilterList> {

    FilterList fl;

    /**
     * Default constructor, filters nothing. Required though for RPC deserialization.
     */
    public ModelAwareFilterList() {
        fl = new FilterList(); //it's worth to prevent the class against null pointer on fl
    }

    /**
     * Constructor that takes a set of {@link org.apache.hadoop.hbase.filter.Filter}s. The default operator
     * MUST_PASS_ALL is assumed.
     *
     * @param rowFilters list of filters
     */
    public ModelAwareFilterList(List<Filter> rowFilters) {
        fl = new FilterList(rowFilters);
    }

    /**
     * Constructor that takes an operator.
     *
     * @param operator Operator to process filter set with.
     */
    public ModelAwareFilterList(FilterList.Operator operator) {
        fl = new FilterList(operator);
    }

    /**
     * Constructor that takes a set of {@link org.apache.hadoop.hbase.filter.Filter}s and an operator.
     *
     * @param operator   Operator to process filter set with.
     * @param rowFilters Set of row filters.
     */
    public ModelAwareFilterList(FilterList.Operator operator, List<Filter> rowFilters) {
        fl = new FilterList(operator, rowFilters);
    }

    /**
     * Applies the message to {@link org.apache.hadoop.hbase.filter.Filter} to context.
     */
    @Override
    public void apply(CamelContext context, HBaseRow rowModel) {
        for (Filter filter : getFilters()) {
            if (ModelAwareFilter.class.isAssignableFrom(filter.getClass())) { //probably and is assignable from filter
                ((ModelAwareFilter<?>) filter).apply(context, rowModel);
            }
        }
    }

    /**
     * Wraps an existing {@link FilterList} filter into a {@link ModelAwareFilterList}.
     */
    public static ModelAwareFilterList wrap(FilterList filter) {
        return new ModelAwareFilterList(filter.getOperator(), filter.getFilters());
    }

    public List<Filter> getFilters() {
        return fl.getFilters();
    }

    public void addFilter(Filter filter) {
        getFilters().add(filter);
    }

    @Override
    public FilterList getFilteredList() {
        return fl;
    }
}
