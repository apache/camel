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

package org.apache.camel.processor.idempotent.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

@Metadata(
        label = "bean",
        description = "Idempotent repository that uses Hazelcast cache to store message ids.",
        annotations = {"interfaceName=org.apache.camel.spi.IdempotentRepository"})
@Configurer(metadataOnly = true)
public class HazelcastIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    protected boolean useLocalHzInstance;

    @Metadata(description = "Name of cache to use", defaultValue = "HazelcastIdempotentRepository")
    private String repositoryName;

    private IMap<String, Boolean> repo;

    @Metadata(description = "To use an existing Hazelcast instance instead of local")
    private HazelcastInstance hazelcastInstance;

    public HazelcastIdempotentRepository() {
        this(null);
    }

    public HazelcastIdempotentRepository(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, HazelcastIdempotentRepository.class.getSimpleName());
    }

    public HazelcastIdempotentRepository(HazelcastInstance hazelcastInstance, String repositoryName) {
        this.hazelcastInstance = hazelcastInstance;
        this.repositoryName = repositoryName;
    }

    @Override
    protected void doStart() throws Exception {
        if (hazelcastInstance == null) {
            Config cfg = new XmlConfigBuilder().build();
            cfg.setProperty("hazelcast.version.check.enabled", "false");
            hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
            useLocalHzInstance = true;
        } else {
            ObjectHelper.notNull(hazelcastInstance, "hazelcastInstance");
        }
        repo = hazelcastInstance.getMap(repositoryName);
    }

    @Override
    protected void doStop() throws Exception {
        if (useLocalHzInstance) {
            hazelcastInstance.getLifecycleService().shutdown();
        }
    }

    @Override
    public boolean add(String key) {
        repo.lock(key);
        try {
            return repo.putIfAbsent(key, false) == null;
        } finally {
            repo.unlock(key);
        }
    }

    @Override
    public boolean confirm(String key) {
        repo.lock(key);
        try {
            return repo.replace(key, false, true);
        } finally {
            repo.unlock(key);
        }
    }

    @Override
    public boolean contains(String key) {
        repo.lock(key);
        try {
            return this.repo.containsKey(key);
        } finally {
            repo.unlock(key);
        }
    }

    @Override
    public boolean remove(String key) {
        repo.lock(key);
        try {
            return repo.remove(key) != null;
        } finally {
            repo.unlock(key);
        }
    }

    @Override
    public void clear() {
        repo.clear();
    }

    public String getRepositoryName() {
        return repositoryName;
    }
}
