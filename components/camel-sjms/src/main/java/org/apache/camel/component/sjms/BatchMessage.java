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
package org.apache.camel.component.sjms;

import java.util.List;
import java.util.Map;

/**
 * A {@link List} of these objects can be used to batch a collection of bodies and
 * header pairs in one exchange.
 * <p/>
 * <b>Important:</b> This BatchMessage is only supported by <tt>InOnly</tt> messaging style
 * (eg you cannot do request/reply with this BatchMessage)
 *
 * @deprecated do not use, its being removed in a future Camel release
 */
@Deprecated
public class BatchMessage<T> {
    private T payload;
    private Map<String, Object> headers;

    /**
     * @param payload may not be null
     * @param headers may be null
     */
    public BatchMessage(T payload, Map<String, Object> headers) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload may not be null");
        } else {
            this.payload = payload;
        }
        this.headers = headers;
    }

    public T getPayload() {
        return payload;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((headers == null) ? 0 : headers.hashCode());
        result = prime * result + ((payload == null) ? 0 : payload.hashCode());
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof BatchMessage)) {
            return false;
        }
        BatchMessage other = (BatchMessage) obj;
        if (headers == null) {
            if (other.headers != null) {
                return false;
            }
        } else if (!headers.equals(other.headers)) {
            return false;
        }
        if (payload == null) {
            if (other.payload != null) {
                return false;
            }
        } else if (!payload.equals(other.payload)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "BatchMessage [payload=" + payload + ", headers=" + headers + "]";
    }
}
