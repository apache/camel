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
package org.apache.camel.component.docling.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.component.docling.DoclingComponent;
import org.apache.camel.component.docling.DoclingConfiguration;
import org.apache.camel.test.infra.docling.services.DoclingService;
import org.apache.camel.test.infra.docling.services.DoclingServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DoclingITestSupport extends CamelTestSupport {

    protected static final Logger LOG = LoggerFactory.getLogger(DoclingITestSupport.class);

    @RegisterExtension
    static DoclingService doclingService = DoclingServiceFactory.createService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DoclingComponent docling = context.getComponent("docling", DoclingComponent.class);
        DoclingConfiguration conf = new DoclingConfiguration();
        conf.setUseDoclingServe(true);
        conf.setDoclingServeUrl(doclingService.doclingServerUrl());
        // PDF processing can take longer than the default 30s
        conf.setProcessTimeout(120000);
        docling.setConfiguration(conf);

        LOG.info("Testing Docling-Serve at: {}", doclingService.doclingServerUrl());

        return context;
    }

}
