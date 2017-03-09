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
import java.util.Map;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.maven.DefaultMavenArtifactProvider;
import org.apache.camel.catalog.maven.MavenArtifactProvider;

/**
 * A REST based {@link CamelCatalog} service as a JAX-RS resource class.
 */
@Api(value = "/camel-catalog", description = "Camel Catalog REST API")
@Path("/camel-catalog")
public class CamelCatalogRest {

    private CamelCatalog catalog = new DefaultCamelCatalog(true);
    private MavenArtifactProvider maven = new DefaultMavenArtifactProvider();

    public CamelCatalog getCatalog() {
        return catalog;
    }

    /**
     * To inject an existing {@link CamelCatalog}
     */
    public void setCatalog(CamelCatalog catalog) {
        this.catalog = catalog;
    }

    @GET
    @Path("/catalogVersion")
    @Produces("text/plain")
    @ApiOperation(value = "The version of this Camel Catalog")
    public String getCatalogVersion() {
        return catalog.getCatalogVersion();
    }

    @GET
    @Path("/findComponentNames")
    @Produces("application/json")
    @ApiOperation(value = "Find all the component names from the Camel catalog")
    public List<String> findComponentNames() {
        return catalog.findComponentNames();
    }

    @GET
    @Path("/findDataFormatNames")
    @Produces("application/json")
    @ApiOperation(value = "Find all the data format names from the Camel catalog")
    public List<String> findDataFormatNames() {
        return catalog.findDataFormatNames();
    }

    @GET
    @Path("/findLanguageNames")
    @Produces("application/json")
    @ApiOperation(value = "Find all the language names from the Camel catalog")
    public List<String> findLanguageNames() {
        return catalog.findLanguageNames();
    }

    @GET
    @Path("/findModelNames")
    @Produces("application/json")
    @ApiOperation(value = "Find all the model (EIP) names from the Camel catalog")
    public List<String> findModelNames() {
        return catalog.findModelNames();
    }

    @GET
    @Path("/findComponentNames/{filter}")
    @Produces("application/json")
    @ApiOperation(value = "Find all the component names from the Camel catalog that matches the label")
    public List<String> findComponentNames(@ApiParam("Filter used to only return component names that matches by their labels")
                                           @PathParam("filter") String filter) {
        return catalog.findComponentNames(filter);
    }

    @GET
    @Path("/findDataFormatNames/{filter}")
    @Produces("application/json")
    @ApiOperation(value = "Find all the data format names from the Camel catalog that matches the label")
    public List<String> findDataFormatNames(@ApiParam("Filter used to only return data format names that matches by their labels")
                                            @PathParam("filter") String filter) {
        return catalog.findDataFormatNames(filter);
    }

    @GET
    @Path("/findLanguageNames/{filter}")
    @Produces("application/json")
    @ApiOperation(value = "Find all the language names from the Camel catalog that matches the label")
    public List<String> findLanguageNames(@ApiParam("Filter used to only return language names that matches by their labels")
                                          @PathParam("filter") String filter) {
        return catalog.findLanguageNames(filter);
    }

    @GET
    @Path("/findModelNames/{filter}")
    @Produces("application/json")
    @ApiOperation(value = "Find all the model (EIP) names from the Camel catalog that matches the label")
    public List<String> findModelNames(@ApiParam("Filter used to only return model (EIP) names that matches by their labels")
                                       @PathParam("filter") String filter) {
        return catalog.findModelNames(filter);
    }

    @GET
    @Path("/componentJSonSchema/{name}")
    @Produces("application/json")
    @ApiOperation(value = "Returns the component information as JSon format")
    public String componentJSonSchema(@ApiParam(value = "The name of the component", required = true)
                                      @PathParam("name") String name) {
        return catalog.componentJSonSchema(name);
    }

    @GET
    @Path("/dataFormatJSonSchema/{name}")
    @Produces("application/json")
    @ApiOperation(value = "Returns the data format information as JSon format")
    public String dataFormatJSonSchema(@ApiParam(value = "The name of the data format", required = true)
                                       @PathParam("name") String name) {
        return catalog.dataFormatJSonSchema(name);
    }

    @GET
    @Path("/languageJSonSchema/{name}")
    @Produces("application/json")
    @ApiOperation(value = "Returns the language information as JSon format")
    public String languageJSonSchema(@ApiParam(value = "The name of the language", required = true)
                                     @PathParam("name") String name) {
        return catalog.languageJSonSchema(name);
    }

    @GET
    @Path("/modelJSonSchema/{name}")
    @Produces("application/json")
    @ApiOperation(value = "Returns the model (EIP) information as JSon format")
    public String modelJSonSchema(@ApiParam(value = "The name of the model (EIP)", required = true)
                                  @PathParam("name") String name) {
        return catalog.modelJSonSchema(name);
    }

    @GET
    @Path("/componentAsciiDoc/{name}")
    @Produces("text/plain")
    @ApiOperation(value = "Returns the component documentation as Ascii doc format")
    public String componentAsciiDoc(@ApiParam(value = "The name of the component", required = true)
                                    @PathParam("name") String name) {
        return catalog.componentAsciiDoc(name);
    }

    @GET
    @Path("/dataFormatAsciiDoc/{name}")
    @Produces("text/plain")
    @ApiOperation(value = "Returns the data format documentation as Ascii doc format")
    public String dataFormatAsciiDoc(@ApiParam(value = "The name of the data format", required = true)
                                     @PathParam("name") String name) {
        return catalog.dataFormatAsciiDoc(name);
    }

    @GET
    @Path("/languageAsciiDoc/{name}")
    @Produces("text/plain")
    @ApiOperation(value = "Returns the language documentation as Ascii doc format")
    public String languageAsciiDoc(@ApiParam(value = "The name of the language", required = true)
                                   @PathParam("name") String name) {
        return catalog.languageAsciiDoc(name);
    }

