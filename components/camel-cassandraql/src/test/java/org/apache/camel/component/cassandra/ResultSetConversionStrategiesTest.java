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

import java.util.Collections;
import java.util.List;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ResultSetConversionStrategy} implementations
 */
public class ResultSetConversionStrategiesTest {

    public ResultSetConversionStrategiesTest() {
    }

    @Test
    public void testAll() {
        ResultSetConversionStrategy strategy = ResultSetConversionStrategies.fromName("ALL");
        ResultSet resultSet = mock(ResultSet.class);
        List<Row> rows = Collections.nCopies(20, mock(Row.class));
        when(resultSet.all()).thenReturn(rows);

        Object body = strategy.getBody(resultSet);
        assertTrue(body instanceof List);
        assertSame(rows, body);
    }

    @Test
    public void testOne() {
        ResultSetConversionStrategy strategy = ResultSetConversionStrategies.fromName("ONE");
        ResultSet resultSet = mock(ResultSet.class);
        Row row = mock(Row.class);
        when(resultSet.one()).thenReturn(row);

        Object body = strategy.getBody(resultSet);
        assertTrue(body instanceof Row);
        assertSame(row, body);
    }

    @Test
    public void testLimit() {
        ResultSetConversionStrategy strategy = ResultSetConversionStrategies.fromName("LIMIT_10");
        ResultSet resultSet = mock(ResultSet.class);
        List<Row> rows = Collections.nCopies(20, mock(Row.class));
        when(resultSet.iterator()).thenReturn(rows.iterator());

        Object body = strategy.getBody(resultSet);
        assertTrue(body instanceof List);
        assertEquals(10, ((List<?>)body).size());
    }
}
