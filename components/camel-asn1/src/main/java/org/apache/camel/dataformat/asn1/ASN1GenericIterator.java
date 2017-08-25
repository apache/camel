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
package org.apache.camel.dataformat.asn1;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Iterator;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ASN1GenericIterator<T> implements Iterator<T>, Closeable {
    static final Logger LOGGER = LoggerFactory.getLogger(ASN1GenericIterator.class);

    private volatile ASN1InputStream asn1InputStream;
    private volatile T parent;
    private Class<T> clazz;

    public ASN1GenericIterator(Class<T> clazz, InputStream inputStream) {
        if (inputStream instanceof ASN1InputStream) {
            this.asn1InputStream = (ASN1InputStream)inputStream;
        } else {
            this.asn1InputStream = new ASN1InputStream(inputStream);
        }
        this.parent = null;
        this.clazz = clazz;
    }

    @Override
    public boolean hasNext() {
        try {
            if (asn1InputStream == null) {
                return false;
            }
            boolean availableDataInCurrentEntry = asn1InputStream.available() > 0;
            if (!availableDataInCurrentEntry) {
                // advance to the next entry.
                parent = getNextElement();
                if (parent == null) {
                    asn1InputStream.close();
                    availableDataInCurrentEntry = false;
                } else {
                    availableDataInCurrentEntry = true;
                }
            }
            return availableDataInCurrentEntry;
        } catch (IOException exception) {
            // Just wrap the IOException as CamelRuntimeException
            throw new RuntimeCamelException(exception);
        }
    }

    private T getNextElement() {
        if (asn1InputStream == null) {
            return null;
        }

        try {
            ASN1Primitive current = getNextEntry();

            if (current != null) {
                T instance = ObjectHelper.cast(clazz, createGenericTypeObject(current, clazz));
                return instance;
            } else {
                LOGGER.trace("close asn1InputStream");
                return null;
            }
        } catch (Throwable exception) {
            // Just wrap the IOException as CamelRuntimeException
            throw new RuntimeCamelException(exception);
        }
    }

    @SuppressWarnings("rawtypes")
    private Object createGenericTypeObject(ASN1Primitive current, Class<T> clazz2) throws Throwable {
        Class<?>[] paramIS = new Class[1];
        paramIS[0] = InputStream.class;

        Method m = clazz.getDeclaredMethod("decode", paramIS);

        InputStream is = new ByteArrayInputStream(current.getEncoded());
        Object clazzInstance = clazz.newInstance();
        m.invoke(clazzInstance, is);

        return clazzInstance;
    }

    private ASN1Primitive getNextEntry() throws IOException {
        return asn1InputStream.readObject();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T next() {
        if (parent == null) {
            parent = getNextElement();
        }
        T answer = parent;
        parent = null;
        checkNullAnswer(answer);

        return answer;
    }

    private void checkNullAnswer(T answer) {
        if (answer == null && asn1InputStream != null) {
            IOHelper.close(asn1InputStream);
            asn1InputStream = null;
        }
    }

    @Override
    public void close() throws IOException {
        IOHelper.close(asn1InputStream);
        asn1InputStream = null;
    }

}
