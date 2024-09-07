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
package org.apache.camel.spi;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * An optional interface an {@link Endpoint} may choose to implement which allows it to expose a way of browsing the
 * exchanges available.
 */
public interface BrowsableEndpoint extends Endpoint {

    /**
     * Return the exchanges available on this endpoint
     *
     * @return the exchanges on this endpoint
     */
    List<Exchange> getExchanges();

    /**
     * Return the exchanges available on this endpoint, allowing to filter the
     * result in the specific component.
     *
     * @param limit  to limit the result o a specific maximum. Use 0 for no limit.
     * @param filter filter to filter among the messages to include.
     * @return the exchanges on this endpoint
     */
    default List<Exchange> getExchanges(int limit, Predicate filter) {
        List<Exchange> answer = getExchanges();
        if (filter != null) {
            answer = (List<Exchange>) answer.stream().filter(filter).collect(Collectors.toList());
        }
        if (limit > 0) {
            answer = answer.stream().limit(limit).toList();
        }
        return answer;
    }
}
