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
package org.apache.camel.spring.interceptor.route;

import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;

public class DataSourceSpringRouteBuilder extends SpringRouteBuilder {
    
    public void configure() throws Exception {
        
        SpringTransactionPolicy required = bean(SpringTransactionPolicy.class, "PROPAGATION_REQUIRED");

        
        // useTransactionErrorHandler is only used for unit testing to reuse code
        // for doing a 2nd test without this transaction error handler, so ignore
        // this. For spring based transaction, end users are encouraged to use the
        // transaction error handler instead of the default DeadLetterChannel.
        errorHandler(transactionErrorHandler(required).
            // notice that the builder has builder methods for chained configuration
            delay(5 * 1000L));
        

       
        // set the required policy for this route
        from("direct:okay").policy(required).
            setBody(constant("Tiger in Action")).beanRef("bookService").
            setBody(constant("Elephant in Action")).beanRef("bookService");

        // set the required policy for this route
        from("direct:fail").policy(required).
            setBody(constant("Tiger in Action")).beanRef("bookService").
            setBody(constant("Donkey in Action")).beanRef("bookService");
        
    }
}


