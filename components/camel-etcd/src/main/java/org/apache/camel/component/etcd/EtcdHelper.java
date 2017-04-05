/**
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
package org.apache.camel.component.etcd;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EtcdHelper  {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdHelper.class);
    private static final String OUTDATED_EVENT_MSG = "requested index is outdated and cleared";

    private EtcdHelper() {
    }

    public static boolean isOutdatedIndexException(EtcdException exception) {
        if (exception.isErrorCode(EtcdErrorCode.EventIndexCleared) && exception.etcdMessage != null) {
            return exception.etcdMessage.toLowerCase().contains(OUTDATED_EVENT_MSG);
        }

        return false;
    }

    public static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static void setIndex(AtomicLong index, EtcdKeysResponse response) {
        if (response != null && response.node != null) {
            index.set(response.node.modifiedIndex + 1);
            LOGGER.debug("Index received={}, next={}", response.node.modifiedIndex, index.get());
        } else {
            index.set(response.etcdIndex + 1);
            LOGGER.debug("Index received={}, next={}", response.node.modifiedIndex, index.get());
        }
    }

    public static URI[] resolveURIs(CamelContext camelContext, String uriList) throws Exception {
        String[] uris;
        if (uriList != null) {
            uris = uriList.split(",");
        } else {
            uris = EtcdConstants.ETCD_DEFAULT_URIS.split(",");
        }

        URI[] etcdUriList = new URI[uris.length];

        for (int i = 0; i < uris.length; i++) {
            etcdUriList[i] = camelContext != null
                ? URI.create(camelContext.resolvePropertyPlaceholders(uris[i]))
                : URI.create(uris[i]);
        }

        return etcdUriList;
    }
}
