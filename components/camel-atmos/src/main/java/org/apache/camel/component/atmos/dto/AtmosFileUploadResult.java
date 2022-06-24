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
package org.apache.camel.component.atmos.dto;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.atmos.util.AtmosConstants;
import org.apache.camel.component.atmos.util.AtmosResultCode;

public class AtmosFileUploadResult extends AtmosResult {

    /**
     * Object payload contained in Exchange In case of a single file Exchange Header is populated with the name of the
     * remote path uploaded In case of a multiple files Exchange Header is populated with the name of the remote paths
     * uploaded In case of a single file Exchange Body is populated with the result code of the upload operation for the
     * remote path. In case of multiple files Exchange Body is populated with a map containing as key the remote path
     * uploaded and as value the result code of the upload operation
     * 
     * @param exchange
     */
    @Override
    public void populateExchange(Exchange exchange) {
        Map<String, AtmosResultCode> map = (Map<String, AtmosResultCode>) resultEntries;
        if (map.size() == 1) {
            //set info in exchange (note: it runs only once)
            map.forEach((pathExtracted, codeExtracted) -> {
                exchange.getIn().setHeader(AtmosConstants.UPLOADED_FILE, pathExtracted);
                exchange.getIn().setBody(codeExtracted.name());
            });

        } else {
            final String pathsExtracted = String.join("\n", map.keySet());

            exchange.getIn().setHeader(AtmosConstants.UPLOADED_FILES, pathsExtracted);
            exchange.getIn().setBody(map);
        }
    }
}
