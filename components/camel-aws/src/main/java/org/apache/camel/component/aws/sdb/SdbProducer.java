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
package org.apache.camel.component.aws.sdb;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;

/**
 * A Producer which sends messages to the Amazon SimpleDB Service
 * <a href="http://aws.amazon.com/simpledb/">AWS SDB</a>
 */
public class SdbProducer extends DefaultProducer {
    
    private transient String sdbProducerToString;
    
    public SdbProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
        case BatchDeleteAttributes:
            new BatchDeleteAttributesCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case BatchPutAttributes:
            new BatchPutAttributesCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case DeleteAttributes:
            new DeleteAttributesCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case DeleteDomain:
            new DeleteDomainCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case DomainMetadata:
            new DomainMetadataCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case GetAttributes:
            new GetAttributesCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case ListDomains:
            new ListDomainsCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case PutAttributes:
            new PutAttributesCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        case Select:
            new SelectCommand(getEndpoint().getSdbClient(), getConfiguration(), exchange).execute();
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private SdbOperations determineOperation(Exchange exchange) {
        SdbOperations operation = exchange.getIn().getHeader(SdbConstants.OPERATION, SdbOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected SdbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (sdbProducerToString == null) {
            sdbProducerToString = "SdbProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sdbProducerToString;
    }

    @Override
    public SdbEndpoint getEndpoint() {
        return (SdbEndpoint) super.getEndpoint();
    }
}