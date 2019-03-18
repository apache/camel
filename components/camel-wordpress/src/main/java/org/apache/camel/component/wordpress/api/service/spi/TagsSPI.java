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

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.Order;
import org.apache.camel.component.wordpress.api.model.Tag;
import org.apache.camel.component.wordpress.api.model.TagOrderBy;

/**
 * Describes the Tags Wordpress API
 * 
 * @see <a href= "https://developer.wordpress.org/rest-api/reference/tags/">Tags API Reference</a>
 * @since 0.0.1
 */
@Path("/wp")
public interface TagsSPI {

    // @formatter:off
    @GET
    @Path("/v{apiVersion}/tags")
    @Produces(MediaType.APPLICATION_JSON)
    List<Tag> list(@PathParam("apiVersion") String apiVersion, @QueryParam("context") Context context, @QueryParam("page") Integer page, @QueryParam("per_page") Integer perPage,
                   @QueryParam("search") String search, @QueryParam("exclude") List<Integer> exclude, @QueryParam("include") List<Integer> include, @QueryParam("offset") List<Integer> offset,
                   @QueryParam("order") Order order, @QueryParam("orderby") TagOrderBy orderBy, @QueryParam("hide_empty") Boolean hideEmpty, @QueryParam("post") Integer post,
                   @QueryParam("slug") String slug);

    @GET
    @Path("/v{apiVersion}/tags/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Tag retrieve(@PathParam("apiVersion") String apiVersion, @PathParam("id") Integer id, @QueryParam("context") Context context);

    @POST
    @Path("/v{apiVersion}/tags")
    Tag create(@PathParam("apiVersion") String apiVersion, Tag tag);

    @POST
    @Path("/v{apiVersion}/tags/{id}")
    Tag update(@PathParam("apiVersion") String apiVersion, @PathParam("id") Integer id, Tag tag);

    @DELETE
    @Path("/v{apiVersion}/tags/{id}")
    Tag delete(@PathParam("apiVersion") String apiVersion, @PathParam("id") Integer id, @QueryParam("force") boolean force);
}
