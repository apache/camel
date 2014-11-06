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
package org.apache.camel.example.etl;

import org.apache.camel.Exchange;
import org.apache.camel.spring.SpringRouteBuilder;

import static org.apache.camel.language.juel.JuelExpression.el;

/**
 * @version 
 */
// START SNIPPET: example
public class EtlRoutes extends SpringRouteBuilder {
    public void configure() throws Exception {

        from("file:src/data?noop=true")
            .convertBodyTo(PersonDocument.class)
            .to("jpa:org.apache.camel.example.etl.CustomerEntity");

        // the following will dump the database to files
        from("jpa:org.apache.camel.example.etl.CustomerEntity?consumer.initialDelay=3000&delay=3000&consumeDelete=false&consumeLockEntity=false")
            .setHeader(Exchange.FILE_NAME, el("${in.body.userName}.xml"))
            .to("file:target/customers");
    }
}
// END SNIPPET: example
