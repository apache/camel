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
package org.apache.camel.support.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RestUtilTest {

    @Test
    public void testRestUtil() {
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType(null, null));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType(null, "*/*"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "*/*"));

        Assertions.assertFalse(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/json,application/xml"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/json, application/xml"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml,application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml, application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml,application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml, application/json"));

        Assertions.assertFalse(RestUtil.isValidOrAcceptedContentType("application/xml", "application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/xml", "application/json,application/xml"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/xml", "application/json, application/xml"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/xml", "application/xml,application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/xml", "application/xml, application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/xml", "application/xml,application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/xml", "application/xml, application/json"));

        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json,application/xml", "application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json,application/xml", "application/xml"));
    }

    @Test
    public void testRestUtilWithParameters() {
        // the parameters of each media type (such as q or charset) are not part of the match
        Assertions.assertTrue(
                RestUtil.isValidOrAcceptedContentType("application/json", "application/xml;q=0.9, application/json"));
        Assertions.assertTrue(RestUtil.isValidOrAcceptedContentType("application/json", "application/json; charset=UTF-8"));
        Assertions.assertFalse(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml;q=0.9, text/plain"));
        // an empty part does not match
        Assertions.assertFalse(RestUtil.isValidOrAcceptedContentType("application/json", "application/xml,"));
    }
}
