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

import java.util.List;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyException;
import org.apache.camel.dsl.yaml.support.model.MyFailingProcessor;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnExceptionTest extends YamlTestSupport {

    @Test
    void onException() throws Exception {
        loadRoutes("""
                - beans:
                  - name: myFailingProcessor
                    type: %s
                - onException:
                    handled:
                      constant: "true"
                    exception:
                      - %s
                    steps:
                      - transform:
                          constant: "Sorry"
                      - to: "mock:on-exception"
                - from:
                    uri: "direct:start"
                    steps:
                      - process:
                          ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName(), MyException.class.getName()));
        withMock("mock:on-exception", mock -> mock.expectedBodiesReceived("Sorry"));
        context.start();
        withTemplate(t -> t.to("direct:start").withBody("hello").send());
        MockEndpoint.assertIsSatisfied(context);
    }

    // CAMEL-24702: handled is an expression; a plain value must fail with a message that says what to write
    @Test
    void onExceptionHandledAsPlainValueFailsWithHelpfulMessage() {
        var ex = assertThrows(Exception.class, () -> loadRoutes(
                List.of(ResourceHelper.fromString("route-0.yaml", """
                        - onException:
                            handled: true
                            exception:
                              - java.lang.Exception
                            steps:
                              - to: "mock:on-exception"
                        """)), false));
        boolean found = false;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null
                    && t.getMessage().contains("an expression is expected here, not a plain value (true)")
                    && t.getMessage().contains("constant: {expression: \"true\"}")) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Expected helpful message about expression; actual exception: " + ex).isTrue();
    }

    // CAMEL-24702: an unsupported field names the node and the field; bean as a language says method:
    @Test
    void setBodyWithBeanAsFieldFailsWithMessageNamingField() {
        var ex = assertThrows(Exception.class, () -> loadRoutes(
                List.of(ResourceHelper.fromString("route-1.yaml", """
                        - from:
                            uri: timer:tick
                            steps:
                              - setBody:
                                  bean: myBean
                        """)), false));
        boolean found = false;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null
                    && t.getMessage().contains("Error constructing YAML node id: setBody: unsupported field: bean")) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Expected message naming the field; actual: " + ex).isTrue();
    }
}
