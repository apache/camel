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
package org.apache.camel.spring;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MainExampleTest {

    @Test
    public void testMain() {
        assertDoesNotThrow(() -> {
            Main main = new Main();
            main.configure().addRoutesBuilder(new RouteBuilder() {
                @Override
                public void configure() {
                    from("file://src/test/data?initialDelay=0&delay=10&noop=true").process(new MyProcessor())
                            .to("file://target/mainTest");
                }
            });
            main.start();

            // run for 1 second
            main.configure().setDurationMaxSeconds(1);

            main.stop();
        });

    }

    @Test
    public void testFileApplicationContextUri() {

        assertDoesNotThrow(() -> {
            Main main = new Main();

            main.setFileApplicationContextUri("src/test/resources/org/apache/camel/spring/routingUsingProcessor.xml");
            main.start();

            // run for 1 second
            main.configure().setDurationMaxSeconds(1);

            main.stop();

        });

    }

}
