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
package org.apache.camel.component.sql;

/**
 * The type of output produced by the SQL producer and consumer for the result of a query.
 */
public enum SqlOutputType {

    /**
     * Returns the result of the query as a single object.
     * <p>
     * If the query has only a single column, the value of that column is returned (for example
     * {@code SELECT COUNT(*) FROM PROJECT} returns a {@link Long}). If the query has more than one column, a
     * {@link java.util.Map} of the row is returned. If {@code outputClass} is set, the row is instead converted into a
     * Java bean of that type by calling the setters that match the column names; the class must have a default
     * constructor.
     * <p>
     * If the query returns more than one row, a non-unique result exception is thrown.
     */
    SelectOne,

    /**
     * Returns the result of the query as a {@link java.util.List} of {@link java.util.Map}, one map per row keyed by
     * column name. If {@code outputClass} is set, each row is instead converted into a Java bean of that type by
     * calling the setters that match the column names.
     */
    SelectList,

    /**
     * Streams the result of the query using an {@link java.util.Iterator}, so rows are read from the
     * {@link java.sql.ResultSet} lazily instead of being loaded into memory all at once. This can be combined with the
     * Splitter EIP in streaming mode to process the result set in a streaming fashion.
     */
    StreamList
}
