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
package org.apache.camel.component.headersmap;

import java.util.Map;

import com.cedarsoftware.util.CaseInsensitiveMap;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.annotations.JdkService;

/**
 * A faster {@link HeadersMapFactory} which is using the {@link com.cedarsoftware.util.CaseInsensitiveMap} map
 * implementation.
 */
@JdkService(HeadersMapFactory.FACTORY)
public class FastHeadersMapFactory implements HeadersMapFactory {

    @Override
    public Map<String, Object> newMap() {
        return new CaseInsensitiveMap<>();
    }

    @Override
    public Map<String, Object> newMap(Map<String, Object> map) {
        return new CaseInsensitiveMap<>(map);
    }

    @Override
    public boolean isInstanceOf(Map<String, Object> map) {
        return map instanceof CaseInsensitiveMap;
    }

    @Override
    public boolean isCaseInsensitive() {
        return true;
    }

    @Override
    public String toString() {
        return "camel-headersmap";
    }
}
