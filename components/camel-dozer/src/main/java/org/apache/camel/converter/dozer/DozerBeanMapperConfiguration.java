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
package org.apache.camel.converter.dozer;

import java.util.List;
import java.util.Map;

import com.github.dozermapper.core.CustomConverter;
import com.github.dozermapper.core.CustomFieldMapper;
import com.github.dozermapper.core.events.EventListener;
import com.github.dozermapper.core.loader.api.BeanMappingBuilder;

public class DozerBeanMapperConfiguration {

    private List<String> mappingFiles;
    private List<CustomConverter> customConverters;
    private List<EventListener> eventListeners;
    private Map<String, CustomConverter> customConvertersWithId;
    private CustomFieldMapper customFieldMapper;
    private List<BeanMappingBuilder> beanMappingBuilders;

    public List<String> getMappingFiles() {
        return mappingFiles;
    }

    public void setMappingFiles(List<String> mappingFiles) {
        this.mappingFiles = mappingFiles;
    }

    public List<CustomConverter> getCustomConverters() {
        return customConverters;
    }

    public void setCustomConverters(List<CustomConverter> customConverters) {
        this.customConverters = customConverters;
    }

    public List<EventListener> getEventListeners() {
        return eventListeners;
    }

    public void setEventListeners(List<EventListener> eventListeners) {
        this.eventListeners = eventListeners;
    }

    public Map<String, CustomConverter> getCustomConvertersWithId() {
        return customConvertersWithId;
    }

    public void setCustomConvertersWithId(Map<String, CustomConverter> customConvertersWithId) {
        this.customConvertersWithId = customConvertersWithId;
    }

    public CustomFieldMapper getCustomFieldMapper() {
        return customFieldMapper;
    }

    public void setCustomFieldMapper(CustomFieldMapper customFieldMapper) {
        this.customFieldMapper = customFieldMapper;
    }

    public List<BeanMappingBuilder> getBeanMappingBuilders() {
        return beanMappingBuilders;
    }

    public void setBeanMappingBuilders(List<BeanMappingBuilder> beanMappingBuilders) {
        this.beanMappingBuilders = beanMappingBuilders;
    }
}
