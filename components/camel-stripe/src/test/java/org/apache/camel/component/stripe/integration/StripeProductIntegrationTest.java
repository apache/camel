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
package org.apache.camel.component.stripe.integration;

import java.util.HashMap;
import java.util.Map;

import com.stripe.model.Price;
import com.stripe.model.Product;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stripe.StripeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Stripe Product and Price operations.
 *
 * To run this test: mvn verify -Dtest=StripeProductIntegrationTest -DSTRIPE_API_KEY=sk_test_...
 */
@EnabledIfSystemProperty(named = "STRIPE_API_KEY", matches = ".*")
public class StripeProductIntegrationTest extends StripeIntegrationTestSupport {

    @Test
    public void testCreateProduct() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Test Product");
        params.put("description", "Integration test product");
        params.put("active", true);

        Product product = template.requestBody("direct:createProduct", params, Product.class);

        assertNotNull(product);
        assertNotNull(product.getId());
        assertTrue(product.getId().startsWith("prod_"));
        assertEquals("Test Product", product.getName());
        assertEquals("Integration test product", product.getDescription());
        assertTrue(product.getActive());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteProduct", null, StripeConstants.OBJECT_ID, product.getId());
    }

    @Test
    public void testUpdateProduct() throws Exception {
        // Create a product
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("name", "Original Product");

        Product created = template.requestBody("direct:createProduct", createParams, Product.class);
        assertNotNull(created.getId());

        // Update the product
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("name", "Updated Product");
        updateParams.put("description", "Updated description");

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        Product updated = template.requestBodyAndHeaders("direct:updateProduct", updateParams,
                headers, Product.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("Updated Product", updated.getName());
        assertEquals("Updated description", updated.getDescription());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteProduct", null, StripeConstants.OBJECT_ID, created.getId());
    }

    @Test
    public void testCreatePrice() throws Exception {
        // First create a product
        Map<String, Object> productParams = new HashMap<>();
        productParams.put("name", "Price Test Product");

        Product product = template.requestBody("direct:createProduct", productParams, Product.class);
        assertNotNull(product.getId());

        // Create a price for the product
        Map<String, Object> priceParams = new HashMap<>();
        priceParams.put("product", product.getId());
        priceParams.put("currency", "usd");
        priceParams.put("unit_amount", 2000L); // $20.00

        Price price = template.requestBody("direct:createPrice", priceParams, Price.class);

        assertNotNull(price);
        assertNotNull(price.getId());
        assertTrue(price.getId().startsWith("price_"));
        assertEquals("usd", price.getCurrency());
        assertEquals(2000L, price.getUnitAmount().longValue());
        assertEquals(product.getId(), price.getProduct());
        assertTrue(price.getActive());

        // Cleanup - archive product instead of delete since it has prices
        Map<String, Object> archiveParams = new HashMap<>();
        archiveParams.put("active", false);
        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, product.getId());
        template.requestBodyAndHeaders("direct:updateProduct", archiveParams, headers, Product.class);
    }

    @Test
    public void testRetrievePrice() throws Exception {
        // Create a product
        Map<String, Object> productParams = new HashMap<>();
        productParams.put("name", "Retrieve Price Test");

        Product product = template.requestBody("direct:createProduct", productParams, Product.class);

        // Create a price
        Map<String, Object> priceParams = new HashMap<>();
        priceParams.put("product", product.getId());
        priceParams.put("currency", "usd");
        priceParams.put("unit_amount", 1500L);

        Price created = template.requestBody("direct:createPrice", priceParams, Price.class);
        assertNotNull(created.getId());

        // Retrieve the price
        Price retrieved = template.requestBodyAndHeader("direct:retrievePrice", null,
                StripeConstants.OBJECT_ID, created.getId(), Price.class);

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(1500L, retrieved.getUnitAmount().longValue());

        // Cleanup - archive product instead of delete since it has prices
        Map<String, Object> archiveParams = new HashMap<>();
        archiveParams.put("active", false);
        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, product.getId());
        template.requestBodyAndHeaders("direct:updateProduct", archiveParams, headers, Product.class);
    }

    @Test
    public void testProductWithMetadata() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Metadata Product");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("internal_id", "PROD-12345");
        metadata.put("category", "electronics");
        params.put("metadata", metadata);

        Product product = template.requestBody("direct:createProduct", params, Product.class);

        assertNotNull(product);
        assertNotNull(product.getMetadata());
        assertEquals("PROD-12345", product.getMetadata().get("internal_id"));
        assertEquals("electronics", product.getMetadata().get("category"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteProduct", null, StripeConstants.OBJECT_ID, product.getId());
    }

    @Test
    public void testCreateProductWithJson() throws Exception {
        String json = """
                {
                    "name": "JSON Product",
                    "description": "Product created with JSON",
                    "active": true
                }
                """;

        Product product = template.requestBody("direct:createProduct", json, Product.class);

        assertNotNull(product);
        assertNotNull(product.getId());
        assertTrue(product.getId().startsWith("prod_"));
        assertEquals("JSON Product", product.getName());
        assertEquals("Product created with JSON", product.getDescription());
        assertTrue(product.getActive());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteProduct", null, StripeConstants.OBJECT_ID, product.getId());
    }

    @Test
    public void testCreateProductAndPriceWithJson() throws Exception {
        // Create product with JSON
        String productJson = """
                {
                    "name": "JSON Product with Price",
                    "active": true
                }
                """;

        Product product = template.requestBody("direct:createProduct", productJson, Product.class);
        assertNotNull(product.getId());

        // Create price with JSON
        String priceJson = String.format("""
                {
                    "product": "%s",
                    "currency": "usd",
                    "unit_amount": 2999
                }
                """, product.getId());

        Price price = template.requestBody("direct:createPrice", priceJson, Price.class);

        assertNotNull(price);
        assertNotNull(price.getId());
        assertTrue(price.getId().startsWith("price_"));
        assertEquals("usd", price.getCurrency());
        assertEquals(2999L, price.getUnitAmount().longValue());
        assertEquals(product.getId(), price.getProduct());

        // Cleanup - archive product instead of delete since it has prices
        Map<String, Object> archiveParams = new HashMap<>();
        archiveParams.put("active", false);
        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, product.getId());
        template.requestBodyAndHeaders("direct:updateProduct", archiveParams, headers, Product.class);
    }

    @Test
    public void testCreateProductWithMetadataJson() throws Exception {
        String json = """
                {
                    "name": "JSON Metadata Product",
                    "metadata": {
                        "sku": "JSON-SKU-789",
                        "category": "software",
                        "vendor": "test-vendor"
                    }
                }
                """;

        Product product = template.requestBody("direct:createProduct", json, Product.class);

        assertNotNull(product);
        assertNotNull(product.getMetadata());
        assertEquals("JSON-SKU-789", product.getMetadata().get("sku"));
        assertEquals("software", product.getMetadata().get("category"));
        assertEquals("test-vendor", product.getMetadata().get("vendor"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteProduct", null, StripeConstants.OBJECT_ID, product.getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Product routes
                from("direct:createProduct")
                        .to("stripe:products?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrieveProduct")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:products?apiKey={{STRIPE_API_KEY}}");

                from("direct:updateProduct")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:products?apiKey={{STRIPE_API_KEY}}");

                from("direct:deleteProduct")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_DELETE))
                        .to("stripe:products?apiKey={{STRIPE_API_KEY}}");

                // Price routes
                from("direct:createPrice")
                        .to("stripe:prices?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrievePrice")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:prices?apiKey={{STRIPE_API_KEY}}");
            }
        };
    }
}
