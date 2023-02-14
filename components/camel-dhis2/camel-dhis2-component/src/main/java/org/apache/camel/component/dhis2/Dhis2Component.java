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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.dhis2.internal.Dhis2ApiCollection;
import org.apache.camel.component.dhis2.internal.Dhis2ApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.component.AbstractApiComponent;
import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;

@org.apache.camel.spi.annotations.Component("dhis2")
public class Dhis2Component extends AbstractApiComponent<Dhis2ApiName, Dhis2Configuration, Dhis2ApiCollection> {
    @Metadata(label = "advanced")
    Dhis2Configuration configuration;

    private Dhis2Client dhis2Client;

    public Dhis2Component() {
        super(Dhis2Endpoint.class, Dhis2ApiName.class, Dhis2ApiCollection.getCollection());
    }

    public Dhis2Component(CamelContext context) {
        super(context, Dhis2Endpoint.class, Dhis2ApiName.class, Dhis2ApiCollection.getCollection());
    }

    @Override
    protected Dhis2ApiName getApiName(String apiNameStr)
            throws IllegalArgumentException {
        return getCamelContext().getTypeConverter().convertTo(Dhis2ApiName.class, apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, Dhis2ApiName apiName,
            Dhis2Configuration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new Dhis2Endpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(Dhis2Configuration configuration) {
        super.setConfiguration(configuration);
    }

    public Dhis2Client getClient(Dhis2Configuration endpointConfiguration) {
        if (endpointConfiguration.equals(this.configuration)) {
            synchronized (this) {
                if (this.dhis2Client == null) {
                    this.dhis2Client = Dhis2ClientBuilder.newClient(endpointConfiguration.getBaseApiUrl(),
                            endpointConfiguration.getUsername(), endpointConfiguration.getPassword()).build();
                }
            }

            return this.dhis2Client;
        } else {
            if (endpointConfiguration.getClient() != null) {
                return endpointConfiguration.getClient();
            } else {
                return Dhis2ClientBuilder.newClient(endpointConfiguration.getBaseApiUrl(),
                        endpointConfiguration.getUsername(), endpointConfiguration.getPassword()).build();
            }
        }
    }

}
