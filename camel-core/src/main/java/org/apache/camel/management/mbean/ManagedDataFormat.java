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
package org.apache.camel.management.mbean;

import java.util.List;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedDataFormatMBean;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed DataFormat")
public class ManagedDataFormat implements ManagedInstance, ManagedDataFormatMBean {
    private final CamelContext camelContext;
    private final DataFormat dataFormat;

    public ManagedDataFormat(CamelContext camelContext, DataFormat dataFormat) {
        this.camelContext = camelContext;
        this.dataFormat = dataFormat;
    }

    public void init(ManagementStrategy strategy) {
        // noop
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public CamelContext getContext() {
        return camelContext;
    }

    @Override
    public String getName() {
        if (dataFormat instanceof DataFormatName) {
            return ((DataFormatName) dataFormat).getDataFormatName();
        }
        return null;
    }

    @Override
    public String getCamelId() {
        return camelContext.getName();
    }

    @Override
    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    @Override
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (dataFormat instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) dataFormat).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @Override
    public String informationJson() {
        String dataFormatName = getName();
        if (dataFormatName != null) {
            return camelContext.explainDataFormatJson(dataFormatName, dataFormat, true);
        } else {
            return null;
        }
    }

    @Override
    public TabularData explain(boolean allOptions) {
        String dataFormatName = getName();
        if (dataFormatName != null) {
            try {
                TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.explainDataFormatTabularType());

                String json = camelContext.explainDataFormatJson(dataFormatName, dataFormat, allOptions);
                List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("properties", json, true);

                for (Map<String, String> row : rows) {
                    String name = row.get("name");
                    String kind = row.get("kind");
                    String label = row.get("label") != null ? row.get("label") : "";
                    String type = row.get("type");
                    String javaType = row.get("javaType");
                    String deprecated = row.get("deprecated") != null ? row.get("deprecated") : "";
                    String secret = row.get("secret") != null ? row.get("secret") : "";
                    String value = row.get("value") != null ? row.get("value") : "";
                    String defaultValue = row.get("defaultValue") != null ? row.get("defaultValue") : "";
                    String description = row.get("description") != null ? row.get("description") : "";

                    CompositeType ct = CamelOpenMBeanTypes.explainDataFormatsCompositeType();
                    CompositeData data = new CompositeDataSupport(ct,
                            new String[]{"option", "kind", "label", "type", "java type", "deprecated", "secret", "value", "default value", "description"},
                            new Object[]{name, kind, label, type, javaType, deprecated, secret, value, defaultValue, description});
                    answer.put(data);
                }

                return answer;
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        } else {
            return null;
        }
    }

    @Override
    public DataFormat getInstance() {
        return dataFormat;
    }

}
