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
package org.apache.camel.component.twilio;

import java.net.URI;
import java.net.URISyntaxException;

import com.twilio.rest.api.v2010.Account;
import com.twilio.rest.api.v2010.account.AuthorizedConnectApp;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Conference;
import com.twilio.rest.api.v2010.account.ConnectApp;
import com.twilio.rest.api.v2010.account.IncomingPhoneNumber;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.Recording;
import com.twilio.rest.api.v2010.account.Transcription;
import com.twilio.rest.api.v2010.account.call.Feedback;
import com.twilio.rest.api.v2010.account.call.FeedbackSummary;
import com.twilio.rest.api.v2010.account.conference.Participant;
import com.twilio.rest.api.v2010.account.incomingphonenumber.Local;
import com.twilio.rest.api.v2010.account.incomingphonenumber.Mobile;
import com.twilio.rest.api.v2010.account.incomingphonenumber.TollFree;
import com.twilio.rest.api.v2010.account.recording.AddOnResult;
import com.twilio.rest.api.v2010.account.usage.Record;
import com.twilio.rest.api.v2010.account.usage.Trigger;
import com.twilio.rest.api.v2010.account.usage.record.AllTime;
import com.twilio.rest.api.v2010.account.usage.record.Daily;
import com.twilio.rest.api.v2010.account.usage.record.LastMonth;
import com.twilio.rest.api.v2010.account.usage.record.Monthly;
import com.twilio.rest.api.v2010.account.usage.record.ThisMonth;
import com.twilio.rest.api.v2010.account.usage.record.Today;
import com.twilio.rest.api.v2010.account.usage.record.Yearly;
import com.twilio.rest.api.v2010.account.usage.record.Yesterday;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Sip;
import org.apache.camel.Converter;

@Converter
public final class TwilioConverter {

    private TwilioConverter() {
        //Utility Class
    }

    @Converter
    public static URI toURI(String value) throws URISyntaxException {
        return new URI(value);
    }

    // -----------------------------------------------------
    // com.twilio.type
    // -----------------------------------------------------

    @Converter
    public static PhoneNumber toPhoneNumber(String value) {
        return new PhoneNumber(value);
    }

