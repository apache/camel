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
package org.apache.camel.test.junit5.patterns;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleNotifyBuilderTest extends CamelTestSupport {

    @Test
    public void testNotifyBuilder() {
        NotifyBuilder notify = new NotifyBuilder(context).from("seda:start").wereSentTo("seda:queue").whenDone(10).create();

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:start", "Camel" + i);
        }

        boolean matches = notify.matches(10, TimeUnit.SECONDS);
        assertTrue(matches);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").transform(simple("Bye ${body}")).to("seda:queue");
            }
        };
    }

}
