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
package org.apache.camel.component.atom;

import com.apptasticsoftware.rssreader.Item;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

/**
 *
 */
public class RepositoryGuidIdempotentStrategy implements AtomIdempotentStrategy {

    IdempotentRepository idempotentRepository = null;

    public RepositoryGuidIdempotentStrategy() {
        this(null);
    }

    public RepositoryGuidIdempotentStrategy(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    @Override
    public boolean isValidItem(Item entry) {
        boolean valid = true;
        if (entry != null && entry.getGuid().isPresent()) {
            valid = getIdempotentRepository().add(entry.getGuid().get());
        }
        return valid;
    }

    protected IdempotentRepository getIdempotentRepository() {
        if (this.idempotentRepository == null) {
            this.idempotentRepository = MemoryIdempotentRepository.memoryIdempotentRepository();
        }
        return this.idempotentRepository;
    }

    protected RepositoryGuidIdempotentStrategy setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
        return this;
    }
}
