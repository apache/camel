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
package org.apache.camel.component.yammer.model;

import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    @JsonProperty("previous_companies")
    private List<String> previousCompanies;
    @JsonProperty("kids_names")
    private String kidsNames;
    @JsonProperty("activated_at")
    private String activatedAt;
    private String interests;
    private String admin;
    @JsonProperty("full_name")
    private String fullName;
    private String name;
    @JsonProperty("last_name")
    private String lastName;
    @JsonProperty("mugshot_url_template")
    private String mugshotUrlTemplate;
    private String type;
    @JsonProperty("mugshot_url")
    private String mugshotUrl;
    @JsonProperty("birth_date")
    private String birthDate;
    private String timezone;
    private String location;
    private String state;
    @JsonProperty("web_url")
    private String webUrl;
    private Stats stats;
    @JsonProperty("show_ask_for_photo")
    private Boolean showAskForPhoto;
    @JsonProperty("external_urls")
    private List<String> externalUrls;
    private List<String> schools;
    private String summary;
    @JsonProperty("job_title")
    private String jobTitle;
    private Long id;
    private String expertise;
    @JsonProperty("network_domains")
    private List<String> networkDomains;
    @JsonProperty("network_name")
    private String networkName;
    @JsonProperty("hire_date")
    private String hireDate;
    private String url;
    private String guid;
    @JsonProperty("significant_other")
    private String significantOther;
    @JsonProperty("verified_admin")
    private String verifiedAdmin;
    private Settings settings;
    @JsonProperty("can_broadcast")
    private String canBroadcast;
    @JsonProperty("first_name")
    private String firstName;
    private String department;
    @JsonProperty("network_id")
    private Long networkId;
    private Contact contact;

    public List<String> getPreviousCompanies() {
        return previousCompanies;
    }

    public void setPreviousCompanies(List<String> previousCompanies) {
        this.previousCompanies = previousCompanies;
    }

    public String getKidsNames() {
        return kidsNames;
    }

    public void setKidsNames(String kidsNames) {
        this.kidsNames = kidsNames;
    }

    public String getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(String activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getInterests() {
        return interests;
    }

    public void setInterests(String interests) {
        this.interests = interests;
    }

    public String getAdmin() {
        return admin;
    }

    public void setAdmin(String admin) {
        this.admin = admin;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMugshotUrlTemplate() {
        return mugshotUrlTemplate;
    }

    public void setMugshotUrlTemplate(String mugshotUrlTemplate) {
        this.mugshotUrlTemplate = mugshotUrlTemplate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMugshotUrl() {
        return mugshotUrl;
    }

    public void setMugshotUrl(String mugshotUrl) {
        this.mugshotUrl = mugshotUrl;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }

    public Boolean getShowAskForPhoto() {
        return showAskForPhoto;
    }

    public void setShowAskForPhoto(Boolean showAskForPhoto) {
        this.showAskForPhoto = showAskForPhoto;
    }

    public List<String> getExternalUrls() {
        return externalUrls;
    }

    public void setExternalUrls(List<String> externalUrls) {
        this.externalUrls = externalUrls;
    }

    public List<String> getSchools() {
        return schools;
    }

    public void setSchools(List<String> schools) {
        this.schools = schools;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public List<String> getNetworkDomains() {
        return networkDomains;
    }

    public void setNetworkDomains(List<String> networkDomains) {
        this.networkDomains = networkDomains;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public String getHireDate() {
        return hireDate;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = hireDate;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getSignificantOther() {
        return significantOther;
    }

    public void setSignificantOther(String significantOther) {
        this.significantOther = significantOther;
    }

    public String getVerifiedAdmin() {
        return verifiedAdmin;
    }

    public void setVerifiedAdmin(String verifiedAdmin) {
        this.verifiedAdmin = verifiedAdmin;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public String getCanBroadcast() {
        return canBroadcast;
    }

    public void setCanBroadcast(String canBroadcast) {
        this.canBroadcast = canBroadcast;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return "User [previousCompanies=" + previousCompanies + ", kidsNames=" + kidsNames + ", activatedAt=" + activatedAt + ", interests=" + interests + ", admin=" + admin + ", fullName="
                + fullName + ", name=" + name + ", lastName=" + lastName + ", mugshotUrlTemplate=" + mugshotUrlTemplate + ", type=" + type + ", mugshotUrl=" + mugshotUrl + ", birthDate=" + birthDate
                + ", timezone=" + timezone + ", location=" + location + ", state=" + state + ", webUrl=" + webUrl + ", stats=" + stats + ", showAskForPhoto=" + showAskForPhoto + ", externalUrls="
                + externalUrls + ", schools=" + schools + ", summary=" + summary + ", jobTitle=" + jobTitle + ", id=" + id + ", expertise=" + expertise + ", networkDomains=" + networkDomains
                + ", networkName=" + networkName + ", hireDate=" + hireDate + ", url=" + url + ", guid=" + guid + ", significantOther=" + significantOther + ", verifiedAdmin=" + verifiedAdmin
                + ", settings=" + settings + ", canBroadcast=" + canBroadcast + ", firstName=" + firstName + ", department=" + department + ", networkId=" + networkId + ", contact=" + contact + "]";
    }

}
