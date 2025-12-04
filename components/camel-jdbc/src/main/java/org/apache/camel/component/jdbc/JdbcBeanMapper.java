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

package org.apache.camel.component.jdbc;

import static org.apache.camel.component.jdbc.JdbcHelper.newBeanInstance;

import java.sql.SQLException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.support.RowMapper;

public class JdbcBeanMapper implements RowMapper<Map<String, Object>, Object> {
    private final CamelContext camelContext;
    private final String outputClass;
    private final BeanRowMapper beanRowMapper;

    public JdbcBeanMapper(CamelContext camelContext, String outputClass, BeanRowMapper beanRowMapper) {
        this.camelContext = camelContext;
        this.outputClass = outputClass;
        this.beanRowMapper = beanRowMapper;
    }

    @Override
    public Object map(Map<String, Object> row) {
        if (row != null && outputClass != null) {
            try {
                return newBeanInstance(camelContext, outputClass, beanRowMapper, row);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            return row;
        }
    }
}
