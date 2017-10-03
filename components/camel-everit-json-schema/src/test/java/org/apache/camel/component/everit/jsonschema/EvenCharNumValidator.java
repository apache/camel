package org.apache.camel.component.everit.jsonschema;

import java.util.Optional;

import org.everit.json.schema.FormatValidator;

public class EvenCharNumValidator implements FormatValidator {

    @Override
    public Optional<String> validate(final String subject) {
      if (subject.length() % 2 == 0) {
        return Optional.empty();
      } else {
        return Optional.of(String.format("the length of string [%s] is odd", subject));
      }
    }

    @Override
    public String formatName() {
        return "evenlength";
    }
  }