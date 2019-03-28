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
package org.apache.camel.component.wordpress.api.service.spi;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.Taxonomy;

@Path("/wp")
public interface TaxonomySPI {

    // @formatter:off
    @GET
    @Path("/v{apiVersion}/taxonomies")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Taxonomy> list(@PathParam("apiVersion") String apiVersion, @QueryParam("context") Context context, @QueryParam("type") String postType);

    @GET
    @Path("/v{apiVersion}/taxonomies/{taxonomy}")
    @Produces(MediaType.APPLICATION_JSON)
    Taxonomy retrieve(@PathParam("apiVersion") String apiVersion, @QueryParam("context") Context context, @PathParam("taxonomy") String taxonomy);
}
