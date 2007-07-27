/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.etl;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision: 1.1 $
 */
public class EtlRoutes extends RouteBuilder {
    private static final transient Log log = LogFactory.getLog(EtlRoutes.class);

    public void configure() throws Exception {
        from("file:src/data?noop=true").convertBodyTo(PersonDocument.class).to("jpa:org.apache.camel.example.etl.CustomerEntity");

/*
        from("file:src/data?noop=true").convertBodyTo(PersonDocument.class).process(new Processor() {
            public void process(Exchange exchange) throws Exception {
                System.out.println("Body is: " + exchange.getIn().getBody());
                log.info("Body is: " + exchange.getIn().getBody());
            }
        });
*/
    }
}
