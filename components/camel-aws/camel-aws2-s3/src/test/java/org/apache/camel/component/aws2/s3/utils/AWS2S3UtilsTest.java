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
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    // ---- determineKey ----

    @Test
    void keyFromHeaderIsUsedLiterallyAndNotEvaluated() {
        Exchange exchange = createExchangeWithBody("body");
        // a key coming from the header (e.g. inherited from a consumed object name) must be used as-is
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "${sys.user.name}.txt");
        AWS2S3Configuration config = new AWS2S3Configuration();

        assertEquals("${sys.user.name}.txt", AWS2S3Utils.determineKey(exchange, config));
    }

    @Test
    void keyFromConfigurationIsEvaluatedAsSimpleExpression() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader("name", "report");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setKeyName("${header.name}.txt");

        assertEquals("report.txt", AWS2S3Utils.determineKey(exchange, config));
    }

    @Test
    void keyFromHeaderTakesPrecedenceOverConfiguration() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "literal-key");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setKeyName("${header.name}.txt");

        assertEquals("literal-key", AWS2S3Utils.determineKey(exchange, config));
    }

    @Test
    void keyMissingFromHeaderAndConfigurationThrows() {
        Exchange exchange = createExchangeWithBody("body");
        AWS2S3Configuration config = new AWS2S3Configuration();

        assertThrows(IllegalArgumentException.class, () -> AWS2S3Utils.determineKey(exchange, config));
    }

    // ---- determineBucketName ----

    @Test
    void bucketFromOverrideHeaderIsUsedLiterallyAndNotEvaluated() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader(AWS2S3Constants.OVERRIDE_BUCKET_NAME, "${sys.user.name}-bucket");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setBucketName("configured-bucket");

        assertEquals("${sys.user.name}-bucket", AWS2S3Utils.determineBucketName(exchange, config));
    }

    @Test
    void bucketFromConfigurationIsEvaluatedAsSimpleExpression() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader("env", "prod");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setBucketName("bucket-${header.env}");

        assertEquals("bucket-prod", AWS2S3Utils.determineBucketName(exchange, config));
    }

    @Test
    void bucketOverrideHeaderTakesPrecedenceOverConfiguration() {
        Exchange exchange = createExchangeWithBody("body");
        exchange.getIn().setHeader(AWS2S3Constants.OVERRIDE_BUCKET_NAME, "header-bucket");
        AWS2S3Configuration config = new AWS2S3Configuration();
        config.setBucketName("configured-bucket");

        assertEquals("header-bucket", AWS2S3Utils.determineBucketName(exchange, config));
    }

    @Test
    void bucketMissingFromHeaderAndConfigurationThrows() {
        Exchange exchange = createExchangeWithBody("body");
        AWS2S3Configuration config = new AWS2S3Configuration();

        assertThrows(IllegalArgumentException.class, () -> AWS2S3Utils.determineBucketName(exchange, config));
    }
}
