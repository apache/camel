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
package org.apache.camel.component.gae.auth;

import org.apache.camel.builder.RouteBuilder;

public class GAuthRouteBuilder extends RouteBuilder {

    public static final String AUTHORIZE_URI_PART =
        "authorize" 
            + "?scope=http%3A%2F%2Ftest.example.org%2Fscope" 
            + "&callback=http%3A%2F%2Ftest.example.org%2Fhandler"
            + "&serviceRef=#testService";

    public static final String UPGRADE_URI_PART = 
        "upgrade?serviceRef=#testService";
        
    @Override
    public void configure() throws Exception {
        // Endpoints that use a consumer secret for signing requests
        from("direct:input1-cs").to("gauth-cs:" + AUTHORIZE_URI_PART);
        from("direct:input2-cs").to("gauth-cs:" + UPGRADE_URI_PART);
        // Endpoints that use a private key for signing requests
        from("direct:input1-pk").to("gauth-pk:" + AUTHORIZE_URI_PART);
        from("direct:input2-pk").to("gauth-pk:" + UPGRADE_URI_PART);
    }

}
