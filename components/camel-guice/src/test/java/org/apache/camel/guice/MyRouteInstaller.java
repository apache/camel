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
package org.apache.camel.guice;

import org.apache.camel.builder.RouteBuilder;

/**
 * Lets use a RouteBuilder as an installer of other routes
 * where we can let the CamelContext resolve endpoints for us
 */
public class MyRouteInstaller extends RouteBuilder {

    public void configure() throws Exception {
        // TODO we could register explicit endpoints here too if we want

        // lets add some other route builders
        includeRoutes(new MyConfigurableRoute(endpoint("direct:a"), endpoint("direct:b")));
        includeRoutes(new MyConfigurableRoute(endpoint("direct:c"), endpoint("direct:d")));

    }
}
