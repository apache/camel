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

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.camel.component.wordpress.api.model.Category;
import org.apache.camel.component.wordpress.api.model.CategoryOrderBy;
import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.Order;

/**
 * Describes the Categories Wordpress API
 * 
 * @see <a href= "https://developer.wordpress.org/rest-api/reference/categories/">Categories API Reference</a>
 * @since 0.0.1
 */
@Path("/wp")
public interface CategoriesSPI {

    // @formatter:off
    @GET
    @Path("/v{apiVersion}/categories")
    @Produces(MediaType.APPLICATION_JSON)
    List<Category> list(@PathParam("apiVersion") String apiVersion, @QueryParam("context") Context context, @QueryParam("page") Integer page, @QueryParam("per_page") Integer perPage,
                        @QueryParam("search") String search, @QueryParam("exclude") List<Integer> exclude, @QueryParam("include") List<Integer> include, @QueryParam("order") Order order,
                        @QueryParam("orderby") CategoryOrderBy orderBy, @QueryParam("hide_empty") Boolean hideEmpty, @QueryParam("parent") Integer parent, @QueryParam("post") Integer post,
                        @QueryParam("slug") String slug);

    @GET
    @Path("/v{apiVersion}/categories/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Category retrieve(@PathParam("apiVersion") String apiVersion, @PathParam("id") int categoryId, @QueryParam("context") Context context);

    @POST
    @Path("/v{apiVersion}/categories/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Category create(@PathParam("apiVersion") String apiVersion, Category category);

    @POST
    @Path("/v{apiVersion}/categories/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Category update(@PathParam("apiVersion") String apiVersion, @PathParam("id") int categoryId, Category category);

    @DELETE
    @Path("/v{apiVersion}/categories/{id}")
    Category delete(@PathParam("apiVersion") String apiVersion, @PathParam("id") int categoryId, @QueryParam("force") boolean force);

}
