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
package org.apache.camel.component.milvus.helpers;

import io.milvus.param.dml.DeleteParam;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;

/**
 * A Camel {@link Processor} that builds a Milvus {@link io.milvus.param.dml.DeleteParam} from simple string properties
 * and sets it as the exchange body together with the {@link MilvusAction#DELETE} header.
 */
public class MilvusHelperDelete implements Processor {

    private String collectionName = "default_collection";
    private String filter;

    @Override
    public void process(Exchange exchange) throws Exception {
        if (filter == null || filter.isEmpty()) {
            throw new IllegalArgumentException(
                    "A filter expression is required for delete operations (e.g., \"id in [1, 2, 3]\")");
        }

        DeleteParam param = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(filter)
                .build();

        exchange.getIn().setBody(param);
        exchange.getIn().setHeader(MilvusHeaders.ACTION, MilvusAction.DELETE);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getFilter() {
        return filter;
    }

    /**
     * @param filter a Milvus boolean expression to select entities to delete (e.g., {@code id in [1, 2, 3]},
     *               {@code age > 18})
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }
}
