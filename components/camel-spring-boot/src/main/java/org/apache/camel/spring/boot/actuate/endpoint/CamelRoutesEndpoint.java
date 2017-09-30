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
package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spring.boot.model.RouteDetailsInfo;
import org.apache.camel.spring.boot.model.RouteInfo;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link Endpoint} to expose {@link org.apache.camel.Route} information.
 */
@ConfigurationProperties(prefix = "endpoints." + CamelRoutesEndpoint.ENDPOINT_ID)
public class CamelRoutesEndpoint extends AbstractCamelEndpoint<List<RouteInfo>> {
    public static final String ENDPOINT_ID = "camelroutes";

    public CamelRoutesEndpoint(CamelContext camelContext) {
        super(ENDPOINT_ID, camelContext);
    }

    @Override
    public List<RouteInfo> invoke() {
        return getRoutesInfo();
    }

    public RouteInfo getRouteInfo(String id) {
        Route route = getCamelContext().getRoute(id);
        if (route != null) {
            return new RouteInfo(route);
        }

        return null;
    }

    public List<RouteInfo> getRoutesInfo() {
        return getCamelContext().getRoutes().stream()
            .map(RouteInfo::new)
            .collect(Collectors.toList());
    }

    public RouteDetailsInfo getRouteDetailsInfo(String id) {
        Route route = getCamelContext().getRoute(id);
        if (route != null) {
            return new RouteDetailsInfo(getCamelContext(), route);
        }

        return null;
    }

    public void startRoute(String id) throws Exception {
        getCamelContext().getRouteController().startRoute(id);
    }

    public void resetRoute(String id) throws Exception {
        ManagedRouteMBean managedRouteMBean = getCamelContext().getManagedRoute(id, ManagedRouteMBean.class);
        if (managedRouteMBean != null) {
            managedRouteMBean.reset(true);
        } 
    }

    public void stopRoute(String id, Optional<Long> timeout, Optional<TimeUnit> timeUnit, Optional<Boolean> abortAfterTimeout) throws Exception {
        if (timeout.isPresent()) {
            getCamelContext().getRouteController().stopRoute(id, timeout.get(), timeUnit.orElse(TimeUnit.SECONDS), abortAfterTimeout.orElse(Boolean.TRUE));
        } else {
            getCamelContext().getRouteController().stopRoute(id);
        }
    }

    public void suspendRoute(String id, Optional<Long> timeout, Optional<TimeUnit> timeUnit) throws Exception {
        if (timeout.isPresent()) {
            getCamelContext().getRouteController().suspendRoute(id, timeout.get(), timeUnit.orElse(TimeUnit.SECONDS));
        } else {
            getCamelContext().getRouteController().suspendRoute(id);
        }
    }

    public void resumeRoute(String id) throws Exception {
        getCamelContext().getRouteController().resumeRoute(id);
    }
}
