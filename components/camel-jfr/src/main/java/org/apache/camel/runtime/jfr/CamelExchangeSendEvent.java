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
package org.apache.camel.runtime.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Spans the time taken to send an exchange to an endpoint. The endpoint URI is sanitized, so credentials in the URI are
 * not recorded.
 *
 * @since 4.22
 */
@Name("org.apache.camel.exchange.send")
@Category({ "Camel Application", "Runtime" })
@Label("Camel Exchange Send")
@Description("Time to send an exchange to an endpoint")
@StackTrace(false)
public class CamelExchangeSendEvent extends Event {

    @Label("Exchange Id")
    public String exchangeId;
    @Label("Endpoint Uri")
    public String endpointUri;
    @Label("Failed")
    public boolean failed;
}
