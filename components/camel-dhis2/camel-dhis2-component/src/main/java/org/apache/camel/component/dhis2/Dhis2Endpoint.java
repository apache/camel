/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.apache.camel.component.dhis2;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dhis2.api.Dhis2Get;
import org.apache.camel.component.dhis2.api.Dhis2Post;
import org.apache.camel.component.dhis2.api.Dhis2ResourceTables;
import org.apache.camel.component.dhis2.internal.Dhis2ApiCollection;
import org.apache.camel.component.dhis2.internal.Dhis2ApiName;
import org.apache.camel.component.dhis2.internal.Dhis2Constants;
import org.apache.camel.component.dhis2.internal.Dhis2PropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;

/**
 * Leverages the DHIS2 Java SDK to integrate Apache Camel with the DHIS2 Web API.
 * <p>
 */
@UriEndpoint(firstVersion = "3.21.0", scheme = "dhis2", title = "DHIS2", syntax = "dhis2:methodName",
             apiSyntax = "apiName/methodName", category = {
                     Category.API })
public class Dhis2Endpoint extends AbstractApiEndpoint<Dhis2ApiName, Dhis2Configuration> {

    @UriParam
    private final Dhis2Configuration configuration;

    // TODO create and manage API proxy
    private Object apiProxy;

    public Dhis2Endpoint(String uri, Dhis2Component component,
                         Dhis2ApiName apiName, String methodName, Dhis2Configuration endpointConfiguration) {
        super(uri, component, apiName, methodName, Dhis2ApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    public Producer createProducer()
            throws Exception {
        return new Dhis2Producer(this);
    }

    public Consumer createConsumer(Processor processor)
            throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final Dhis2Consumer consumer = new Dhis2Consumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<Dhis2Configuration> getPropertiesHelper() {
        return Dhis2PropertiesHelper.getHelper(getCamelContext());
    }

    protected String getThreadProfileName() {
        return Dhis2Constants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        Dhis2Client dhis2Client = this.getClient();
        switch (apiName) {
            case GET:
                apiProxy = new Dhis2Get(dhis2Client);
                break;
            case POST:
                apiProxy = new Dhis2Post(dhis2Client);
                break;
            case RESOURCE_TABLES:
                apiProxy = new Dhis2ResourceTables(dhis2Client);
                break;
            default:
                throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return apiProxy;
    }

    protected Dhis2Client getClient() {
        return ((Dhis2Component) this.getComponent()).getClient(this.configuration);
    }
}
