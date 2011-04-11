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
package org.apache.camel.example.reportincident;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

/**
 * Our routes that we can build using Camel DSL as we extend the RouteBuilder class.
 * <p/>
 * In the configure method we have all kind of DSL methods we use for expressing our routes.
 */
public class ReportIncidentRoutes extends RouteBuilder {
    
    public void configure() throws Exception {
        // webservice response for OK
        OutputReportIncident ok = new OutputReportIncident();
        ok.setCode("0");

        from("cxf:bean:reportIncident")
            .convertBodyTo(InputReportIncident.class) // TODO remove?
            .setHeader(Exchange.FILE_NAME, constant("request-${date:now:yyyy-MM-dd-HHmmssSSS}"))
            .to("file://target/inbox/")
            .transform(constant(ok));
    }
}