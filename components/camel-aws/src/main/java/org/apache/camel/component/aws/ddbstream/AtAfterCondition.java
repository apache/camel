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
package org.apache.camel.component.aws.ddbstream;

import java.math.BigInteger;

interface AtAfterCondition {

    /**
     * @return true if sequenceNumber is (at,after) the endpointSequenceNumber.
     */
    boolean matches(BigInteger endpointSequenceNumber, BigInteger sequenceNumber);

    static enum Conditions implements AtAfterCondition {
        AFTER() {
            @Override
            public boolean matches(BigInteger endpointSequenceNumber, BigInteger sequenceNumber) {
                return endpointSequenceNumber.compareTo(sequenceNumber) < 0;
            }
        },

        AT() {
            @Override
            public boolean matches(BigInteger endpointSequenceNumber, BigInteger sequenceNumber) {
                return endpointSequenceNumber.compareTo(sequenceNumber) <= 0;
            }
        }
        // TODO rename to LT/LTEQ/EQ/GTEQ/GT
    }
}
