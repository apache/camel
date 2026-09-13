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
package org.apache.camel.component.timer;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class TimerConstants {

    @Metadata(description = "The fired time", javaType = "Date")
    public static final String HEADER_FIRED_TIME = Exchange.TIMER_FIRED_TIME;
    @Metadata(description = "The counter of the timer, starting at 1 for the first fire and incremented on each fire",
              javaType = "long")
    public static final String HEADER_TIMER_COUNTER = Exchange.TIMER_COUNTER;
    @Metadata(description = "The name of the timer", javaType = "String")
    public static final String HEADER_TIMER_NAME = Exchange.TIMER_NAME;
    @Metadata(description = "The period of the timer in millis", javaType = "long")
    public static final String HEADER_TIMER_PERIOD = Exchange.TIMER_PERIOD;
    @Metadata(description = "The time the timer was scheduled to fire (its trigger time)", javaType = "Date")
    public static final String HEADER_TIMER_TIME = Exchange.TIMER_TIME;
    @Metadata(description = "The timestamp of the message", javaType = "long")
    public static final String HEADER_MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;

    private TimerConstants() {

    }
}
