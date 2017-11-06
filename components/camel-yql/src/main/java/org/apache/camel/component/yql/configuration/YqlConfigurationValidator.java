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
package org.apache.camel.component.yql.configuration;

import org.apache.camel.component.yql.exception.YqlException;
import org.apache.commons.lang3.StringUtils;

public final class YqlConfigurationValidator {

    private YqlConfigurationValidator() {
    }

    public static void validateProperties(final YqlConfiguration configuration) {
        if (StringUtils.isEmpty(configuration.getQuery())) {
            throw new YqlException("<query> is not present or not valid!");
        }

        if (!StringUtils.equalsAny(configuration.getFormat(), "json", "xml")) {
            throw new YqlException("<format> is not valid!");
        }

        if (configuration.getCrossProduct() != null && !configuration.getCrossProduct().equals("optimized")) {
            throw new YqlException("<crossProduct> is not valid!");
        }

        if (configuration.getJsonCompat() != null && !configuration.getJsonCompat().equals("new")) {
            throw new YqlException("<jsonCompat> is not valid!");
        }
    }
}
