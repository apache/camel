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
package org.apache.camel.support.sql;

import javax.sql.DataSource;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * Factory for creating DataSource instances used by SQL components.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>{@code
 * from("timer:test?period=1000")
 *         .to("sql:select * from users?dataSourceFactory=#class:com.example.MyDataSourceFactory")
 *         .log("${body}");
 * }</pre>
 */
public interface DataSourceFactory extends CamelContextAware {

    /**
     * Creates a DataSource for the given exchange.
     *
     * @return           a configured DataSource instance
     * @throws Exception if the DataSource cannot be created
     */
    DataSource createDataSource(Endpoint endpoint) throws Exception;

}
