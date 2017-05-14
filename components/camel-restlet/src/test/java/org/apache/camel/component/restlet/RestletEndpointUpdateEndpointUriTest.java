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
package org.apache.camel.component.restlet;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

/**
 * @version
 */
public class RestletEndpointUpdateEndpointUriTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testUpdateEndpointUri() throws Exception {
        RestletComponent component = context.getComponent("restlet", RestletComponent.class);

        Endpoint endpoint = component.createEndpoint("restlet:http://localhost:9090/users/user");
        assertEquals("The restlet endpoint didn't update it's URI properly", "restlet://http://localhost:9090/users/user?restletMethod=GET",
                     endpoint.getEndpointUri());

        endpoint = component.createEndpoint("restlet:http://localhost:9090/users/user?restletMethod=post");
        assertEquals("The restlet endpoint didn't update it's URI properly", "restlet://http://localhost:9090/users/user?restletMethod=POST",
                     endpoint.getEndpointUri());

        endpoint = component.createEndpoint("restlet:http://localhost:9090/users/user?restletMethods=post");
        assertEquals("The restlet endpoint didn't update it's URI properly", "restlet://http://localhost:9090/users/user?restletMethods=POST",
                     endpoint.getEndpointUri());

        endpoint = component.createEndpoint("restlet:http://localhost:9090/users/user?restletMethods=lock,head");
        assertEquals("The restlet endpoint didn't update it's URI properly", "restlet://http://localhost:9090/users/user?restletMethods=LOCK,HEAD",
                     endpoint.getEndpointUri());

        endpoint = component.createEndpoint("restlet:http://localhost:9090/users/user?restletMethods=proppatch,mkcol,propfind");
        assertEquals("The restlet endpoint didn't update it's URI properly", "restlet://http://localhost:9090/users/user?restletMethods=PROPPATCH,MKCOL,PROPFIND",
                     endpoint.getEndpointUri());

        endpoint = component.createEndpoint("restlet:http://localhost:9090/users/user?restletMethods=delete,copy,options,connect");
        assertEquals("The restlet endpoint didn't update it's URI properly", "restlet://http://localhost:9090/users/user?restletMethods=DELETE,COPY,OPTIONS,CONNECT",
                     endpoint.getEndpointUri());
    }

}