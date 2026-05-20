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
package org.apache.camel.component.aws2.s3.utils;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AWS2S3UtilsTest extends CamelTestSupport {

    // ---- evaluateDestinationBucketPrefix ----

    @Test
    void prefixLiteralStringPassesThroughUnchanged() {
        Exchange exchange = createExchangeWithBody("body");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setDestinationBucketPrefix("archive/");

        assertEquals("archive/", AWS2S3Utils.evaluateDestinationBucketPrefix(exchange, config));
    }

    @Test
    void prefixSimpleExpressionIsEvaluated() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader("folder", "2026/05");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setDestinationBucketPrefix("${header.folder}/");

        assertEquals("2026/05/", AWS2S3Utils.evaluateDestinationBucketPrefix(exchange, config));
    }

    @Test
    void prefixNullOrEmptyReturnsEmptyString() {
        Exchange exchange = createExchangeWithBody("body");
        AWS2S3Configuration config = new AWS2S3Configuration();
        // no prefix set — null

        assertEquals("", AWS2S3Utils.evaluateDestinationBucketPrefix(exchange, config));
    }

    // ---- evaluateDestinationBucketSuffix ----

    @Test
    void suffixLiteralStringPassesThroughUnchanged() {
        Exchange exchange = createExchangeWithBody("body");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setDestinationBucketSuffix(".processed");

        assertEquals(".processed", AWS2S3Utils.evaluateDestinationBucketSuffix(exchange, config));
    }

    @Test
    void suffixSimpleExpressionIsEvaluated() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader("env", "prod");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setDestinationBucketSuffix("-${header.env}");

        assertEquals("-prod", AWS2S3Utils.evaluateDestinationBucketSuffix(exchange, config));
    }

    @Test
    void suffixNullOrEmptyReturnsEmptyString() {
        Exchange exchange = createExchangeWithBody("body");
        AWS2S3Configuration config = new AWS2S3Configuration();
        // no suffix set — null

        assertEquals("", AWS2S3Utils.evaluateDestinationBucketSuffix(exchange, config));
    }
}
