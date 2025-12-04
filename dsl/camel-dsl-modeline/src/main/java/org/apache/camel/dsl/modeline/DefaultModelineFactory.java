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

package org.apache.camel.dsl.modeline;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;

@JdkService(ModelineFactory.FACTORY)
public class DefaultModelineFactory extends ServiceSupport
        implements ModelineFactory, CamelContextAware, NonManagedService, StaticService {

    private CamelContext camelContext;
    private final ModelineParser jbang = new JBangModelineParser();
    private ModelineParser parser;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void parseModeline(Resource resource) throws Exception {
        List<CamelContextCustomizer> customizers = new ArrayList<>(jbang.parse(resource));
        // custom parser which may return null
        if (parser != null) {
            var list = parser.parse(resource);
            if (list != null) {
                customizers.addAll(list);
            }
        }
        customizers.forEach(this::onConfigureModeline);
    }

    /**
     * Configures the modeline via the {@link CamelContextCustomizer}
     *
     * @param customizer the customer for configuring a detected modeline
     */
    protected void onConfigureModeline(CamelContextCustomizer customizer) {
        customizer.configure(camelContext);
    }

    @Override
    protected void doInit() throws Exception {
        // is there any custom modeline parser
        parser = CamelContextHelper.findSingleByType(camelContext, ModelineParser.class);
    }

    @Override
    public String toString() {
        return "camel-dsl-modeline";
    }
}
