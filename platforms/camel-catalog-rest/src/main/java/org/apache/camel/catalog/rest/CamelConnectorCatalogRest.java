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
package org.apache.camel.catalog.rest;

import java.util.List;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.camel.catalog.connector.CamelConnectorCatalog;
import org.apache.camel.catalog.connector.ConnectorDto;
import org.apache.camel.catalog.connector.DefaultCamelConnectorCatalog;
import org.apache.camel.catalog.maven.DefaultMavenArtifactProvider;
import org.apache.camel.catalog.maven.MavenArtifactProvider;

/**
 * A REST based {@link CamelConnectorCatalog} service as a JAX-RS resource class.
 */
@Api(value = "/camel-connector-catalog", description = "Camel Connector Catalog REST API")
@Path("/camel-connector-catalog")
public class CamelConnectorCatalogRest {

    private CamelConnectorCatalog catalog = new DefaultCamelConnectorCatalog();
    private MavenArtifactProvider maven = new DefaultMavenArtifactProvider();

    public CamelConnectorCatalog getCatalog() {
        return catalog;
    }

    /**
     * To inject an existing {@link CamelConnectorCatalog}
     */
    public void setCatalog(CamelConnectorCatalog catalog) {
        this.catalog = catalog;
    }

    @GET
    @Path("/findConnector")
    @Produces("application/json")
    @ApiOperation(value = "Find all the connectors from the catalog")
    public List<ConnectorDto> findConnector(@ApiParam("Whether to include latest version only")
                                            @QueryParam("latestVersionOnly") boolean latestVersionOnly) {
        return catalog.findConnector(latestVersionOnly);
    }

    @GET
    @Path("/findConnector/{filter}")
    @Produces("application/json")
    @ApiOperation(value = "Find all the connectors from the catalog")
    public List<ConnectorDto> findConnector(@ApiParam("Filter the connector matching by name, description or labels")
                                            @PathParam("filter") String filter,
                                            @ApiParam("Whether to include latest version only")
                                            @QueryParam("latestVersionOnly") boolean latestVersionOnly) {
        return catalog.findConnector(latestVersionOnly);
    }

    @GET
    @Path("/connectorJSon/{groupId}/{artifactId}/{version}")
    @Produces("application/json")
    @ApiOperation(value = "Returns the camel-connector json file for the given connector with the Maven coordinate")
    public String connectorJSon(@ApiParam("Maven groupdId of the connector")
                                @PathParam("groupId") String groupId,
                                @ApiParam("Maven artifactId of the connector")
                                @PathParam("artifactId") String artifactId,
                                @ApiParam("Maven version of the connector")
                                @PathParam("version") String version) {
        return catalog.connectorJSon(groupId, artifactId, version);
    }

    @GET
    @Path("/connectorSchemaJSon/{groupId}/{artifactId}/{version}")
    @Produces("application/json")
    @ApiOperation(value = "Returns the camel-connector-schema json file for the given connector with the Maven coordinate")
    public String connectorSchemaJSon(@ApiParam("Maven groupdId of the connector")
                                      @PathParam("groupId") String groupId,
                                      @ApiParam("Maven artifactId of the connector")
                                      @PathParam("artifactId") String artifactId,
                                      @ApiParam("Maven version of the connector")
                                      @PathParam("version") String version) {
        return catalog.connectorSchemaJSon(groupId, artifactId, version);
    }

    @POST
    @Path("/addMavenRepository/{name}/{url}")
    @ApiOperation(value = "Adds a third party Maven repository to use for downloading Maven artifacts")
    public void addMavenRepository(@ApiParam("The name of the Maven repository") @PathParam("name") String name,
                                   @ApiParam("The URL of the Maven repository") @PathParam("url") String url) {
        maven.addMavenRepository(name, url);
    }

    @POST
    @Path("/addConnectorFromMavenArtifact/{groupId}/{artifactId}/{version}")
    @Produces("application/json")
    @ApiOperation(value = "Downloads the Maven artifact and scan for custom Camel connectors which will be added to the catalog and returns the names of the found connectors")
    public Set<String> addConnectorFromMavenArtifact(@ApiParam("The Maven groupId") @PathParam("groupId") String groupId,
                                                     @ApiParam("The Maven artifactId") @PathParam("artifactId") String artifactId,
                                                     @ApiParam("The Maven version") @PathParam("version") String version) {
        return maven.addArtifactToCatalog(null, catalog, groupId, artifactId, version);
    }

}
