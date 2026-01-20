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
package org.apache.camel.component.file;

import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileEndpointParamsTest extends ContextTestSupport {

    @Test
    public void testChecksumFileAlgorithmParams() throws Exception {
        MyFileEndpoint myEndpoint = new MyFileEndpoint("file:target/foo", context.getComponent("file"));
        myEndpoint.setChecksumFileAlgorithm("MD5");

        Map<String, Object> params = myEndpoint.getParams();
        assertEquals("MD5", params.get("checksumFileAlgorithm"), "checksumFileAlgorithm should be in params map");
    }

    private static class MyFileEndpoint extends FileEndpoint {
        public MyFileEndpoint(String uri, Component component) {
            super(uri, component);
        }

        public Map<String, Object> getParams() {
            return getParamsAsMap();
        }
    }
}
