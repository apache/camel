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
package org.apache.camel.component.openai;

import java.util.List;

/**
 * Utility methods for embedding similarity calculations. Can be used as a Camel bean for easy integration in routes.
 */
public final class SimilarityUtils {

    private SimilarityUtils() {
    }

    /**
     * Calculate cosine similarity between two embedding vectors.
     *
     * @param  a first embedding vector
     * @param  b second embedding vector
     * @return   similarity score between -1.0 and 1.0 (higher = more similar)
     */
    public static double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimensions: " + a.size() + " vs " + b.size());
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            float ai = a.get(i);
            float bi = b.get(i);
            dotProduct += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0) {
            return 0.0;
        }
        return dotProduct / denominator;
    }

    /**
     * Calculate cosine similarity using double arrays (more efficient).
     *
     * @param  a first embedding vector
     * @param  b second embedding vector
     * @return   similarity score between -1.0 and 1.0 (higher = more similar)
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimensions: " + a.length + " vs " + b.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0) {
            return 0.0;
        }
        return dotProduct / denominator;
    }

    /**
     * Calculate Euclidean distance between two embedding vectors.
     *
     * @param  a first embedding vector
     * @param  b second embedding vector
     * @return   distance (lower = more similar)
     */
    public static double euclideanDistance(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimensions: " + a.size() + " vs " + b.size());
        }

        double sum = 0.0;
        int size = a.size();
        for (int i = 0; i < size; i++) {
            double diff = a.get(i).doubleValue() - b.get(i).doubleValue();
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Calculate dot product between two embedding vectors. Useful when vectors are normalized.
     *
     * @param  a first embedding vector
     * @param  b second embedding vector
     * @return   dot product value
     */
    public static double dotProduct(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimensions: " + a.size() + " vs " + b.size());
        }

        double product = 0.0;
        for (int i = 0; i < a.size(); i++) {
            product += a.get(i) * b.get(i);
        }
        return product;
    }

    /**
     * Normalize a vector to unit length.
     *
     * @param  vector the vector to normalize
     * @return        normalized vector
     */
    public static List<Float> normalize(List<Float> vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm == 0) {
            return vector;
        }

        final double finalNorm = norm;
        return vector.stream()
                .map(v -> (float) (v / finalNorm))
                .toList();
    }
}
