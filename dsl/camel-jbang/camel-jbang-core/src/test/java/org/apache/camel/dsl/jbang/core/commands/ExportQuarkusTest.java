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
package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ExportQuarkusTest {

    @ParameterizedTest
    @CsvSource({
            // option names in camel case are dashed
            "quarkus.http.rootPath, quarkus.http.root-path",
            "quarkus.log.category.\"org.acme.myApp\".minLevel, quarkus.log.category.\"org.acme.myApp\".min-level",
            "quarkus.datasource.\"myOrders\".dbKind, quarkus.datasource.\"myOrders\".db-kind",
            // names chosen by the user are kept as they are
            "quarkus.http.port, quarkus.http.port",
            "quarkus.swagger-ui.urls.Camel, quarkus.swagger-ui.urls.Camel",
            "quarkus.datasource.Orders.db-kind, quarkus.datasource.Orders.db-kind",
            "quarkus.datasource.order_history.db-kind, quarkus.datasource.order_history.db-kind",
            "quarkus.kubernetes.env.vars.MY_VAR, quarkus.kubernetes.env.vars.MY_VAR",
            // a name in camel case that starts with a lower-case letter looks like an option name
            "quarkus.datasource.myOrders.db-kind, quarkus.datasource.my-orders.db-kind",
            "quarkus.datasource.my_DataSource.db-kind, quarkus.datasource.my-data-source.db-kind",
            // a quote that is not closed keeps the rest of the key
            "quarkus.log.category.\"org.acme.myApp.minLevel, quarkus.log.category.\"org.acme.myApp.minLevel",
            // empty segments
            "quarkus..http.rootPath, quarkus..http.root-path",
            "quarkus.http., quarkus.http." })
    public void shouldDashOnlyOptionNames(String key, String expected) {
        assertThat(ExportQuarkus.dashQuarkusOptionNames(key)).isEqualTo(expected);
    }
}
