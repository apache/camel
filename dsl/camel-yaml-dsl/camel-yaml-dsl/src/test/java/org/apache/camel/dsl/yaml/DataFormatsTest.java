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
package org.apache.camel.dsl.yaml;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.dataformat.Base64DataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataFormatsTest extends YamlTestSupport {

    @Test
    void dataFormats() throws Exception {
        loadRoutes("""
                - dataFormats:
                  - base64:
                      id: df1
                      lineLength: 88
                  - csv:
                      id: df2
                      headerDisabled: true
                      ignoreEmptyLines: true
                """);

        var df1 = (Base64DataFormat) context.getDataFormats().get("df1");
        assertThat(df1).isNotNull();
        assertThat(df1.getId()).isEqualTo("df1");
        assertThat(df1.getLineLength()).isEqualTo("88");

        var df2 = (CsvDataFormat) context.getDataFormats().get("df2");
        assertThat(df2).isNotNull();
        assertThat(df2.getId()).isEqualTo("df2");
        assertThat(df2.getHeaderDisabled()).isEqualTo("true");
        assertThat(df2.getIgnoreEmptyLines()).isEqualTo("true");
    }
}
