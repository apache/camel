/**
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
package org.apache.camel.test.perf.esb;

import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class HttpHbrEsbPerformanceIntegrationTest extends AbstractBaseEsbPerformanceIntegrationTest {

    @Test
    public void testHttpHbr() throws Exception {
        // warm up with 1.000 messages so that the JIT compiler kicks in
        send("http://127.0.0.1:8192/service/CBRTransportHeaderProxy", 1000);

        StopWatch watch = new StopWatch();
        send("http://127.0.0.1:8192/service/CBRTransportHeaderProxy", count);

        log.warn("Ran {} tests in {}ms", count, watch.taken());
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/http-hbr-bundle-context.xml";
    }
}