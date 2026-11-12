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
package org.apache.camel.component.azure.functions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.functions.client.FunctionsClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Invoke and manage Azure Functions.
 */
@UriEndpoint(firstVersion = "4.19.0",
             scheme = "azure-functions",
             title = "Azure Functions",
             syntax = "azure-functions:functionApp/functionName",
             category = { Category.CLOUD, Category.SERVERLESS },
             producerOnly = true,
             headersClass = FunctionsConstants.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.VALUE, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "listFunctionApps",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "getFunctionApp",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "createFunctionApp",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "deleteFunctionApp",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "startFunctionApp",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "stopFunctionApp",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "restartFunctionApp",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "listFunctions", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "getFunction", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "getFunctionKeys",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "getFunctionAppConfiguration",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "updateFunctionAppConfiguration",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "listTags", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "tagResource", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "untagResource",
                                                         carriers = Carrier.ANY) },
                                  description = "The request body of invokeFunction, the default: text, a number or a boolean"
                                                + " is sent as text (UTF-8), and bytes, a stream or a file as their content. A Map"
                                                + " or a POJO is not"
                                                + " marshalled: marshal it to JSON first, as a body that does not convert to bytes"
                                                + " fails. The Content-Type is the one the CamelAzureFunctionsHttpHeaders header"
                                                + " sets, else the Content-Type header of the message, else application/json."
                                                + " GET and DELETE send no body."
                                                + " The management operations do not use the body: they take their input from"
                                                + " headers, such as the app settings and the tags"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listFunctionApps",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.util.Map<String, Object>>"),
                                           @BodyType.When(option = "operation", value = "getFunctionApp",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, Object>"),
                                           @BodyType.When(option = "operation", value = "createFunctionApp",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, Object>"),
                                           @BodyType.When(option = "operation", value = "deleteFunctionApp",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "startFunctionApp",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "stopFunctionApp",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "restartFunctionApp",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "listFunctions",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.util.Map<String, Object>>"),
                                           @BodyType.When(option = "operation", value = "getFunction", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, Object>"),
                                           @BodyType.When(option = "operation", value = "getFunctionKeys",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, Object>"),
                                           @BodyType.When(option = "operation", value = "getFunctionAppConfiguration",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, String>"),
                                           @BodyType.When(option = "operation", value = "updateFunctionAppConfiguration",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, String>"),
                                           @BodyType.When(option = "operation", value = "listTags", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, String>"),
                                           @BodyType.When(option = "operation", value = "tagResource", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, String>"),
                                           @BodyType.When(option = "operation", value = "untagResource", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, String>") },
                                   description = "The response body of the function as text for invokeFunction, the default, with"
                                                 + " the status in the CamelAzureFunctionsStatusCode header. A status of 400 or"
                                                 + " above fails the exchange with an HttpOperationFailedException, unless"
                                                 + " throwExceptionOnFailure=false, which keeps the response as text. A List of"
                                                 + " Maps that describe the function apps or the functions for"
                                                 + " the list operations, a Map for the get and create operations, the settings or"
                                                 + " the tags as a Map, and true for delete, start, stop and restart"))
public class FunctionsEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Pattern FUNCTION_APP_NAME = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,58}[A-Za-z0-9])?");

    @UriParam
    private FunctionsConfiguration configuration;

    private FunctionsClientFactory clientFactory;

    public FunctionsEndpoint(String uri, Component component, FunctionsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FunctionsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported for azure-functions component");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        clientFactory = new FunctionsClientFactory();
    }

    @Override
    public String getServiceUrl() {
        if (isFunctionAppName(configuration.getFunctionApp())) {
            return "https://" + configuration.getFunctionApp() + ".azurewebsites.net";
        }
        return null;
    }

    /**
     * Whether the value is an Azure app name: letters, digits and hyphens, not starting or ending with a hyphen, at
     * most 60 characters.
     */
    static boolean isFunctionAppName(String functionApp) {
        return functionApp != null && FUNCTION_APP_NAME.matcher(functionApp).matches();
    }

    @Override
    public String getServiceProtocol() {
        return "azure-functions";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        Map<String, String> metadata = new HashMap<>();
        if (ObjectHelper.isNotEmpty(configuration.getFunctionApp())) {
            metadata.put("functionApp", configuration.getFunctionApp());
        }
        if (ObjectHelper.isNotEmpty(configuration.getFunctionName())) {
            metadata.put("functionName", configuration.getFunctionName());
        }
        if (ObjectHelper.isNotEmpty(configuration.getResourceGroup())) {
            metadata.put("resourceGroup", configuration.getResourceGroup());
        }
        return metadata;
    }

    public FunctionsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(FunctionsConfiguration configuration) {
        this.configuration = configuration;
    }

    public FunctionsClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(FunctionsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
}
