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
package org.apache.camel.dataformat.csv.component;

import org.apache.camel.builder.RouteBuilder;

public class DataFormatComponentRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // use the dataformat: component instead of the unmarshal() DSL; it must honor the global
        // camel.dataformat.csv.* configuration from application.properties (CAMEL-23772)
        from("timer:tick?repeatCount=1")
                .setBody().constant("One   \r\nTwo   \r\nThree   \r\n")
                .to("dataformat:csv:unmarshal")
                .to("mock:result");
    }
}
