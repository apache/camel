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
package org.apache.camel.component.web3j;

import java.math.BigInteger;

import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;

public final class Web3jHelper {

    private Web3jHelper() {
    }

    public static DefaultBlockParameter toDefaultBlockParameter(String block) {
        DefaultBlockParameter defaultBlockParameter = null;
        if (block != null) {
            for (DefaultBlockParameterName defaultBlockParameterName: DefaultBlockParameterName.values()) {
                if (block.equalsIgnoreCase(defaultBlockParameterName.getValue())) {
                    defaultBlockParameter = defaultBlockParameterName;
                }
            }

            if (defaultBlockParameter == null) {
                defaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(block));
            }
        }

        return defaultBlockParameter;
    }

}
