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
package org.apache.camel.component.google.bigquery.unit;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies what the producer reports for a body it cannot turn into rows.
 */
class GoogleBigQueryProducerBodyTypeTest extends BaseBigQueryTest {

    @Test
    void aBodyThatIsNotAMapOrAListIsReported() throws Exception {
        Exchange exchange = createExchangeWithBody("not a row");
        producer.process(exchange);

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot handle body type java.lang.String");
    }

    @Test
    void aMissingBodyIsReportedInsteadOfFailingWithANullPointer() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot handle body type null");
    }
}
