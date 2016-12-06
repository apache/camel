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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;

/**
 * A REST based {@link CamelCatalog} service as a JAX-RS resource class.
 */
@Api(value = "/camel-catalog", description = "Camel Catalog REST API")
@Path("/camel-catalog")
public class CamelCatalogRest {

    private CamelCatalog catalog = new DefaultCamelCatalog(true);

    public CamelCatalog getCatalog() {
        return catalog;
    }

    /**
     * To inject an existing {@link CamelCatalog}
     */
    public void setCatalog(CamelCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * The version of this Camel Catalog
     */
    @GET
    @Path("/catalogVersion")
    @ApiOperation(value = "The version of this Camel Catalog")
    public String getCatalogVersion() {
        return catalog.getCatalogVersion();
    }

    /**
     * Find all the component names from the Camel catalog
     */
    @GET
    @Path("/findComponentNames")
    @Produces("application/json")
    public List<String> findComponentNames() {
        return catalog.findComponentNames();
    }

    /**
     * Find all the data format names from the Camel catalog
     */
    @GET
    @Path("/findDataFormatNames")
    @Produces("application/json")
    public List<String> findDataFormatNames() {
        return catalog.findDataFormatNames();
    }

    /**
     * Find all the language names from the Camel catalog
     */
    @GET
    @Path("/findLanguageNames")
    @Produces("application/json")
    public List<String> findLanguageNames() {
        return catalog.findLanguageNames();

    }

    /**
     * Find all the model names from the Camel catalog
     */
    @GET
    @Path("/findModelNames")
    @Produces("application/json")
    public List<String> findModelNames() {
        return catalog.findModelNames();
    }

    /**
     * Find all the component names from the Camel catalog that matches the label
     */
    @GET
    @Path("/findComponentNames/{filter}")
    @Produces("application/json")
    public List<String> findComponentNames(@PathParam("filter") String filter) {
        return catalog.findComponentNames(filter);
    }

    /**
     * Find all the data format names from the Camel catalog that matches the label
     */
    @GET
    @Path("/findDataFormatNames/{filter}")
    @Produces("application/json")
    public List<String> findDataFormatNames(@PathParam("filter") String filter) {
        return catalog.findDataFormatNames(filter);
    }

    /**
     * Find all the language names from the Camel catalog that matches the label
     */
    @GET
    @Path("/findLanguageNames/{filter}")
    @Produces("application/json")
    public List<String> findLanguageNames(@PathParam("filter") String filter) {
        return catalog.findLanguageNames(filter);
    }

    /**
     * Find all the model names from the Camel catalog that matches the label
     */
    @GET
    @Path("/findModelNames/{filter}")
    @Produces("application/json")
    public List<String> findModelNames(@PathParam("filter") String filter) {
        return catalog.findModelNames(filter);
    }

    /**
     * Returns the component information as JSon format.
     */
    @GET
    @Path("/componentJSonSchema/{name}")
    @Produces("application/json")
    public String componentJSonSchema(@PathParam("name") String name) {
        return catalog.componentJSonSchema(name);
    }

    /**
     * Returns the data format information as JSon format.
     */
    @GET
    @Path("/dataFormatJSonSchema/{name}")
    @Produces("application/json")
    public String dataFormatJSonSchema(@PathParam("name") String name) {
        return catalog.dataFormatJSonSchema(name);
    }

    /**
     * Returns the language information as JSon format.
     */
    @GET
    @Path("/languageJSonSchema/{name}")
    @Produces("application/json")
    public String languageJSonSchema(@PathParam("name") String name) {
        return catalog.languageJSonSchema(name);
    }

    /**
     * Returns the model information as JSon format.
     */
    @GET
    @Path("/modelJSonSchema/{name}")
    @Produces("application/json")
    public String modelJSonSchema(@PathParam("name") String name) {
        return catalog.modelJSonSchema(name);
    }

    /**
     * Returns the component documentation as Ascii doc format.
     */
    @GET
    @Path("/componentAsciiDoc/{name}")
    @Produces("text/plain")
    public String componentAsciiDoc(@PathParam("name") String name) {
        return catalog.componentAsciiDoc(name);
    }

    /**
     * Returns the data format documentation as Ascii doc format.
     */
    @GET
    @Path("/dataFormatAsciiDoc/{name}")
    @Produces("text/plain")
    public String dataFormatAsciiDoc(@PathParam("name") String name) {
        return catalog.dataFormatAsciiDoc(name);
    }

    /**
     * Returns the language documentation as Ascii doc format.
     */
    @GET
    @Path("/languageAsciiDoc/{name}")
    @Produces("text/plain")
    public String languageAsciiDoc(@PathParam("name") String name) {
        return catalog.languageAsciiDoc(name);
    }

    /**
     * Find all the unique label names all the components are using.
     */
    @GET
    @Path("/findComponentLabels")
    @Produces("application/json")
    public Set<String> findComponentLabels() {
        return catalog.findComponentLabels();
    }

    /**
     * Find all the unique label names all the data formats are using.
     */
    @GET
    @Path("/findDataFormatLabels")
    @Produces("application/json")
    public Set<String> findDataFormatLabels() {
        return catalog.findDataFormatLabels();
    }

    /**
     * Find all the unique label names all the data formats are using.
     */
    @GET
    @Path("/findLanguageLabels")
    @Produces("application/json")
    public Set<String> findLanguageLabels() {
        return catalog.findLanguageLabels();
    }

    /**
     * Find all the unique label names all the models are using.
     */
    @GET
    @Path("/findModelLabels")
    @Produces("application/json")
    public Set<String> findModelLabels() {
        return catalog.findModelLabels();
    }

    /**
     * Returns the Apache Camel Maven Archetype catalog in XML format.
     */
    @GET
    @Path("/archetypeCatalogAsXml")
    @Produces("application/xml")
    public String archetypeCatalogAsXml() {
        return catalog.archetypeCatalogAsXml();
    }

    /**
     * Returns the Camel Spring XML schema
     */
    @GET
    @Path("/springSchemaAsXml")
    @Produces("application/xml")
    public String springSchemaAsXml() {
        return catalog.springSchemaAsXml();
    }

    /**
     * Returns the Camel Blueprint XML schema
     */
    @GET
    @Path("/blueprintSchemaAsXml")
    @Produces("application/xml")
    public String blueprintSchemaAsXml() {
        return catalog.blueprintSchemaAsXml();
    }

    /**
     * Lists all the components summary details in JSon
     */
    @GET
    @Path("/listComponentsAsJson")
    @Produces("application/json")
    public String listComponentsAsJson() {
        return catalog.listComponentsAsJson();
    }

    /**
     * Lists all the data formats summary details in JSon
     */
    @GET
    @Path("/listDataFormatsAsJson")
    @Produces("application/json")
    public String listDataFormatsAsJson() {
        return catalog.listDataFormatsAsJson();
    }

    /**
     * Lists all the languages summary details in JSon
     */
    @GET
    @Path("/listLanguagesAsJson")
    @Produces("application/json")
    public String listLanguagesAsJson() {
        return catalog.listLanguagesAsJson();
    }

    /**
     * Lists all the models (EIPs) summary details in JSon
     */
    @GET
    @Path("/listModelsAsJson")
    @Produces("application/json")
    public String listModelsAsJson() {
        return catalog.listModelsAsJson();
    }

    /**
     * Reports a summary what the catalog contains in JSon
     */
    @GET
    @Path("/summaryAsJson")
    @Produces("application/json")
    public String summaryAsJson() {
        return catalog.summaryAsJson();
    }

}
