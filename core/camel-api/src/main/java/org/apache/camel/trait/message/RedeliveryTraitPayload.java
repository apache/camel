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

package org.apache.camel.trait.message;

/**
 * Some messages can carry redelivery details which might affect routing (i.e; JMS messages). This trait allows
 * implementations to assign a payload that determines the redelivery state for the message.
 */
public enum RedeliveryTraitPayload {
    /**
     * The default redelivery payload, as most messages don't support redeliveries
     **/
    UNDEFINED_REDELIVERY,
    /**
     * When a message supports redelivery, this indicates that this message is in a non-redelivery state
     */
    NON_REDELIVERY,

    /**
     * When a message supports redelivery, this indicates that this message is in a redelivery state
     */
    IS_REDELIVERY,
}
