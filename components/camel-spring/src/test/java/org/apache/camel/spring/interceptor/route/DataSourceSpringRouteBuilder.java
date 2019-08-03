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
package org.apache.camel.spring.interceptor.route;

import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;

public class DataSourceSpringRouteBuilder extends SpringRouteBuilder {
    
    @Override
    public void configure() throws Exception {
        // get the required policy
        SpringTransactionPolicy required = lookup("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);

        // For spring based transaction, end users are encouraged to use the
        // transaction error handler instead of the default DeadLetterChannel.
        errorHandler(transactionErrorHandler(required));

        // set the required policy for this route
        from("direct:okay").policy(required).
            setBody(constant("Tiger in Action")).bean("bookService").
            setBody(constant("Elephant in Action")).bean("bookService");

        // set the required policy for this route
        from("direct:fail").policy(required).
            setBody(constant("Tiger in Action")).bean("bookService").
            setBody(constant("Donkey in Action")).bean("bookService");
    }

}


