## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;

/**
 * A Camel Java8 DSL Router
 */
public class MyRouteBuilder extends RouteBuilder {
    private static final Object[] OBJECTS = new Object[]{
        "A string",
        new Integer(1),
        new Double(1.0)
    };

    private int index;

    /**
     * Let's configure the Camel routing rules using Java code...
     */
    public void configure() {

        // here is a sample which set a raondom body then performs content
        // based routing on the message using method references
        from("timer:simple?period=1000")
            .process()
                .message(m -> m.setHeader("index", index++ % 3))
            .transform()
                .message(this::randomBody)
            .choice()
                .when()
                    .body(String.class::isInstance)
                    .log("Got a String body")
                .when()
                    .body(Integer.class::isInstance)
                    .log("Got an Integer body")
                .when()
                    .body(Double.class::isInstance)
                    .log("Got a Double body")
                .otherwise()
                    .log("Other type message");
    }

    private Object randomBody(Message m) {
        return OBJECTS[m.getHeader("index", Integer.class)];
    }
}
