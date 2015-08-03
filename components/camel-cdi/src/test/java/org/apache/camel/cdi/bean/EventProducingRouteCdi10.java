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
package org.apache.camel.cdi.bean;

import javax.enterprise.context.ApplicationScoped;

import javax.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiEventEndpoint;
import org.apache.camel.cdi.pojo.EventPayloadInteger;
import org.apache.camel.cdi.pojo.EventPayloadString;
import org.apache.camel.cdi.qualifier.BarQualifier;
import org.apache.camel.cdi.qualifier.FooQualifier;

@ApplicationScoped
public class EventProducingRouteCdi10 extends RouteBuilder {

    @Inject
    private CdiEventEndpoint<Object> objectCdiEventEndpoint;

    @Inject
    private CdiEventEndpoint<String> stringCdiEventEndpoint;

    @Inject
    private CdiEventEndpoint<EventPayloadString> stringPayloadCdiEventEndpoint;

    @Inject
    private CdiEventEndpoint<EventPayloadInteger> integerPayloadCdiEventEndpoint;

    @Inject
    @FooQualifier
    private CdiEventEndpoint<Long> fooQualifierCdiEventEndpoint;

    @Inject
    @BarQualifier
    private CdiEventEndpoint<Long> barQualifierCdiEventEndpoint;

    @Override
    public void configure() {
        from("direct:produceObject").to(objectCdiEventEndpoint);

        from("direct:produceString").to(stringCdiEventEndpoint);

        from("direct:produceStringPayload").to(stringPayloadCdiEventEndpoint);

        from("direct:produceIntegerPayload").to(integerPayloadCdiEventEndpoint);

        from("direct:produceFooQualifier").to(fooQualifierCdiEventEndpoint);

        from("direct:produceBarQualifier").to(barQualifierCdiEventEndpoint);
    }
}
