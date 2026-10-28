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
package org.apache.camel.component.kudu;

import org.apache.camel.RuntimeCamelException;
import org.apache.kudu.client.RowError;

/**
 * Thrown by the producer when the response of an insert, delete, update or upsert carries a row error. There are two
 * cases, and the {@link RowError#getErrorStatus() status} of the row error tells them apart:
 * <ul>
 * <li>a row that the tablet server rejected, for example because its key is already present (insert) or because the row
 * is not found (update, delete): the row was not written;</li>
 * <li>a request that failed, for example because it timed out or because no tablet server could be reached. The Kudu
 * client reports such a request the same way in the default flush mode of a session. In this case it is not known
 * whether the row was written.</li>
 * </ul>
 * The message holds the status only. It does not include the row, because the text of a {@link RowError} prints every
 * column value of the row; {@link #getRowError()} gives access to it.
 */
public class KuduRowErrorException extends RuntimeCamelException {

    private static final long serialVersionUID = 1L;

    private final transient RowError rowError;

    public KuduRowErrorException(RowError rowError) {
        super("Kudu reported a row error for the operation: " + rowError.getErrorStatus());
        this.rowError = rowError;
    }

    /**
     * The row error of the Kudu client; its {@link RowError#getErrorStatus() status} tells the kind of error (already
     * present, not found, timed out, network error, ...). It is not serialized with the exception.
     */
    public RowError getRowError() {
        return rowError;
    }
}
