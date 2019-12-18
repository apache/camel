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
package org.apache.camel.component.cassandra;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

/**
 * Implementations of {@link ResultSetConversionStrategy}
 */
public final class ResultSetConversionStrategies {

    private static final Pattern LIMIT_NAME_PATTERN = Pattern.compile("^LIMIT_(\\d+)$");

    private static final ResultSetConversionStrategy ALL = new ResultSetConversionStrategy() {
        @Override
        public Object getBody(ResultSet resultSet) {
            return resultSet.all();
        }
    };

    private static final ResultSetConversionStrategy ONE = new ResultSetConversionStrategy() {
        @Override
        public Object getBody(ResultSet resultSet) {
            return resultSet.one();
        }
    };

    private ResultSetConversionStrategies() {
    }

    /**
     * Retrieve all rows. Message body contains a big list of {@link Row}s
     */
    public static ResultSetConversionStrategy all() {
        return ALL;
    }

    /**
     * Retrieve a single row. Message body contains a single {@link Row}
     */
    public static ResultSetConversionStrategy one() {
        return ONE;
    }

    private static class LimitResultSetConversionStrategy implements ResultSetConversionStrategy {
        private final int rowMax;

        LimitResultSetConversionStrategy(int rowMax) {
            this.rowMax = rowMax;
        }

        @Override
        public Object getBody(ResultSet resultSet) {
            List<Row> rows = new ArrayList<>(rowMax);
            int rowCount = 0;
            Iterator<Row> rowIter = resultSet.iterator();
            while (rowIter.hasNext() && rowCount < rowMax) {
                rows.add(rowIter.next());
                rowCount++;
            }
            return rows;
        }
    }

    /**
     * Retrieve a limited list of rows. Message body contains a list of
     * {@link Row} containing at most rowMax rows.
     */
    public static ResultSetConversionStrategy limit(int rowMax) {
        return new LimitResultSetConversionStrategy(rowMax);
    }

    /**
     * Get {@link ResultSetConversionStrategy} from String
     */
    public static ResultSetConversionStrategy fromName(String name) {
        if (name == null) {
            return null;
        }
        if (name.equals("ALL")) {
            return ResultSetConversionStrategies.all();
        }
        if (name.equals("ONE")) {
            return ResultSetConversionStrategies.one();
        }
        Matcher matcher = LIMIT_NAME_PATTERN.matcher(name);
        if (matcher.matches()) {
            int limit = Integer.parseInt(matcher.group(1));
            return limit(limit);
        }
        throw new IllegalArgumentException("Unknown conversion strategy " + name);
    }
}
