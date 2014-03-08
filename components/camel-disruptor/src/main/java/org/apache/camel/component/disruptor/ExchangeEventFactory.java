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

package org.apache.camel.component.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * This class is used by the Disruptor to create new instanced of an {@link ExchangeEvent} to fill up a ringbuffer
 * with mutable object references.
 */
class ExchangeEventFactory implements EventFactory<ExchangeEvent> {

    public static final ExchangeEventFactory INSTANCE = new ExchangeEventFactory();

    @Override
    public ExchangeEvent newInstance() {
        return new ExchangeEvent();
    }
}
