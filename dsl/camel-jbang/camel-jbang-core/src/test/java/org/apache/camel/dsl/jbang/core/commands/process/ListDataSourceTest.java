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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * camel get datasource: the connection pools of the integrations.
 */
@ExtendWith(MockitoExtension.class)
class ListDataSourceTest extends ProcessCommandTestSupport {

    @Test
    void dataSourcesAsJson() throws Exception {
        JsonObject root = buildContextStatus("orders", 5);
        root.put("dataSources", dataSources(pool("default", 2, 3)));
        writeStatusFile(TEST_PID, root);

        ListDataSource command = new ListDataSource(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;
        command.sort = "pid";

        assertThat(call(command)).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        JsonObject row = rows.getMap(0);
        assertThat(row.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(row.getString("name")).isEqualTo("orders");
        assertThat(row.getString("dsName")).isEqualTo("default");
        assertThat(row.getString("poolType")).isEqualTo("HikariCP");
        assertThat(row.getInteger("active")).isEqualTo(2);
        assertThat(row.getInteger("idle")).isEqualTo(3);
        assertThat(row.getInteger("total")).isEqualTo(5);
        assertThat(row.getInteger("maxPoolSize")).isEqualTo(10);
    }

    @Test
    void tableOfTheDataSources() throws Exception {
        JsonObject root = buildContextStatus("orders", 5);
        root.put("dataSources", dataSources(pool("default", 2, 3), pool("audit", 0, 1)));
        writeStatusFile(TEST_PID, root);

        ListDataSource command = new ListDataSource(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        assertThat(call(command)).isZero();

        assertThat(printer.getStdout()).contains("default-pool").contains("audit-pool");
    }

    @Test
    void integrationWithoutDataSourcesListsNothing() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));

        ListDataSource command = new ListDataSource(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;
        command.sort = "pid";

        assertThat(call(command)).isZero();

        assertThat(printer.getStdout()).isEqualTo("[]");
    }

    private static JsonObject dataSources(JsonObject... pools) {
        JsonObject answer = new JsonObject();
        answer.put("dataSources", new JsonArray(List.of(pools)));
        return answer;
    }

    private static JsonObject pool(String name, int active, int idle) {
        JsonObject pool = new JsonObject();
        pool.put("name", name);
        pool.put("type", "org.postgresql.ds.PGSimpleDataSource");
        pool.put("poolType", "HikariCP");
        pool.put("poolName", name + "-pool");
        pool.put("active", active);
        pool.put("idle", idle);
        pool.put("total", active + idle);
        pool.put("maxPoolSize", 10);
        pool.put("waiting", 0);
        return pool;
    }

    private int call(ListDataSource command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }
}
