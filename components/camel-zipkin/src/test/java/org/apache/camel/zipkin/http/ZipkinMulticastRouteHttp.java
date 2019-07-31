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
package org.apache.camel.zipkin.http;

import org.apache.camel.zipkin.ZipkinMulticastRouteTest;
import org.apache.camel.zipkin.ZipkinTracer;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Integration test requires running Zipkin running
 *
 * <p>The easiest way to run is locally:
 * <pre>{@code
 * curl -sSL https://zipkin.io/quickstart.sh | bash -s
 * java -jar zipkin.jar
 * }</pre>
 */
public class ZipkinMulticastRouteHttp extends ZipkinMulticastRouteTest {
    @Override protected void setSpanReporter(ZipkinTracer zipkin) {
        zipkin.setSpanReporter(
            AsyncReporter.create(URLConnectionSender.create("http://locahost:9411/api/v2/spans"))
        );
    }
}
