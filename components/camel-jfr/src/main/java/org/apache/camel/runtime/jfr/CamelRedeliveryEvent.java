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

@Name("org.apache.camel.redelivery")
@Category({ "Camel Application", "Runtime" })
@Label("Camel Redelivery")
@Description("Emitted on each redelivery attempt")
@StackTrace(false)
public class CamelRedeliveryEvent extends Event {

    @Label("Exchange Id")
    public String exchangeId;
    @Label("Route Id")
    public String routeId;
    @Label("Attempt")
    public int attempt;
    @Label("Max Attempts")
    public int maxAttempts;
}
