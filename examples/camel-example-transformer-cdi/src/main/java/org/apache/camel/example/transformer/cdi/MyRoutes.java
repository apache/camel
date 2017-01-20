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
package org.apache.camel.example.transformer.cdi;

import org.apache.camel.builder.RouteBuilder;

/**
 * Configures all our Camel routes, components, endpoints and beans
 */
public class MyRoutes extends RouteBuilder {

    @Override
    public void configure() {
        transformer()
            .fromType("xml:MyRequest")
            .toType("xml:MyResponse")
            .withUri("xslt:transform.xsl");
        
        from("timer:foo?period=5000").id("timer-route")
            .log("start -->")
            .setBody(constant("<MyRequest>foobar</MyRequest>"))
            .log("--> Sending:[${body}]")
            .to("direct:a")
            .log("--> Received:[${body}]")
            .log("<-- end");
        
        from("direct:a").id("xslt-route")
            .inputType("xml:MyRequest")
            .outputType("xml:MyResponse")
            .log("----> Received:[${body}]");
    }

}
