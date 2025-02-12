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
package org.apache.camel.component.redis.processor.idempotent;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.redis.RedisConfiguration;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

@Metadata(label = "bean",
          description = "Idempotent repository that uses Redis to store message ids.",
          annotations = { "interfaceName=org.apache.camel.spi.IdempotentRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Spring Redis based message id repository")
public class SpringRedisIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private SetOperations<String, String> setOperations;
    @Metadata(description = "Name of repository", required = true)
    private String repositoryName;
    @Metadata(description = "Redis configuration")
    private RedisConfiguration redisConfiguration;
    private RedisTemplate<String, String> redisTemplate;
    @Metadata(label = "advanced", description = "Delete all keys of the currently selected database."
                                                + " Be careful if enabling this as all existing data will be deleted.")
    private boolean flushOnStartup;

    public SpringRedisIdempotentRepository() {
    }

    public SpringRedisIdempotentRepository(RedisTemplate<String, String> redisTemplate, String repositoryName) {
        this.setOperations = redisTemplate.opsForSet();
        this.repositoryName = repositoryName;
        this.redisTemplate = redisTemplate;
    }

    public SpringRedisIdempotentRepository(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public static SpringRedisIdempotentRepository redisIdempotentRepository(String processorName) {
        return new SpringRedisIdempotentRepository(processorName);
    }

    public static SpringRedisIdempotentRepository redisIdempotentRepository(
            RedisTemplate<String, String> redisTemplate, String processorName) {
        return new SpringRedisIdempotentRepository(redisTemplate, processorName);
    }

    public boolean isFlushOnStartup() {
        return flushOnStartup;
    }

    /**
     * Delete all keys of the currently selected database. Be careful if enabling this as all existing data will be
     * deleted.
     */
    public void setFlushOnStartup(boolean flushOnStartup) {
        this.flushOnStartup = flushOnStartup;
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        if (!contains(key)) {
            return setOperations.add(repositoryName, key) != null;
        } else {
            return false;
        }
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return setOperations.isMember(repositoryName, key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        return setOperations.remove(repositoryName, key) != null;
    }

    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    @ManagedAttribute(description = "The repository name")
    public String getRepositoryName() {
        return repositoryName;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        if (redisConfiguration == null && this.redisTemplate == null) {
            // create configuration if no custom template has been configured
            redisConfiguration = new RedisConfiguration();
        }
        if (this.redisTemplate == null) {
            this.redisTemplate = (RedisTemplate<String, String>) redisConfiguration.getRedisTemplate();
        }
        ObjectHelper.notNull(this.redisTemplate, "redisTemplate", this);
        this.setOperations = redisTemplate.opsForSet();
        if (flushOnStartup) {
            redisTemplate.getConnectionFactory().getConnection().flushDb();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (redisConfiguration != null) {
            redisConfiguration.stop();
        }
    }
}
