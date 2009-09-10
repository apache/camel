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
package org.apache.camel.management.event;

import java.util.EventObject;

import org.apache.camel.CamelContext;

/**
 * @version $Revision$
 */
public class ServiceStopFailureEvent extends EventObject {

    private CamelContext context;
    private Object service;
    private Exception cause;

    public ServiceStopFailureEvent(CamelContext context, Object service, Exception cause) {
        super(service);
        this.context = context;
        this.service = service;
        this.cause = cause;
    }

    public CamelContext getContext() {
        return context;
    }

    public Object getService() {
        return service;
    }

    public Exception getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "Failure to stop service: " + service + " due to " + cause.getMessage();
    }

}