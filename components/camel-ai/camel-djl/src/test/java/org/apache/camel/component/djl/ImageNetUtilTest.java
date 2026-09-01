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
package org.apache.camel.component.djl;

import java.util.List;

import ai.djl.modality.Classifications;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageNetUtilTest {

    private Exchange exchangeWithClassName(String className) {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setBody(new Classifications(List.of(className), List.of(1.0)));
        return exchange;
    }

    @Test
    void extractsTheLabelFromAnImageNetClassName() {
        Exchange exchange = exchangeWithClassName("n01440764 tench, Tinca tinca");
        new ImageNetUtil().extractClassName(exchange);
        assertEquals("tench", exchange.getMessage().getBody(String.class));
    }

    @Test
    void classNameWithoutSpaceDoesNotThrow() {
        Exchange exchange = exchangeWithClassName("tench");
        assertDoesNotThrow(() -> new ImageNetUtil().extractClassName(exchange));
        assertEquals("tench", exchange.getMessage().getBody(String.class));
    }
}