    @GET
    @Path("/findComponentLabels")
    @Produces("application/json")
    @ApiOperation(value = "Find all the unique label names all the components are using")
    public Set<String> findComponentLabels() {
        return catalog.findComponentLabels();
    }

    @GET
    @Path("/findDataFormatLabels")
    @Produces("application/json")
    @ApiOperation(value = "Find all the unique label names all the data formats are using")
    public Set<String> findDataFormatLabels() {
        return catalog.findDataFormatLabels();
    }

    @GET
    @Path("/findLanguageLabels")
    @Produces("application/json")
    @ApiOperation(value = "Find all the unique label names all the languages are using")
    public Set<String> findLanguageLabels() {
        return catalog.findLanguageLabels();
    }

    @GET
    @Path("/findModelLabels")
    @Produces("application/json")
    @ApiOperation(value = "Find all the unique label names all the models (EIP) are using.")
    public Set<String> findModelLabels() {
        return catalog.findModelLabels();
    }

    @GET
    @Path("/archetypeCatalogAsXml")
    @Produces("application/xml")
    @ApiOperation(value = "Returns the Apache Camel Maven Archetype catalog in XML format")
    public String archetypeCatalogAsXml() {
        return catalog.archetypeCatalogAsXml();
    }

    @GET
    @Path("/springSchemaAsXml")
    @Produces("application/xml")
    @ApiOperation(value = "Returns the Camel Spring XML schema")
    public String springSchemaAsXml() {
        return catalog.springSchemaAsXml();
    }

    @GET
    @Path("/blueprintSchemaAsXml")
    @Produces("application/xml")
    @ApiOperation(value = "Returns the Camel Blueprint XML schema")
    public String blueprintSchemaAsXml() {
        return catalog.blueprintSchemaAsXml();
    }

    @GET
    @Path("/listComponentsAsJson")
    @Produces("application/json")
    @ApiOperation(value = "Lists all the components summary details in JSon")
    public String listComponentsAsJson() {
        return catalog.listComponentsAsJson();
    }

    @GET
    @Path("/listDataFormatsAsJson")
    @Produces("application/json")
    @ApiOperation(value = "Lists all the data formats summary details in JSon")
    public String listDataFormatsAsJson() {
        return catalog.listDataFormatsAsJson();
    }

    @GET
    @Path("/listLanguagesAsJson")
    @Produces("application/json")
    @ApiOperation(value = "Lists all the languages summary details in JSon")
    public String listLanguagesAsJson() {
        return catalog.listLanguagesAsJson();
    }

    @GET
    @Path("/listModelsAsJson")
    @Produces("application/json")
    @ApiOperation(value = "Lists all the models (EIP) summary details in JSon")
    public String listModelsAsJson() {
        return catalog.listModelsAsJson();
    }

    @GET
    @Path("/summaryAsJson")
    @Produces("application/json")
    @ApiOperation(value = "Reports a summary what the catalog contains in JSon")
    public String summaryAsJson() {
        return catalog.summaryAsJson();
    }

    @POST
    @Path("/asEndpointUri/{scheme}")
    @Consumes("application/json")
    @Produces("text/plain")
    @ApiOperation(value = "Creates an endpoint uri in Java style configured using the provided options in the JSon body")
    public String asEndpointUri(@ApiParam(value = "The component scheme", readOnly = true) @PathParam("scheme") String scheme,
                                @ApiParam(value = "The options as a JSon map with key/value pairs", required = true) String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(json, Map.class);
            return catalog.asEndpointUri(scheme, map, true);
        } catch (Exception e) {
            return null;
        }
    }

    @POST
    @Path("/asEndpointUriXml/{scheme}")
    @Consumes("application/json")
    @Produces("text/plain")
    @ApiOperation(value = "Creates an endpoint uri in XML style configured using the provided options in the JSon body")
    public String asEndpointUriXml(@ApiParam(value = "The component scheme", readOnly = true) @PathParam("scheme") String scheme,
                                   @ApiParam(value = "The options as a JSon map with key/value pairs", required = true) String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(json, Map.class);
            return catalog.asEndpointUriXml(scheme, map, true);
        } catch (Exception e) {
            return null;
        }
    }

    @POST
    @Path("/mavenCacheDirectory/{name}")
    @ApiOperation(value = "Configures the Maven cache directory to use when downloading artifacts")
    public void mavenCacheDirectory(@ApiParam(value = "The name of the cache directory", required = true) @PathParam("name") String name) {
        maven.setCacheDirectory(name);
    }

    @POST
    @Path("/addMavenRepository/{name}/{url}")
    @ApiOperation(value = "Adds a third party Maven repository to use for downloading Maven artifacts")
    public void addMavenRepository(@ApiParam(value = "The name of the Maven repository", required = true) @PathParam("name") String name,
                                   @ApiParam(value = "The URL of the Maven repository", required = true) @PathParam("url") String url) {
        maven.addMavenRepository(name, url);
    }

    @POST
    @Path("/addComponentFromMavenArtifact/{groupId}/{artifactId}/{version}")
    @Produces("application/json")
    @ApiOperation(value = "Downloads the Maven artifact and scan for custom Camel components which will be added to the catalog and returns the names of the found components")
    public Set<String> addComponentFromMavenArtifact(@ApiParam(value = "The Maven groupId", required = true) @PathParam("groupId") String groupId,
                                                     @ApiParam(value = "The Maven artifactId", required = true) @PathParam("artifactId") String artifactId,
                                                     @ApiParam(value = "The Maven version", required = true) @PathParam("version") String version) {
        return maven.addArtifactToCatalog(catalog, null, groupId, artifactId, version);
    }

}
