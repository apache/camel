package com.graphqljava.apache.camel;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class GraphQLDataFetchers {
    //TODO : directly access through APIs (remove mock data)
    private static List<Map<String, String>> users = Arrays.asList(
            ImmutableMap.of("id", "user-1",
                    "age", "10",
                    "detailsId", "detail-1"),
            ImmutableMap.of("id", "user-2",
                    "age", "11",
                    "detailsId", "detail-2")
    );

    private static List<Map<String, String>> details = Arrays.asList(
            ImmutableMap.of("id", "detail-1",
                    "firstName", "Zoran",
                    "lastName", "Regvert"),
            ImmutableMap.of("id", "detail-2",
                    "firstName", "Andrea",
                    "lastName", "Cosentino")
    );

    // Add Data Fetcher Methods
    public DataFetcher getUserByIdDataFetcher() {
        return dataFetchingEnvironment -> {
            String userId = dataFetchingEnvironment.getArgument("id");
            return users
                    .stream()
                    .filter(user -> user.get("id").equals(userId))
                    .findFirst()
                    .orElse(null);
        };
    }

    public DataFetcher getDetailsDataFetcher() {
        return dataFetchingEnvironment -> {
            Map<String, String> user = dataFetchingEnvironment.getSource();
            String detailId = user.get("detailsId");
            return details
                    .stream()
                    .filter(detail -> detail.get("id").equals(detailId))
                    .findFirst()
                    .orElse(null);
        };
    }
}