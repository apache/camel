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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRunLinesTest {

    private static ProcessorInfo processor(String source, long total, long failed, long mean) {
        ProcessorInfo p = new ProcessorInfo();
        p.source = source;
        p.total = total;
        p.failed = failed;
        p.meanTime = mean;
        return p;
    }

    @Test
    void whatTheProcessorsOfALineDid() {
        RouteInfo r = new RouteInfo();
        r.source = "file:/work/app/OrderRoute.java:9";
        r.total = 10;
        r.meanTime = 5;
        r.processors.add(processor("file:/work/app/OrderRoute.java:11", 10, 0, 2));
        // two processors on one line: summed, the mean weighted by the exchanges
        r.processors.add(processor("OrderRoute.java:12", 4, 1, 10));
        r.processors.add(processor("OrderRoute.java:12", 6, 0, 0));
        // another file, a processor without a location
        r.processors.add(processor("classpath:Other.java:12", 99, 99, 99));
        r.processors.add(processor(null, 99, 99, 99));

        Map<Integer, SourceViewer.LiveLine> lines = LiveRunLines.of(List.of(r), "/home/me/app/OrderRoute.java");
        assertThat(lines).containsOnlyKeys(8, 10, 11);
        assertThat(lines.get(8)).isEqualTo(new SourceViewer.LiveLine(10, 0, 5));
        assertThat(lines.get(10)).isEqualTo(new SourceViewer.LiveLine(10, 0, 2));
        assertThat(lines.get(11)).isEqualTo(new SourceViewer.LiveLine(10, 1, 4));
    }

    @Test
    void nothingForAnIntegrationThatDoesNotRun() {
        assertThat(LiveRunLines.of(List.of(), "OrderRoute.java")).isEmpty();
        assertThat(LiveRunLines.of(null, "OrderRoute.java")).isEmpty();
    }
}
