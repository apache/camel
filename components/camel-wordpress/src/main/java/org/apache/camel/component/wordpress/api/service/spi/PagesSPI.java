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

import java.util.Date;
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

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.Order;
import org.apache.camel.component.wordpress.api.model.Page;
import org.apache.camel.component.wordpress.api.model.PageOrderBy;
import org.apache.camel.component.wordpress.api.model.PublishableStatus;

/**
 * Describes the Wordpress Pages API.
 * 
 * @see <a href= "https://developer.wordpress.org/rest-api/reference/pages/">Pages API Reference</a>
 * @since 0.0.1
 */
@Path("/wp")
public interface PagesSPI {

    // @formatter:off
    @GET
    @Path("/v{apiVersion}/pages")
    @Produces(MediaType.APPLICATION_JSON)
    List<Page> list(@PathParam("apiVersion") String apiVersion, @QueryParam("context") Context context, @QueryParam("page") Integer page, @QueryParam("per_page") Integer perPage,
                    @QueryParam("search") String search, @QueryParam("after") Date after, @QueryParam("author") List<Integer> author, @QueryParam("author_exclude") List<Integer> authorExclude,
                    @QueryParam("before") Date before, @QueryParam("exclude") List<Integer> exclude, @QueryParam("include") List<Integer> include, @QueryParam("menu_order") Integer menuOrder,
                    @QueryParam("offset") List<Integer> offset, @QueryParam("order") Order order, @QueryParam("orderby") PageOrderBy orderBy, @QueryParam("parent") Integer parent,
                    @QueryParam("parent_exclude") Integer parentExclude, @QueryParam("slug") List<String> slug, @QueryParam("status") PublishableStatus status, @QueryParam("filter") String filter);

    // @formatter:off
    @GET
    @Path("/v{apiVersion}/pages/{pageId}")
    @Produces(MediaType.APPLICATION_JSON)
    Page retrieve(@PathParam("apiVersion") String apiVersion, @PathParam("pageId") int pageId, @QueryParam("context") Context context, @QueryParam("password") String password);

    // @formatter:on
    @POST
    @Path("/v{apiVersion}/pages")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Page create(@PathParam("apiVersion") String apiVersion, Page page);

    // @formatter:off
    @POST
    @Path("/v{apiVersion}/pages/{pageId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Page update(@PathParam("apiVersion") String apiVersion, @PathParam("pageId") int pageId, Page page);

    @DELETE
    @Path("/v{apiVersion}/pages/{pageId}")
    Page delete(@PathParam("apiVersion") String apiVersion, @PathParam("pageId") int pageId, @QueryParam("force") boolean force);

}
