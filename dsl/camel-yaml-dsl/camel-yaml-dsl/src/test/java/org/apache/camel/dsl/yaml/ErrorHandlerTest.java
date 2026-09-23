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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyFailingProcessor;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.SpringTransactionErrorHandlerDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlerTest extends YamlTestSupport {

    @Test
    void errorHandlerRefWithBean() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                      - name: myErrorHandler
                        type: org.apache.camel.model.errorhandler.DeadLetterChannelDefinition
                        properties:
                          dead-letter-uri: "mock:on-error"
                          redelivery-delay: 0
                    - errorHandler:
                        refErrorHandler:
                          ref: "myErrorHandler"
                    - from:
                        uri: "direct:start"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName()));

        withMock("mock:on-error", mock -> mock.expectedMessageCount(1));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory()).isInstanceOf(RefErrorHandlerDefinition.class);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void errorHandlerRef() throws Exception {
        loadRoutes("""
                    - errorHandler:
                        refErrorHandler:
                          ref: "myErrorHandler"
                """);

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory()).isInstanceOf(RefErrorHandlerDefinition.class);

        RefErrorHandlerDefinition errorHandler
                = (RefErrorHandlerDefinition) context.getCamelContextExtension().getErrorHandlerFactory();
        assertThat(errorHandler.getRef()).isEqualTo("myErrorHandler");
    }

    @Test
    void errorHandlerRefInlined() throws Exception {
        loadRoutes("""
                    - errorHandler:
                        refErrorHandler: "myErrorHandler"
                """);

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory()).isInstanceOf(RefErrorHandlerDefinition.class);

        RefErrorHandlerDefinition errorHandler
                = (RefErrorHandlerDefinition) context.getCamelContextExtension().getErrorHandlerFactory();
        assertThat(errorHandler.getRef()).isEqualTo("myErrorHandler");
    }

    @Test
    void errorHandlerDeadLetterChannel() throws Exception {
        loadRoutes("""
                    - errorHandler:
                        deadLetterChannel:
                          deadLetterUri: "mock:on-error"
                          redeliveryPolicy:
                            maximumRedeliveries: 3
                """);

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory()).isInstanceOf(DeadLetterChannelDefinition.class);

        DeadLetterChannelDefinition errorHandler
                = (DeadLetterChannelDefinition) context.getCamelContextExtension().getErrorHandlerFactory();
        assertThat(errorHandler.getDeadLetterUri()).isEqualTo("mock:on-error");
        assertThat(errorHandler.getRedeliveryPolicy().getMaximumRedeliveries()).isEqualTo("3");
    }

    @Test
    void errorHandlerDefaultErrorHandler() throws Exception {
        loadRoutes("""
                    - errorHandler:
                        defaultErrorHandler:
                          useOriginalMessage: true
                          redeliveryPolicy:
                            maximumRedeliveries: 2
                """);

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory())
                .isInstanceOf(DefaultErrorHandlerDefinition.class);

        DefaultErrorHandlerDefinition errorHandler
                = (DefaultErrorHandlerDefinition) context.getCamelContextExtension().getErrorHandlerFactory();
        assertThat(errorHandler.getUseOriginalMessage()).isEqualTo("true");
        assertThat(errorHandler.getRedeliveryPolicy().getMaximumRedeliveries()).isEqualTo("2");
    }

    @Test
    void errorHandlerNo() throws Exception {
        loadRoutes("""
                    - errorHandler:
                        noErrorHandler: {}
                """);

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory()).isInstanceOf(NoErrorHandlerDefinition.class);
    }

    @Test
    void errorHandlerRedeliveryPolicyRef() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                      - name: myPolicy
                        type: org.apache.camel.processor.errorhandler.RedeliveryPolicy
                        properties:
                          maximumRedeliveries: 3
                          logStackTrace: true
                    - errorHandler:
                        defaultErrorHandler:
                          useOriginalMessage: true
                          redeliveryPolicyRef: myPolicy
                    - from:
                        uri: "direct:start"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName()));

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory())
                .isInstanceOf(DefaultErrorHandlerDefinition.class);

        DefaultErrorHandlerDefinition errorHandler
                = (DefaultErrorHandlerDefinition) context.getCamelContextExtension().getErrorHandlerFactory();
        assertThat(errorHandler.getUseOriginalMessage()).isEqualTo("true");
        assertThat(errorHandler.hasRedeliveryPolicy()).isFalse();
        assertThat(errorHandler.getRedeliveryPolicyRef()).isEqualTo("myPolicy");
    }

    @Test
    void errorHandlerSpringTransactionErrorHandler() throws Exception {
        loadRoutesNoValidate("""
                    - errorHandler:
                        springTransactionErrorHandler:
                          transactedPolicyRef: "myTxPolicy"
                """);

        context.start();

        assertThat(context.getCamelContextExtension().getErrorHandlerFactory())
                .isInstanceOf(SpringTransactionErrorHandlerDefinition.class);

        SpringTransactionErrorHandlerDefinition errorHandler
                = (SpringTransactionErrorHandlerDefinition) context.getCamelContextExtension().getErrorHandlerFactory();
        assertThat(errorHandler.getTransactedPolicyRef()).isEqualTo("myTxPolicy");
    }

    @Test
    void errorDuplicateErrorHandler() {
        var route = """
                    - errorHandler:
                        defaultErrorHandler:
                          useOriginalMessage: true
                          redeliveryPolicy:
                            maximumRedeliveries: 2
                        deadLetterChannel:
                          deadLetterUri: "mock:on-error"
                          redeliveryPolicy:
                            maximumRedeliveries: 3
                """;

        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage().contains("2 are valid")).isTrue();
        }
    }
}
