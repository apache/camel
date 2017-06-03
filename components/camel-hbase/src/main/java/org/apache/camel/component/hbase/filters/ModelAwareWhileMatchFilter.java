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
import org.apache.camel.component.hbase.model.HBaseRow;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.WhileMatchFilter;

public class ModelAwareWhileMatchFilter extends WhileMatchFilter implements ModelAwareFilter<WhileMatchFilter> {

    public ModelAwareWhileMatchFilter(Filter filter) {
        super(filter);
    }

    /**
     * Applies the message to {@link org.apache.hadoop.hbase.filter.Filter} to context.
     */
    @Override
    public void apply(CamelContext context, HBaseRow rowModel) {
        if (ModelAwareFilter.class.isAssignableFrom(getFilter().getClass())) {
            ((ModelAwareFilter<?>) getFilter()).apply(context, rowModel);
        }
    }

    /**
     * Wraps an existing {@link WhileMatchFilter} filter into a {@link ModelAwareWhileMatchFilter}.
     */
    public static ModelAwareWhileMatchFilter wrap(WhileMatchFilter filter) {
        return new ModelAwareWhileMatchFilter(filter.getFilter());
    }

    @Override
    public FilterList getFilteredList() {
        return null;
    }

}
