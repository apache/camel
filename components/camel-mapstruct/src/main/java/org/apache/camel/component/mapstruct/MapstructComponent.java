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
package org.apache.camel.component.mapstruct;

import java.util.Map;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@org.apache.camel.spi.annotations.Component("mapstruct")
public class MapstructComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(MapstructComponent.class);

    @Metadata(label = "advanced", autowired = true)
    private MapStructMapperFinder mapStructConverter;
    @Metadata(required = true)
    private String mapperPackageName;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MapstructEndpoint answer = new MapstructEndpoint(uri, this);
        answer.setClassName(remaining);
        setProperties(answer, parameters);
        return answer;
    }

    public MapStructMapperFinder getMapStructConverter() {
        return mapStructConverter;
    }

    /**
     * To use a custom MapStructConverter such as adapting to a special runtime.
     */
    public void setMapStructConverter(MapStructMapperFinder mapStructConverter) {
        this.mapStructConverter = mapStructConverter;
    }

    public String getMapperPackageName() {
        return mapperPackageName;
    }

    /**
     * Package name(s) where Camel should discover Mapstruct mapping classes. Multiple package names can be separated by
     * comma.
     */
    public void setMapperPackageName(String mapperPackageName) {
        this.mapperPackageName = mapperPackageName;
    }

    @Override
    protected void doStart() throws Exception {
        if (mapStructConverter == null) {
            mapStructConverter = new DefaultMapStructFinder();
            CamelContextAware.trySetCamelContext(mapStructConverter, getCamelContext());
            mapStructConverter.setMapperPackageName(mapperPackageName);
        }
        ServiceHelper.startService(mapStructConverter);

        if (mapStructConverter.getMapperPackageName() == null) {
            LOG.warn("Cannot find MapStruct Mapper classes because mapperPackageName has not been configured");
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(mapStructConverter);
    }
}
