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
package org.apache.camel.component.reactive.streams;

/**
 * Useful constants used in the Camel Reactive Streams component.
 */
public final class ReactiveStreamsConstants {

    public static final String SCHEME = "reactive-streams";
    public static final String SERVICE_PATH = "META-INF/services/org/apache/camel/reactive-streams/";
    public static final String DEFAULT_SERVICE_NAME =  "default-service";

    /**
     * Every exchange consumed by Camel has this header set to indicate if the exchange
     * contains an item (value="onNext"), an error (value="onError") or a completion event (value="onComplete").
     * Errors and completion notification are not forwarded by default.
     */
    public static final String REACTIVE_STREAMS_EVENT_TYPE = "CamelReactiveStreamsEventType";

    public static final String REACTIVE_STREAMS_CALLBACK = "CamelReactiveStreamsCallback";


    private ReactiveStreamsConstants() {
    }

}
