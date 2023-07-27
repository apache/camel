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
package org.apache.camel.component.consul;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.SessionCreatedResponse;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;

/**
 * Apache Camel Plug-in for Consul Registry (Objects stored under kv/key as well as bookmarked under kv/[type]/key to
 * avoid iteration over types)
 */
public class ConsulRegistry implements Registry {

    private String hostname = "localhost";
    private int port = 8500;
    private Consul consul;
    private KeyValueClient kvClient;

    /* constructor with default port */
    public ConsulRegistry(String hostname) {
        this(hostname, 8500);
    }

    /* constructor (since spring.xml does not support builder pattern) */
    public ConsulRegistry(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        this.consul = Consul.builder().withUrl("http://" + this.hostname + ":" + this.port).build();
    }

    /* builder pattern */
    private ConsulRegistry(Builder builder) {
        this.hostname = builder.hostname;
        this.port = builder.port;
        this.consul = Consul.builder().withUrl("http://" + this.hostname + ":" + this.port).build();
    }

    @Override
    public Object lookupByName(String key) {
        // Substitute $ character in key
        key = key.replace('$', '/');
        kvClient = consul.keyValueClient();

        return kvClient.getValueAsString(key).map(result -> {
            byte[] postDecodedValue = ConsulRegistryUtils.decodeBase64(result);
            return ConsulRegistryUtils.deserialize(postDecodedValue);
        }).orElse(null);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object object = lookupByName(name);
        if (object == null) {
            return null;
        }
        try {
            return type.cast(object);
        } catch (Exception e) {
            String msg = "Found bean: " + name + " in Consul Registry: " + this + " of type: " + object.getClass().getName()
                         + "expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> result = new HashMap<>();
        // encode $ signs as they occur in subclass types
        String keyPrefix = type.getName().replace('$', '/');
        kvClient = consul.keyValueClient();

        List<String> keys;
        try {
            keys = kvClient.getKeys(keyPrefix);
        } catch (ConsulException e) {
            return result;
        }

        if (keys != null) {
            Object obj;

            for (String key : keys) {
                // change bookmark back into actual key
                key = key.substring(key.lastIndexOf('/') + 1);
                obj = lookupByName(key.replace('$', '/'));
                if (type.isInstance(obj)) {
                    result.put(key, type.cast(obj));
                }
            }
        }
        return result;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        String keyPrefix = type.getName().replace('$', '/');
        Set<T> result = new HashSet<>();

        List<String> keys;
        try {
            keys = kvClient.getKeys(keyPrefix);
        } catch (ConsulException e) {
            return result;
        }

        if (keys != null) {
            Object obj;

            for (String key : keys) {
                // change bookmark back into actual key
                key = key.substring(key.lastIndexOf('/') + 1);
                obj = lookupByName(key.replace('$', '/'));
                if (type.isInstance(obj)) {
                    result.add(type.cast(obj));
                }
            }
        }
        return result;
    }

    @Override
    public void bind(String id, Class<?> type, Object bean) throws RuntimeCamelException {
        put(id, bean);
    }

    @Override
    public void bind(String id, Class<?> type, Supplier<Object> bean) throws RuntimeCamelException {
        throw new UnsupportedOperationException("Binding with supplier not supported");
    }

    @Override
    public void bindAsPrototype(String id, Class<?> type, Supplier<Object> bean) throws RuntimeCamelException {
        throw new UnsupportedOperationException("Binding with supplier not supported");
    }

    public void remove(String key) {
        // create session to avoid conflicts (not sure if that is safe enough)
        SessionClient sessionClient = consul.sessionClient();
        String sessionName = "session_" + UUID.randomUUID().toString();

        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(sessionName).build());
        String sessionId = response.getId();
        kvClient = consul.keyValueClient();
        String lockKey = "lock_" + key;
        kvClient.acquireLock(lockKey, sessionName, sessionId);
        Object object = lookupByName(key);
        if (object == null) {
            String msg = "Bean with key '" + key + "' did not exist in Consul Registry.";
            throw new NoSuchBeanException(msg);
        }
        kvClient.deleteKey(key);
        kvClient.deleteKey(object.getClass().getName() + "/" + key);
        kvClient.releaseLock(lockKey, sessionId);
    }

    public void put(String key, Object object) {
        // Substitute $ character in key
        key = key.replace('$', '/');
        // create session to avoid conflicts
        // (not sure if that is safe enough, again)
        SessionClient sessionClient = consul.sessionClient();
        String sessionName = "session_" + UUID.randomUUID().toString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(sessionName).build());
        String sessionId = response.getId();
        kvClient = consul.keyValueClient();
        String lockKey = "lock_" + key;
        kvClient.acquireLock(lockKey, sessionName, sessionId);

        // Allow only unique keys, last one wins
        if (lookupByName(key) != null) {
            remove(key);
        }
        Object clone = ConsulRegistryUtils.clone((Serializable) object);
        byte[] serializedObject = ConsulRegistryUtils.serialize((Serializable) clone);
        // pre-encode due native encoding issues
        String value = ConsulRegistryUtils.encodeBase64(serializedObject);
        // store the actual class
        kvClient.putValue(key, value);
        // store just as a bookmark
        kvClient.putValue(object.getClass().getName().replace('$', '/') + "/" + key, "1");
        kvClient.releaseLock(lockKey, sessionId);
    }

    public static class Builder {
        // required parameter
        String hostname;
        // optional parameter
        Integer port = 8500;

        public Builder(String hostname) {
            this.hostname = hostname;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public ConsulRegistry build() {
            return new ConsulRegistry(this);
        }
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    static final class ConsulRegistryUtils {

        private ConsulRegistryUtils() {

        }

        /**
         * Decodes using Base64.
         *
         * @param  base64String the {@link String} to decode
         * @return              a decoded data as a byte array
         */
        static byte[] decodeBase64(final String base64String) {
            return Base64.getDecoder().decode(base64String.getBytes(StandardCharsets.ISO_8859_1));
        }

        /**
         * Encodes using Base64.
         *
         * @param  binaryData the data to encode
         * @return            an encoded data as a {@link String}
         */
        static String encodeBase64(final byte[] binaryData) {
            final byte[] encoded = Base64.getEncoder().encode(binaryData);
            return new String(encoded, StandardCharsets.ISO_8859_1);
        }

        /**
         * Deserializes an object out of the given byte array.
         *
         * @param  bytes the byte array to deserialize from
         * @return       an {@link Object} deserialized from the given byte array
         */
        static Object deserialize(byte[] bytes) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeCamelException(e);
            }
        }

        /**
         * A deep serialization based clone
         *
         * @param  object the object to clone
         * @return        a deep clone
         */
        static Object clone(Serializable object) {
            return deserialize(serialize(object));
        }

        /**
         * Serializes the given {@code serializable} using Java Serialization
         *
         * @param  serializable
         * @return              the serialized object as a byte array
         */
        static byte[] serialize(Serializable serializable) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
                 ObjectOutputStream out = new ObjectOutputStream(baos)) {
                out.writeObject(serializable);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

}
