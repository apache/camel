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
package org.apache.camel.component.cmis;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.QueryResult;

/**
 * The CMIS Query producer.
 */
public class CMISQueryProducer extends DefaultProducer {
    private final CMISSessionFacade cmisSessionFacade;

    public CMISQueryProducer(CMISEndpoint endpoint, CMISSessionFacade cmisSessionFacade) {
        super(endpoint);
        this.cmisSessionFacade = cmisSessionFacade;
    }

    public void process(Exchange exchange) throws Exception {
        List<Map<String, Object>> nodes = executeQuery(exchange);
        exchange.getOut().setBody(nodes);
        exchange.getOut().setHeader(CamelCMISConstants.CAMEL_CMIS_RESULT_COUNT, nodes.size());
    }

    private List<Map<String, Object>> executeQuery(Exchange exchange) throws Exception {
        String query = exchange.getIn().getMandatoryBody(String.class);
        Boolean retrieveContent = getRetrieveContent(exchange);
        Integer readSize = getReadSize(exchange);

        ItemIterable<QueryResult> itemIterable = cmisSessionFacade.executeQuery(query);
        return cmisSessionFacade.retrieveResult(retrieveContent, readSize, itemIterable);
    }

    private Integer getReadSize(Exchange exchange) {
        return exchange.getIn().getHeader(CamelCMISConstants.CAMEL_CMIS_READ_SIZE, Integer.class);
    }

    private Boolean getRetrieveContent(Exchange exchange) {
        return exchange.getIn().getHeader(CamelCMISConstants.CAMEL_CMIS_RETRIEVE_CONTENT, Boolean.class);
    }
}
