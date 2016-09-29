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
package org.apache.camel.component.servicenow;

import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;

public class ServiceNowDispatcher {
    private final Predicate<Exchange> predicate;
    private final Processor delegate;

    public ServiceNowDispatcher(Predicate<Exchange> predicate, Processor delegate) {
        this.predicate = ObjectHelper.notNull(predicate, "predicate");
        this.delegate = ObjectHelper.notNull(delegate, "delegate");
    }

    public boolean match(Exchange exchange) {
        return predicate.test(exchange);
    }

    public void process(Exchange exchange) throws Exception {
        delegate.process(exchange);
    }

    // ********************
    // Helpers
    // ********************

    public static ServiceNowDispatcher on(final String action, final String subject, final Processor delegate) {
        return new ServiceNowDispatcher(e -> matches(e.getIn(), action, subject), delegate);
    }

    public static boolean matches(Message in, String action, final String subject) {
        return ObjectHelper.equal(action, in.getHeader(ServiceNowConstants.ACTION, String.class), true)
            && ObjectHelper.equal(subject, in.getHeader(ServiceNowConstants.ACTION_SUBJECT, String.class), true);
    }
}
