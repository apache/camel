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
package org.apache.camel.component.reactive.streams.api;

import org.apache.camel.CamelContext;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConstants;
import org.apache.camel.util.ObjectHelper;

/**
 * This is the main entry-point for getting Camel streams associate to reactive-streams endpoints.
 *
 * It allows to retrieve the {@link CamelReactiveStreamsService} to access Camel streams.
 */
public final class CamelReactiveStreams {
    private CamelReactiveStreams() {
    }

    public static CamelReactiveStreamsService get(CamelContext context) {
        ReactiveStreamsComponent component = context.getComponent(ReactiveStreamsConstants.SCHEME, ReactiveStreamsComponent.class);

        return ObjectHelper.notNull(component.getReactiveStreamsService(), "ReactiveStreamsService");
    }
}
