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
package org.apache.camel.component.google.bigquery.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlHelperIdentifierTest {

    @Test
    public void testIdentifierShapedValuesAreAccepted() {
        assertThat(SqlHelper.isValidIdentifier("report_data")).isTrue();
        assertThat(SqlHelper.isValidIdentifier("_private")).isTrue();
        assertThat(SqlHelper.isValidIdentifier("test.table")).isTrue();
        assertThat(SqlHelper.isValidIdentifier("project-17248459.dataset.table")).isTrue();
    }

    @Test
    public void testValuesCarryingSqlSyntaxAreNotIdentifiers() {
        assertThat(SqlHelper.isValidIdentifier("")).isFalse();
        assertThat(SqlHelper.isValidIdentifier("1dataset")).isFalse();
        assertThat(SqlHelper.isValidIdentifier("dataset table")).isFalse();
        assertThat(SqlHelper.isValidIdentifier("table; DROP TABLE other")).isFalse();
        assertThat(SqlHelper.isValidIdentifier("nope' UNION ALL SELECT 1 --")).isFalse();
    }
}
