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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SdbProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(SdbProducer.class);

    public SdbProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        String domainName = determineDomainName(exchange);
        String itemName = determineItemName(exchange);
        String operation = determineOperation(exchange);

        if (SdbConstants.OPERATION_PUT.equals(operation)) {
            executePut(exchange, domainName, itemName);
        } else if (SdbConstants.OPERATION_GET.equals(operation)) {
            executeGet(exchange, domainName, itemName);
        } else if (SdbConstants.OPERATION_DELETE.equals(operation)) {
            executeDelete(domainName, itemName);
        } else {
            throw new UnsupportedOperationException("Not supported operation: " + operation);
        }
    }

    private void executeDelete(String domainName, String itemName) {
        LOG.trace("Deleting item [{}] from domain [{}]...", itemName, domainName);
        getEndpoint().getSdbClient().deleteAttributes(new DeleteAttributesRequest(domainName, itemName));
    }

    private void executeGet(Exchange exchange, String domainName, String itemName) {
        LOG.trace("Getting item [{}] from domain [{}]...", itemName, domainName);
        GetAttributesRequest getAttributesRequest = new GetAttributesRequest(domainName, itemName);
        GetAttributesResult result = getEndpoint().getSdbClient().getAttributes(getAttributesRequest);
        populateExchangeWithResult(exchange, result);
    }

    private void populateExchangeWithResult(Exchange exchange, GetAttributesResult attributesResult) {
        for (Attribute attribute : attributesResult.getAttributes()) {
            exchange.getIn().setHeader(attribute.getName(), attribute.getValue());
        }
    }

    private void executePut(Exchange exchange, String domainName, String itemName) {
        List<ReplaceableAttribute> attributes = extractAttributesFrom(exchange);
        PutAttributesRequest request = new PutAttributesRequest(domainName, itemName, attributes);

        LOG.trace("Put object [{}] from exchange [{}]...", request, exchange);
        getEndpoint().getSdbClient().putAttributes(request);
    }

    private List<ReplaceableAttribute> extractAttributesFrom(Exchange exchange) {
        List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (entry.getKey().startsWith(SdbConstants.ATTRIBUTE_PREFIX)) {
                String fieldName = entry.getKey().substring(SdbConstants.ATTRIBUTE_PREFIX.length());
                attributes.add(new ReplaceableAttribute(fieldName, (String) entry.getValue(), true));
            }
        }
        return attributes;
    }

    private String determineDomainName(Exchange exchange) {
        String domainName = exchange.getIn().getHeader(SdbConstants.DOMAIN_NAME, String.class);
        return domainName != null ? domainName : getConfiguration().getDomainName();
    }

    private String determineOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(SdbConstants.OPERATION, String.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation != null ? operation : SdbConstants.OPERATION_PUT;
    }

    private String determineItemName(Exchange exchange) {
        String key = exchange.getIn().getHeader(SdbConstants.ITEM_KEY, String.class);
        if (key == null) {
            throw new IllegalArgumentException("AWS SDB Item Key header is missing.");
        }
        return key;
    }

    protected SdbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        return "SdbProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }

    @Override
    public SdbEndpoint getEndpoint() {
        return (SdbEndpoint) super.getEndpoint();
    }
}
