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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.spi.VariableRepositoryFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.GlobalVariableRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link VariableRepositoryFactory}.
 */
public class DefaultVariableRepositoryFactory extends ServiceSupport implements VariableRepositoryFactory, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVariableRepositoryFactory.class);

    private final CamelContext camelContext;
    private VariableRepository global;

    public DefaultVariableRepositoryFactory(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public VariableRepository getVariableRepository(String id) {
        if ("global".equals(id)) {
            return global;
        }

        // otherwise lookup in registry if the repo exists
        VariableRepository repo = CamelContextHelper.lookup(camelContext, id, VariableRepository.class);
        if (repo != null) {
            return repo;
        }

        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        VariableRepository repo = CamelContextHelper.lookup(camelContext, GLOBAL_VARIABLE_FACTORY_ID, VariableRepository.class);
        if (repo != null) {
            LOG.info("Using VariableRepository: {} as global repository", repo.getId());
            global = repo;
        } else {
            global = new GlobalVariableRepository();
        }

        if (!camelContext.hasService(global)) {
            camelContext.addService(global);
        }
    }

}
