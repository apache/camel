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
package org.apache.camel.component.langchain4j.agent.pojos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

/**
 * Test POJO for structured output testing with responseType parameter. Represents a car rental recommendation with
 * vehicle details and reasoning.
 */
public class CarRentalRecommendation {

    @JsonProperty(required = true)
    @Description("The category of vehicle (e.g., Economy, SUV, Luxury, Minivan, Sedan)")
    private String vehicleType;

    @JsonProperty(required = true)
    @Description("The make and model of the vehicle (e.g., Toyota Camry, Ford Explorer)")
    private String vehicleModel;

    @JsonProperty(required = true)
    @Description("The estimated daily rental rate in USD (e.g., 45.0, 89.99, 150.0)")
    private double dailyRate;

    @JsonProperty(required = true)
    @Description("List of key features or benefits of this vehicle")
    private List<String> features;

    @JsonProperty(required = true)
    @Description("The reasoning behind recommending this vehicle")
    private String reasoning;

    @Description("Whether insurance is recommended for this rental")
    private boolean insuranceRecommended;

    public CarRentalRecommendation() {
    }

    public CarRentalRecommendation(
                                   String vehicleType, String vehicleModel, double dailyRate,
                                   List<String> features, String reasoning, boolean insuranceRecommended) {
        this.vehicleType = vehicleType;
        this.vehicleModel = vehicleModel;
        this.dailyRate = dailyRate;
        this.features = features;
        this.reasoning = reasoning;
        this.insuranceRecommended = insuranceRecommended;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public double getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(double dailyRate) {
        this.dailyRate = dailyRate;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public boolean isInsuranceRecommended() {
        return insuranceRecommended;
    }

    public void setInsuranceRecommended(boolean insuranceRecommended) {
        this.insuranceRecommended = insuranceRecommended;
    }

    @Override
    public String toString() {
        return "CarRentalRecommendation{" +
               "vehicleType='" + vehicleType + '\'' +
               ", vehicleModel='" + vehicleModel + '\'' +
               ", dailyRate=" + dailyRate +
               ", features=" + features +
               ", reasoning='" + reasoning + '\'' +
               ", insuranceRecommended=" + insuranceRecommended +
               '}';
    }
}
