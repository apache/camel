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
 * Message traits are runtime traits that can be associated with a message (for instance, the redelivery state, a data
 * type, etc). This is specifically for internal usage of Camel and not a public API.
 */
public enum MessageTrait {
    /**
     * The redelivery trait for the message. See {@link RedeliveryTraitPayload}.
     */
    REDELIVERY,
    /**
     * Whether the message can store a data type. This carries the payload associated with the API specified in
     * {@link org.apache.camel.spi.DataTypeAware}.
     */
    DATA_AWARE;
}
