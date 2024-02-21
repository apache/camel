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

import org.apache.camel.StaticService;

public interface MapStructMapperFinder extends StaticService {

    /**
     * Package name(s) where Camel should discover Mapstruct mapping classes. Multiple package names can be separated by
     * comma.
     */
    void setMapperPackageName(String mapperPackageName);

    /**
     * Package name(s) where Camel should discover Mapstruct mapping classes. Multiple package names can be separated by
     * comma.
     */
    String getMapperPackageName();

    /**
     * Callback on a found mapper class
     *
     * @return number of mapping methods loaded from the given mapper class
     */
    int discoverMappings(Class<?> clazz);

}
