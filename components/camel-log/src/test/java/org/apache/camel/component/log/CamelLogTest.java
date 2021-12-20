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
package org.apache.camel.component.log;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CamelLogTest extends CamelLogTestSupport {

    @Test
    void testPlainLog() {
        String body = "Body as string";
        template.sendBody("direct:startPlain", body);

        Assertions.assertThat(Assertions.contentOf(new File(LOG_FILE_LOCATION)).trim())
                .isEqualTo(body);
    }

    @Test
    void testSimpleLog() {
        String body = "Body as string";
        template.sendBody("direct:startSimple", body);

        Assertions.assertThat(Assertions.contentOf(new File(LOG_FILE_LOCATION)).trim())
                .isEqualTo("Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + body + "]");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:startPlain").to("log:info?plain=true");

                from("direct:startSimple").to("log:info");
            }
        };
    }
}
