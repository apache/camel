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
package org.apache.camel.component.digitalocean.producer;

import com.myjeeva.digitalocean.pojo.Delete;
import com.myjeeva.digitalocean.pojo.Key;
import com.myjeeva.digitalocean.pojo.Keys;
import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for SSH Keys API.
 */
public class DigitalOceanKeysProducer extends DigitalOceanProducer {

    public DigitalOceanKeysProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    public void process(Exchange exchange) throws Exception {

        switch (determineOperation(exchange)) {

        case list:
            getKeys(exchange);
            break;
        case create:
            createKey(exchange);
            break;
        case get:
            getKey(exchange);
            break;
        case update:
            updateKey(exchange);
            break;
        case delete:
            deleteKey(exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }


    private void getKey(Exchange exchange) throws Exception {
        Integer keyId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);
        String fingerprint = exchange.getIn().getHeader(DigitalOceanHeaders.KEY_FINGERPRINT, String.class);
        Key key;

        if (ObjectHelper.isNotEmpty(keyId)) {
            key = getEndpoint().getDigitalOceanClient().getKeyInfo(keyId);
        } else if (ObjectHelper.isNotEmpty(fingerprint)) {
            key = getEndpoint().getDigitalOceanClient().getKeyInfo(fingerprint);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.KEY_FINGERPRINT + " must be specified");
        }
        LOG.trace("Key [{}] ", key);
        exchange.getOut().setBody(key);
    }

    private void getKeys(Exchange exchange) throws Exception {
        Keys keys = getEndpoint().getDigitalOceanClient().getAvailableKeys(configuration.getPage());
        LOG.trace("All Keys : page {} [{}] ", configuration.getPage(), keys.getKeys());
        exchange.getOut().setBody(keys.getKeys());
    }

    private void deleteKey(Exchange exchange) throws Exception {
        Integer keyId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);
        String fingerprint = exchange.getIn().getHeader(DigitalOceanHeaders.KEY_FINGERPRINT, String.class);
        Delete delete;

        if (ObjectHelper.isNotEmpty(keyId)) {
            delete = getEndpoint().getDigitalOceanClient().deleteKey(keyId);
        } else if (ObjectHelper.isNotEmpty(fingerprint)) {
            delete = getEndpoint().getDigitalOceanClient().deleteKey(fingerprint);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.KEY_FINGERPRINT + " must be specified");
        }

        LOG.trace("Delete Key {} ", delete);
        exchange.getOut().setBody(delete);
    }


    private void createKey(Exchange exchange) throws Exception {
        Key key = new Key();

        String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");

        } else {
            key.setName(name);
        }

        String publicKey = exchange.getIn().getHeader(DigitalOceanHeaders.KEY_PUBLIC_KEY, String.class);

        if (ObjectHelper.isEmpty(publicKey)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.KEY_PUBLIC_KEY + " must be specified");
        } else {
            key.setPublicKey(publicKey);
        }

        key = getEndpoint().getDigitalOceanClient().createKey(key);
        LOG.trace("Key created {} ", key);
        exchange.getOut().setBody(key);

    }

    private void updateKey(Exchange exchange) throws Exception {
        Integer keyId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);
        String fingerprint = exchange.getIn().getHeader(DigitalOceanHeaders.KEY_FINGERPRINT, String.class);
        Key key;

        String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(keyId)) {
            key = getEndpoint().getDigitalOceanClient().updateKey(keyId, name);
        } else if (ObjectHelper.isNotEmpty(fingerprint)) {
            key = getEndpoint().getDigitalOceanClient().updateKey(fingerprint, name);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.KEY_FINGERPRINT + " must be specified");
        }

        LOG.trace("Update Key [{}] ", key);
        exchange.getOut().setBody(key);
    }


}
