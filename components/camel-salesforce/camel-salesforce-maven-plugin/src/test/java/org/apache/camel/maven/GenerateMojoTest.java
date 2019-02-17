package org.apache.camel.maven;

import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class GenerateMojoTest {

    private static final String ACCOUNT = "Account";
    private static final String CONTACT = "Contact";
    private static final String MULTIPICKLIST = "multipicklist";
    private static final String PICKLIST = "picklist";

    private static final String PROPER_PICKLIST_TO_STRING_TYPE = String.class.getName();
    private static final String PROPER_MULTIPICKLIST_TO_STRING_TYPE = String.class.getName() + "[]";
    private static final String PROPER_DEFAULT_PICKLIST_TYPE_ENDING = "Enum";
    private static final String PROPER_DEFAULT_MULTIPICKLIST_TYPE_ENDING = "Enum[]";

    @Test
    public void shouldParsePicklistsToObjects() {
        // given
        final int properCountExceptions = 0;
        final GenerateMojo mojo = new GenerateMojo();
        mojo.picklistToStrings = createValidPicklistToStrings();
        mojo.picklistToEnums = createValidPicklistToEnums();

        // when
        int resultCountExceptions = 0;
        try {
            mojo.parsePicklistToEnums();
        } catch (IllegalArgumentException e) {
            resultCountExceptions++;
        }

        try {
            mojo.parsePicklistToStrings();
        } catch (IllegalArgumentException e) {
            resultCountExceptions++;
        }

        // then
        assertEquals(properCountExceptions, resultCountExceptions);
    }

    @Test
    public void shouldParsePicklistsToObjectsFail() {
        // given
        final int properCountExceptions = 2;
        final String[] invalidPicklistToStrings = new String[]{
                "Account,DataSource",
        };
        final String[] invalidPicklistToEnums = new String[]{
                "Con-tact.Contact_Source_Information__c",
        };
        final GenerateMojo mojo = new GenerateMojo();
        mojo.picklistToStrings = invalidPicklistToStrings;
        mojo.picklistToEnums = invalidPicklistToEnums;

        // when
        int resultCountExceptions = 0;
        try {
            mojo.parsePicklistToEnums();
        } catch (IllegalArgumentException e) {
            resultCountExceptions++;
        }

        try {
            mojo.parsePicklistToStrings();
        } catch (IllegalArgumentException e) {
            resultCountExceptions++;
        }

        // then
        assertEquals(properCountExceptions, resultCountExceptions);
    }

    @Test
    public void shouldReturnStringOrEnumTypesDefaultEnumMode() {
        // given
        final String sObjectName = ACCOUNT;
        final GenerateMojo mojo = new GenerateMojo();
        mojo.picklistToStrings = new String[]{
                sObjectName + ".StandardPicklist",
                sObjectName + ".Stringified_Custom_Picklist_Type__c",
        };
        mojo.parsePicklistToStrings();
        assertTrue(mojo.picklistToStrings != null && mojo.picklistToStrings.length > 1);

        final SObjectDescription accountDescription = new SObjectDescription();
        accountDescription.setName(ACCOUNT);
        accountDescription.setFields(new ArrayList<>());

        final SObjectField defaultPicklist = createField("Default_Picklist__c", PICKLIST);
        final SObjectField defaultMultipicklist = createField("Default_Multipicklist__c", MULTIPICKLIST);
        final SObjectField picklistToString = createField(mojo.picklistToStrings[0].substring(mojo.picklistToStrings[0].indexOf('.') + 1), PICKLIST);
        final SObjectField multipicklistToString = createField(mojo.picklistToStrings[1].substring(mojo.picklistToStrings[1].indexOf('.') + 1), MULTIPICKLIST);
        accountDescription.getFields().add(defaultPicklist);
        accountDescription.getFields().add(defaultMultipicklist);
        accountDescription.getFields().add(picklistToString);
        accountDescription.getFields().add(multipicklistToString);

        mojo.useStringsForPicklists = false;
        final GenerateMojo.GeneratorUtility utility = mojo.new GeneratorUtility();

        // when
        final String resultDefaultPicklistType = utility.getFieldType(accountDescription, defaultPicklist);
        final String resultDefaultMultipicklistType = utility.getFieldType(accountDescription, defaultMultipicklist);
        final String resultPicklistToStringType = utility.getFieldType(accountDescription, picklistToString);
        final String resultMultipicklistToStringType = utility.getFieldType(accountDescription, multipicklistToString);

        // then
        assertThat(resultDefaultPicklistType, endsWith(PROPER_DEFAULT_PICKLIST_TYPE_ENDING));
        assertThat(resultDefaultMultipicklistType, endsWith(PROPER_DEFAULT_MULTIPICKLIST_TYPE_ENDING));
        assertThat(resultPicklistToStringType, equalTo(PROPER_PICKLIST_TO_STRING_TYPE));
        assertThat(resultMultipicklistToStringType, equalTo(PROPER_MULTIPICKLIST_TO_STRING_TYPE));
    }

    @Test
    public void shouldReturnStringOrEnumTypesStringMode() {
        // given
        final String sObjectName = CONTACT;
        final GenerateMojo mojo = new GenerateMojo();
        mojo.picklistToEnums = new String[]{
                sObjectName + ".Enum_Contact_Source_Information__c",
                sObjectName + ".Enum_Contract_Type__c",
        };
        mojo.parsePicklistToEnums();

        final SObjectDescription contactDescription = new SObjectDescription();
        contactDescription.setName(sObjectName);
        contactDescription.setFields(new ArrayList<>());

        final SObjectField stringPicklist = createField("Nonspecific_Picklist__c", PICKLIST);
        final SObjectField stringMultipicklist = createField("Nonspecific_Multipicklist__c", MULTIPICKLIST);
        final SObjectField picklistToEnum = createField(mojo.picklistToEnums[0].substring(mojo.picklistToEnums[0].indexOf('.') + 1), PICKLIST);
        final SObjectField multipicklistToEnum = createField(mojo.picklistToEnums[1].substring(mojo.picklistToEnums[1].indexOf('.') + 1), MULTIPICKLIST);
        contactDescription.getFields().add(stringPicklist);
        contactDescription.getFields().add(stringMultipicklist);
        contactDescription.getFields().add(picklistToEnum);
        contactDescription.getFields().add(multipicklistToEnum);

        mojo.useStringsForPicklists = true;
        final GenerateMojo.GeneratorUtility utility = mojo.new GeneratorUtility();

        // when
        final String resultStringPicklistType = utility.getFieldType(contactDescription, stringPicklist);
        final String resultStringMultipicklistType = utility.getFieldType(contactDescription, stringMultipicklist);
        final String resultPicklistToEnumType = utility.getFieldType(contactDescription, picklistToEnum);
        final String resultMultipicklistToEnumType = utility.getFieldType(contactDescription, multipicklistToEnum);

        // then
        assertThat(resultPicklistToEnumType, endsWith(PROPER_DEFAULT_PICKLIST_TYPE_ENDING));
        assertThat(resultMultipicklistToEnumType, endsWith(PROPER_DEFAULT_MULTIPICKLIST_TYPE_ENDING));
        assertThat(resultStringPicklistType, equalTo(PROPER_PICKLIST_TO_STRING_TYPE));
        assertThat(resultStringMultipicklistType, equalTo(PROPER_MULTIPICKLIST_TO_STRING_TYPE));
    }

    private static String[] createValidPicklistToStrings() {
        return new String[]{
                ACCOUNT + ".DataSource",
                ACCOUNT + ".Custom_Picklist_Type__c",
                ACCOUNT + ".Other_Custom_Picklist_Type__c"
        };
    }

    private static String[] createValidPicklistToEnums() {
        return new String[]{
                CONTACT + ".Contact_Source_Information__c",
                CONTACT + ".Contract_Type__c",
                CONTACT + ".Custom_Picklist_Type__c"
        };
    }

    private static SObjectField createField(String name, String type) {
        final SObjectField field = new SObjectField();
        field.setName(name);
        field.setType(type);
        return field;
    }
}
