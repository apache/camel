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
package org.apache.camel.component.jcache;

public enum JCacheProviders implements JCacheProvider {
    hazelcast    {{
            shortName = "hazelcast";
            className = "com.hazelcast.cache.HazelcastCachingProvider";
        }},
    ehcache      {{
            shortName = "ehcache";
            className = "org.ehcache.jsr107.EhcacheCachingProvider";
        }},
    caffeine     {{
            shortName = "caffeine";
            className = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";
        }},
    ispnEmbedded {{
            shortName = "infinispan-embedded";
            className = "org.infinispan.jcache.embedded.JCachingProvider";
        }};

    protected String shortName;
    protected String className;

    @Override
    public String shortName() {
        return shortName;
    }

    @Override
    public String className() {
        return className;
    }

    public static JCacheProvider lookup(String providerName) {
        if (providerName != null) {
            for (JCacheProvider provider : values()) {
                if (provider.shortName().equals(providerName) || provider.className().equals(providerName)) {
                    return provider;
                }
            }
        }

        return new JCacheProvider() {
            @Override
            public String shortName() {
                return providerName;
            }

            @Override
            public String className() {
                return providerName;
            }
        };
    }
}
