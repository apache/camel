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
package org.apache.camel.component.resteasy.test.beans;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/simpleService")
public class SimpleService {

    @GET
    @Path("/getMsg")
    public Response getMessage() {
        return Response.status(200).entity("Message1 from Rest service").build();
    }

    @GET
    @Path("/getMsg2")
    public Response getMessage2() {
        return Response.status(200).entity("Message2 from Rest service").build();
    }

    @GET
    @Path("/getMsg3")
    public Response getMessage3() {
        return Response.status(200).entity("Message3 from Rest service").build();
    }

    @GET
    @Path("/match/prefix")
    public Response matchOnUri() {
        return Response.status(200).entity("Prefix").build();
    }

}
