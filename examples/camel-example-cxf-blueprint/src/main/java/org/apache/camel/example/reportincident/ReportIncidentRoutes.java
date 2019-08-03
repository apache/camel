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
package org.apache.camel.example.reportincident;

import org.apache.camel.builder.RouteBuilder;

/**
 * Our routes that we can build using Camel DSL as we extend the RouteBuilder class.
 * <p/>
 * In the configure method we have all kind of DSL methods we use for expressing our routes.
 */
public class ReportIncidentRoutes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // webservice responses
        OutputReportIncident ok = new OutputReportIncident();
        ok.setCode("OK");

        OutputReportIncident accepted = new OutputReportIncident();
        accepted.setCode("Accepted");

        from("cxf:bean:reportIncident")
            .convertBodyTo(InputReportIncident.class)
            .wireTap("file://target/inbox/?fileName=request-${date:now:yyyy-MM-dd-HHmmssSSS}")
            .choice().when(simple("${body.givenName} == 'Claus'"))
                .transform(constant(ok))
            .otherwise()
                .transform(constant(accepted));
    }
}
