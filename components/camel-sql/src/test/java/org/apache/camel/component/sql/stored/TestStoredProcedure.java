/**
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
package org.apache.camel.component.sql.stored;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestStoredProcedure {

    public static final AtomicLong BATCHFN_CALL_COUNTER = new AtomicLong(0);

    private static final Logger LOG = LoggerFactory.getLogger(TestStoredProcedure.class);


    private TestStoredProcedure() {
    }

    public static void subnumbers(int val1, int val2, int[] ret) {
        LOG.info("calling addnumbers:{} + {}", val1, val2);
        ret[0] = val1 - val2;
    }


    public static void batchfn(String val1) {
        LOG.info("calling batchfn:{}", val1);
        if (val1 == null) {
            throw new IllegalArgumentException("Argument val1 is null!");
        }
        BATCHFN_CALL_COUNTER.incrementAndGet();
    }


    public static void niladic() {
        LOG.info("nilacid called");
    }

}
