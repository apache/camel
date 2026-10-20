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
package org.apache.camel.parser.helper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.apache.camel.parser.model.CoverageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route coverage dump: the counts, and the body types each step has been reached with (CAMEL-25750).
 */
public class RouteCoverageHelperTest {

    @TempDir
    Path dir;

    @Test
    public void testTheBodyTypesOfEveryDumpFile() throws Exception {
        // each test writes its own dump file: one sent text, the other data
        Files.writeString(dir.resolve("OrdersTextTest-testText.xml"), dump(2, "java.lang.String", "payload"));
        Files.writeString(dir.resolve("OrdersDataTest-testData.xml"), dump(1, "java.util.LinkedHashMap", "data"));

        List<CoverageData> data = RouteCoverageHelper.parseDumpRouteCoverageByRouteId(dir.toString(), "orders");

        assertEquals(2, data.size());
        assertEquals("from", data.get(0).getNode());
        assertEquals(3, data.get(0).getCount());
        assertTrue(data.get(0).getBodyTypes().isEmpty());

        CoverageData to = data.get(1);
        assertEquals("to", to.getNode());
        assertEquals(3, to.getCount());
        // the files are read in no particular order
        assertEquals(Set.of("java.lang.String", "java.util.LinkedHashMap"), Set.copyOf(to.getBodyTypes()));
        assertEquals(Set.of("payload", "data"), Set.copyOf(to.getBodyKinds()));
    }

    @Test
    public void testADumpWithoutBodyTypes() throws Exception {
        Files.writeString(dir.resolve("OrdersTest-test.xml"), dump(1, null, null));

        List<CoverageData> data = RouteCoverageHelper.parseDumpRouteCoverageByRouteId(dir.toString(), "orders");

        assertEquals(1, data.get(1).getCount());
        assertTrue(data.get(1).getBodyTypes().isEmpty());
        assertTrue(data.get(1).getBodyKinds().isEmpty());
    }

    private static String dump(int count, String bodyTypes, String bodyKinds) {
        String body = bodyTypes != null ? " bodyTypes=\"" + bodyTypes + "\" bodyKinds=\"" + bodyKinds + "\"" : "";
        return """
                <camelContextRouteCoverage id="camel-1" exchangesTotal="%d" totalProcessingTime="0">
                <routes>
                  <route id="orders" exchangesTotal="%d" totalProcessingTime="0">
                    <from uri="direct:start" exchangesTotal="%d" totalProcessingTime="0" index="0"/>
                    <to id="result" uri="mock:result" exchangesTotal="%d" totalProcessingTime="0" index="1"%s/>
                  </route>
                </routes>
                </camelContextRouteCoverage>
                """.formatted(count, count, count, count, body);
    }
}
