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

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.builder.endpoint.dsl.FileEndpointBuilderFactory;

/**
 * A Camel Java DSL Router
 */
public class MyRouteBuilder extends EndpointRouteBuilder {

    /**
     * Let's configure the Camel routing rules using Java & Endpoint DSLs
     */
    public void configure() {

        //This route has a timer component 'myTimer', that produces tick events, every 5 seconds, at most 10 times.
        //At each tick, we write a message to an output file named 'timer.log', in the 'target' directory.
        //You'll notice the usage of strongly typed methods from Endpoint DSL, like 'timer', 'period','fileExist' etc.
        from(timer("myTimer").period(5_000).repeatCount(10))
                .transform()
                .simple("Timer: ${in.header.CamelTimerName} fired at: ${in.header.CamelTimerFiredTime}") //Here we use simple expression to access exchange properties
                .log("${body}")
                .to(file("target")
                        .fileName("timer.log")
                        .fileExist(FileEndpointBuilderFactory.GenericFileExist.Append) //If the file exists, append to it
                        .appendChars("\n")
                );
    }
}
