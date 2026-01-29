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
package org.apache.camel.component.aws2.sns.integration;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sns.Sns2Constants;
import org.apache.camel.test.infra.common.SharedNameGenerator;
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for CAMEL-22429: Verify that subjects longer than 100 characters are properly truncated when sending
 * to AWS SNS, preventing InvalidParameterException.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
public class SnsTopicProducerSubjectTruncationIT extends Aws2SNSBase {

    @RegisterExtension
    public static SharedNameGenerator sharedNameGenerator = new TestEntityNameGenerator();

    @DisplayName("Test for CAMEL-22429 - Subject longer than 100 chars should be truncated and message sent successfully")
    @Test
    public void sendWithLongSubject() {
        // Create a subject longer than 100 characters (e.g., CloudEvents style subject)
        String longSubject
                = "This is a very long subject that exceeds the AWS SNS maximum subject length of 100 characters and should be truncated automatically";

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Sns2Constants.SUBJECT, longSubject);
                exchange.getIn().setBody("This is my message text with a long subject.");
            }
        });

        // If we get here without an exception, the truncation worked
        assertNotNull(exchange.getIn().getHeader(Sns2Constants.MESSAGE_ID));
    }

    @DisplayName("Test for CAMEL-22429 - Subject exactly 100 chars should work without truncation")
    @Test
    public void sendWithExact100CharSubject() {
        // Create a subject exactly 100 characters
        String exact100Subject = "A".repeat(Sns2Constants.MAX_SUBJECT_LENGTH);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Sns2Constants.SUBJECT, exact100Subject);
                exchange.getIn().setBody("This is my message text with exact 100 char subject.");
            }
        });

        assertNotNull(exchange.getIn().getHeader(Sns2Constants.MESSAGE_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("aws2-sns://%s?autoCreateTopic=true", sharedNameGenerator.getName());
            }
        };
    }
}
