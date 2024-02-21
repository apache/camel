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
package org.apache.camel.impl.event;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelEvent;

public class CamelContextReloadingEvent extends AbstractContextEvent
        implements CamelEvent.CamelContextReloadingEvent {

    private static final long serialVersionUID = 7966471393751298717L;

    private final Object action;

    public CamelContextReloadingEvent(CamelContext context, Object action) {
        super(context);
        this.action = action;
    }

    /**
     * The action which triggered reloading
     */
    public Object getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "Reloading CamelContext: " + getContext().getName();
    }
}
