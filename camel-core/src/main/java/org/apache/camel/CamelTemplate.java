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
package org.apache.camel;

import org.apache.camel.impl.DefaultProducerTemplate;

/**
 * A client helper object (named like Spring's TransactionTemplate & JmsTemplate
 * et al) for working with Camel and sending {@link Message} instances in an
 * {@link Exchange} to an {@link Endpoint}.
 *
 * @version $Revision$
 * @deprecated use {@link ProducerTemplate} instead, can be created using {@link org.apache.camel.CamelContext#createProducerTemplate()}. Will be removed in Camel 2.0
 */
@Deprecated
public class CamelTemplate<E extends Exchange> extends DefaultProducerTemplate<E> {

    public CamelTemplate(CamelContext context) {
        super(context);
    }

    public CamelTemplate(CamelContext context, Endpoint defaultEndpoint) {
        super(context, defaultEndpoint);
    }
}
