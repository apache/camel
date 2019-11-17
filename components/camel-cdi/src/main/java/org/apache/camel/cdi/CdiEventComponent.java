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
package org.apache.camel.cdi;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

@Named("cdi-event")
@ApplicationScoped
/* package-private */ class CdiEventComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
        /* The CDI event endpoint URI follows the format hereafter:
        
        cdi-event://PayloadType<T1,...,Tn>[?qualifiers=QualifierType1[,...[,QualifierTypeN]...]]

        with the authority PayloadType (respectively the QualifierType) being the URI escaped fully
        qualified name of the payload (respectively qualifier) raw type followed by the type parameters
        section delimited by angle brackets for payload parameterized type.

        Which leads to unfriendly URIs, e.g.:

        cdi-event://org.apache.camel.cdi.se.pojo.EventPayload%3Cjava.lang.Integer%3E?qualifiers=org.apache.camel.cdi.se.qualifier.FooQualifier%2Corg.apache.camel.cdi.se.qualifier.BarQualifier

        From the conceptual standpoint, that shows the high impedance between the typesafe nature of CDI
        and the dynamic nature of the Camel component model.

        From the implementation standpoint, that would prevent efficient binding between the endpoint
        instances and observer methods as the CDI container doesn't have any ways of discovering the
        Camel context model during the deployment phase.
        */
        throw new UnsupportedOperationException("Creating CDI event endpoint isn't supported. Use @Inject " + CdiEventEndpoint.class.getSimpleName() + " instead");
    }
}
