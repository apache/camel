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
package org.apache.camel.cdi.routetemplate;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MyRouteCreatorBean {

    private static final Logger LOG = LoggerFactory.getLogger(MyRouteCreatorBean.class);

    @Inject
    CamelContext context;

    public void setupRoutes(@Observes CamelEvent.CamelContextStartedEvent startup) throws Exception {
        LOG.info("Creating routes from templates");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("start", "foo");
        parameters.put("append", "A");
        context.addRouteFromTemplate("foo", "myTemplate", parameters);
        parameters.clear();
        parameters.put("start", "bar");
        parameters.put("append", "B");
        context.addRouteFromTemplate("bar", "myTemplate", parameters);
    }
}
