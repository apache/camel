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
package org.apache.camel.component.swagger;

import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.jaxrs.listing.ApiListingResource;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.model.ApiListing;
import scala.Option;

public class TestMe {

    public static void main(String[] args) throws Exception {
        //use the following as another option
//        BeanConfig bean = new BeanConfig();
//        bean.setResourcePackage("org.apache.camel.component.swagger");
//        bean.setBasePath("http://localhost:8080/spring");
//        bean.setVersion("1.0");
//        bean.setScan(true);

        SwaggerConfig config = new SwaggerConfig();
        DefaultJaxrsApiReader reader = new DefaultJaxrsApiReader();
        Option<ApiListing> api = reader.read("", MyDemo.class, config);
        if (api != null) {
            ApiListing list = api.get();
            System.out.println(list);
        }


    }
}
