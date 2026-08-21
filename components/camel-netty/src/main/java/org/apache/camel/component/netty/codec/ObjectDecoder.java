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
package org.apache.camel.component.netty.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.serialization.ClassResolver;
import org.apache.camel.support.DeserializationFilterHelper;

/**
 * Decodes Java-serialized objects from Netty frames, applying a JEP-290 {@link ObjectInputFilter} as a defense-in-depth
 * measure against unsafe deserialization.
 * <p>
 * Compatible with Netty's {@code ObjectEncoder} compact wire format. The filter is resolved through
 * {@link DeserializationFilterHelper}: when an explicit {@code deserializationFilter} pattern is supplied it is
 * applied, otherwise the JVM-wide {@code jdk.serialFilter} is honoured if set, and failing that the shared Camel
 * default allow-list ({@link DeserializationFilterHelper#DEFAULT_DESERIALIZATION_FILTER}) is applied. A filter is
 * therefore always installed.
 */
public class ObjectDecoder extends LengthFieldBasedFrameDecoder {
    private static final int DEFAULT_MAX_OBJECT_SIZE = 1048576;

    // Matches Netty's CompactObjectOutputStream type constants
    private static final int TYPE_FAT_DESCRIPTOR = 0;
    private static final int TYPE_THIN_DESCRIPTOR = 1;

    private final ClassResolver classResolver;
    private final ObjectInputFilter deserializationFilter;

    public ObjectDecoder(ClassResolver classResolver) {
        this(classResolver, null);
    }

    public ObjectDecoder(ClassResolver classResolver, String deserializationFilter) {
        super(DEFAULT_MAX_OBJECT_SIZE, 0, 4, 0, 4);
        this.classResolver = classResolver;
        this.deserializationFilter = DeserializationFilterHelper.resolveDeserializationFilter(deserializationFilter);
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        ObjectInputStream ois = new CompactFilteringObjectInputStream(
                new ByteBufInputStream(frame, true), classResolver);
        ois.setObjectInputFilter(deserializationFilter);
        try {
            return ois.readObject();
        } finally {
            ois.close();
        }
    }

    /**
     * ObjectInputStream that understands Netty's compact class descriptor wire format (compatible with
     * {@code CompactObjectOutputStream}) and resolves classes via a Netty {@link ClassResolver}.
     */
    private static class CompactFilteringObjectInputStream extends ObjectInputStream {
        private final ClassResolver classResolver;

        CompactFilteringObjectInputStream(InputStream in, ClassResolver classResolver) throws IOException {
            super(in);
            this.classResolver = classResolver;
        }

        @Override
        protected void readStreamHeader() throws IOException {
            // Netty's CompactObjectOutputStream writes a single version byte instead of the
            // standard 4-byte Java stream header (ACED 0005)
            int version = readByte();
            if (version != 5) {
                throw new StreamCorruptedException(
                        "Unsupported version: " + version);
            }
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            int type = read();
            if (type < 0) {
                throw new EOFException();
            }
            switch (type) {
                case TYPE_FAT_DESCRIPTOR:
                    return super.readClassDescriptor();
                case TYPE_THIN_DESCRIPTOR:
                    String className = readUTF();
                    Class<?> clazz = classResolver.resolve(className);
                    return ObjectStreamClass.lookupAny(clazz);
                default:
                    throw new StreamCorruptedException("Unexpected class descriptor type: " + type);
            }
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return classResolver.resolve(desc.getName());
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }
}