    @Converter
    public static Sip toSip(String value) {
        return new Sip(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010
    // -----------------------------------------------------

    @Converter
    public static Account.Status toAccountStatus(String value) {
        return Account.Status.forValue(value);
    }

    @Converter
    public static Account.Type toAccountType(String value) {
        return Account.Type.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account
    // -----------------------------------------------------

    @Converter
    public static AuthorizedConnectApp.Permission toAuthorizedConnectAppPermission(String value) {
        return AuthorizedConnectApp.Permission.forValue(value);
    }

    @Converter
    public static Call.Event toCallEvent(String value) {
        return Call.Event.forValue(value);
    }

    @Converter
    public static Call.Status toCallStatus(String value) {
        return Call.Status.forValue(value);
    }

    @Converter
    public static Call.UpdateStatus toCallUpdateStatus(String value) {
        return Call.UpdateStatus.forValue(value);
    }

    @Converter
    public static Conference.Status toConferenceStatus(String value) {
        return Conference.Status.forValue(value);
    }

    @Converter
    public static Conference.UpdateStatus toConferenceUpdateStatus(String value) {
        return Conference.UpdateStatus.forValue(value);
    }

    @Converter
    public static ConnectApp.Permission toConnectAppPermission(String value) {
        return ConnectApp.Permission.forValue(value);
    }

    @Converter
    public static IncomingPhoneNumber.AddressRequirement toIncomingPhoneNumberAddressRequirement(String value) {
        return IncomingPhoneNumber.AddressRequirement.forValue(value);
    }

    @Converter
    public static IncomingPhoneNumber.EmergencyStatus toIncomingPhoneNumberEmergencyStatus(String value) {
        return IncomingPhoneNumber.EmergencyStatus.forValue(value);
    }

    @Converter
    public static Message.Direction toMessageDirection(String value) {
        return Message.Direction.forValue(value);
    }

    @Converter
    public static Message.Status toMessageStatus(String value) {
        return Message.Status.forValue(value);
    }

    @Converter
    public static Recording.Source toRecordingSource(String value) {
        return Recording.Source.forValue(value);
    }

    @Converter
    public static Recording.Status toRecordingStatus(String value) {
        return Recording.Status.forValue(value);
    }

    @Converter
    public static Transcription.Status toTranscriptionStatus(String value) {
        return Transcription.Status.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.call
    // -----------------------------------------------------

    @Converter
    public static Feedback.Issues toFeedbackIssues(String value) {
        return Feedback.Issues.forValue(value);
    }

    @Converter
    public static FeedbackSummary.Status toFeedbackSummaryStatus(String value) {
        return FeedbackSummary.Status.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.conference
    // -----------------------------------------------------

    @Converter
    public static Participant.Status toParticipantStatus(String value) {
        return Participant.Status.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.incomingphonenumber
    // -----------------------------------------------------

    @Converter
    public static Local.AddressRequirement toLocalAddressRequirement(String value) {
        return Local.AddressRequirement.forValue(value);
    }

    @Converter
    public static Mobile.AddressRequirement toMobileAddressRequirement(String value) {
        return Mobile.AddressRequirement.forValue(value);
    }

    @Converter
    public static TollFree.AddressRequirement toTollFreeAddressRequirement(String value) {
        return TollFree.AddressRequirement.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.message
    // -----------------------------------------------------

    @Converter
    public static com.twilio.rest.api.v2010.account.message.Feedback.Outcome toFeedbackOutcome(String value) {
        return com.twilio.rest.api.v2010.account.message.Feedback.Outcome.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.recording
    // -----------------------------------------------------

    @Converter
    public static AddOnResult.Status toAddOnResultStatus(String value) {
        return AddOnResult.Status.forValue(value);
    }

    @Converter
    public static com.twilio.rest.api.v2010.account.recording.Transcription.Status toRecordingTranscriptionStatus(String value) {
        return com.twilio.rest.api.v2010.account.recording.Transcription.Status.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.usage
    // -----------------------------------------------------

    @Converter
    public static Record.Category toRecordCategory(String value) {
        return Record.Category.forValue(value);
    }

    @Converter
    public static Trigger.Recurring toTriggerRecurring(String value) {
        return Trigger.Recurring.forValue(value);
    }

    @Converter
    public static Trigger.TriggerField toTriggerTriggerField(String value) {
        return Trigger.TriggerField.forValue(value);
    }

    @Converter
    public static Trigger.UsageCategory toTriggerUsageCategory(String value) {
        return Trigger.UsageCategory.forValue(value);
    }

    // -----------------------------------------------------
    // com.twilio.rest.api.v2010.account.usage.record
    // -----------------------------------------------------

    @Converter
    public static AllTime.Category toAllTimeCategory(String value) {
        return AllTime.Category.forValue(value);
    }

    @Converter
    public static Daily.Category toDailyCategory(String value) {
        return Daily.Category.forValue(value);
    }

    @Converter
    public static LastMonth.Category toLastMonthCategory(String value) {
        return LastMonth.Category.forValue(value);
    }

    @Converter
    public static Monthly.Category toMonthlyCategory(String value) {
        return Monthly.Category.forValue(value);
    }

    @Converter
    public static ThisMonth.Category toThisMonthCategory(String value) {
        return ThisMonth.Category.forValue(value);
    }

    @Converter
    public static Today.Category toTodayCategory(String value) {
        return Today.Category.forValue(value);
    }

    @Converter
    public static Yearly.Category toYearlyCategory(String value) {
        return Yearly.Category.forValue(value);
    }

    @Converter
    public static Yesterday.Category toYesterdayCategory(String value) {
        return Yesterday.Category.forValue(value);
    }
}
