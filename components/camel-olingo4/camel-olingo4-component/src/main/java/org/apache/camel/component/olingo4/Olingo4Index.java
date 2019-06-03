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
package org.apache.camel.component.olingo4;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.core.domain.ClientEntitySetImpl;

public class Olingo4Index {

    private Set<Integer> resultIndex = new HashSet<>();

    private Object filter(Object o) {
        if (o == null || resultIndex.contains(o.hashCode())) {
            return null;
        }
        return o;
    }

    private void indexDefault(Object o) {
        if (o == null) {
            return;
        }

        resultIndex.add(o.hashCode());
    }

    private Iterable<?> filter(Iterable<?> iterable) {
        List<Object> filtered = new ArrayList<>();
        if (iterable == null) {
            return filtered;
        }

        for (Object o : iterable) {
            if (resultIndex.contains(o.hashCode())) {
                continue;
            }
            filtered.add(o);
        }

        return filtered;
    }

    private void index(Iterable<?> iterable) {
        if (iterable == null) {
            return;
        }

        for (Object o : iterable) {
            resultIndex.add(o.hashCode());
        }
    }

    private ClientEntitySet filter(ClientEntitySet entitySet) {
        if (entitySet == null) {
            return new ClientEntitySetImpl();
        }

        List<ClientEntity> entities = entitySet.getEntities();

        if (entities.isEmpty()) {
            return entitySet;
        }

        List<ClientEntity> copyEntities = new ArrayList<>();
        copyEntities.addAll(entities);

        for (ClientEntity entity : copyEntities) {
            if (resultIndex.contains(entity.hashCode())) {
                entities.remove(entity);
            }
        }

        return entitySet;
    }

    private void index(ClientEntitySet entitySet) {
        if (entitySet == null) {
            return;
        }

        for (ClientEntity entity : entitySet.getEntities()) {
            resultIndex.add(entity.hashCode());
        }
    }

    /**
     * Index the results
     */
    public void index(Object result) {
        if (result instanceof ClientEntitySet) {
            index((ClientEntitySet)result);
        } else if (result instanceof Iterable) {
            index((Iterable<?>)result);
        } else {
            indexDefault(result);
        }
    }

    @SuppressWarnings("unchecked")
    public Object filterResponse(Object response) {
        if (response instanceof ClientEntitySet) {
            response = filter((ClientEntitySet)response);
        } else if (response instanceof Iterable) {
            response = filter((Iterable<Object>)response);
        } else if (response.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            final int size = Array.getLength(response);
            for (int i = 0; i < size; i++) {
                result.add(Array.get(response, i));
            }
            response = filter(result);
        } else {
            response = filter(response);
        }

        return response;
    }
}
