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

package org.apache.camel.component.qdrant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsSelector;
import org.apache.camel.Converter;

/**
 * Converter methods to convert from / to Qdrant types.
 */
@Converter(generateLoader = true)
public class QdrantConverter {

    @Converter
    public static List<PointStruct> toListOfPointStructs(PointStruct p) {
        return Collections.singletonList(p);
    }

    @Converter
    public static List<PointStruct> toListOfPointStructsFromCollection(Collection<PointStruct> collection) {
        return new ArrayList<>(collection);
    }

    @Converter
    public static List<PointId> toListOfPointIds(PointId p) {
        return Collections.singletonList(p);
    }

    @Converter
    public static List<PointId> toListOfPointIdsFromCollection(Collection<PointId> collection) {
        return new ArrayList<>(collection);
    }

    @Converter
    public static PointsSelector toPointSelector(PointId id) {
        return Points.PointsSelector.newBuilder()
                .setPoints(
                        Points.PointsIdsList.newBuilder()
                                .addIds(id)
                                .build())
                .build();
    }

    @Converter
    public static PointsSelector toPointSelector(Points.Condition condition) {
        return Points.PointsSelector.newBuilder()
                .setFilter(
                        Points.Filter.newBuilder()
                                .addMust(condition)
                                .build())
                .build();
    }

    @Converter
    public static PointsSelector toPointSelector(Filter filter) {
        return Points.PointsSelector.newBuilder()
                .setFilter(filter)
                .build();
    }
}
