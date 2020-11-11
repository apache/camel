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
package org.apache.camel.component.google.bigquery.unit.sql;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.google.bigquery.sql.SqlHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class SqlHelperTest {

    String query = "INSERT INTO ${report}.test( -- TODO \n" + "  id,\n" + "  region\n" + ")\n" + "SELECT\n" + "  id,\n"
                   + "  region\n" + "FROM\n" + "  ${import}.test\n" + "WHERE\n"
                   + "  rec_date = @date AND id = @id\n";

    String expected = "INSERT INTO report_data.test( -- TODO \n" + "  id,\n" + "  region\n" + ")\n" + "SELECT\n" + "  id,\n"
                      + "  region\n" + "FROM\n" + "  import_data.test\n"
                      + "WHERE\n" + "  rec_date = @date AND id = @id\n";

    Exchange exchange = Mockito.mock(Exchange.class);
    Message message = Mockito.mock(Message.class);

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testResolveQuery() throws Exception {
        String answer = SqlHelper.resolveQuery(context, "delete from test.test_sql_table where id = 1", null);
        assertEquals("delete from test.test_sql_table where id = 1", answer);
    }

    @Test
    @Disabled
    public void testResolveClasspathQuery() throws Exception {
        String answer = SqlHelper.resolveQuery(context, "classpath:sql/delete.sql", ":");
        assertEquals("delete from test.test_sql_table where id = @id", answer);
    }

    @Test
    public void testTranslateQuery() {
        when(exchange.getMessage()).thenReturn(message);
        when(message.getHeader(eq("report"), eq(String.class))).thenReturn("report_data");
        when(message.getHeader(eq("import"), eq(String.class))).thenReturn("import_data");

        String answer = SqlHelper.translateQuery(query, exchange);
        assertEquals(expected, answer);
    }

    @Test
    public void testTranslateQueryProperties() {
        when(exchange.getMessage()).thenReturn(message);
        when(exchange.getProperty(eq("report"), eq(String.class))).thenReturn("report_data");
        when(exchange.getProperty(eq("import"), eq(String.class))).thenReturn("import_data");

        String answer = SqlHelper.translateQuery(query, exchange);
        assertEquals(expected, answer);
    }

    @Test
    public void testTranslateQueryWithoutParam() {
        when(exchange.getMessage()).thenReturn(message);
        when(message.getHeader(eq("report"), eq(String.class))).thenReturn("report_data");
        assertThrows(RuntimeExchangeException.class,
                () -> SqlHelper.translateQuery(query, exchange));
    }

    @Test
    public void testExtractParameterNames() {
        Set<String> answer = SqlHelper.extractParameterNames(query);
        assertEquals(2, answer.size());
        assertTrue(answer.contains("date"), "Parameter 'date' not found");
        assertTrue(answer.contains("id"), "Parameter 'id' not found");
    }
}
