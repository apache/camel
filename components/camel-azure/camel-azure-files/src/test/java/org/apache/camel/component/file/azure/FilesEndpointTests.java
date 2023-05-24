package org.apache.camel.component.file.azure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class FilesEndpointTests extends CamelTestSupport {

  @Test
  void hostForValidURIShouldBeExtracted() {
    var endpoint = context.getEndpoint("azure-files://host/share", FilesEndpoint.class);
    assertEquals("host", endpoint.filesHost());
  }

  @Test
  void hostForInvalidURIShouldBeNull() {
    // intentionally missing ://
    var endpoint = context.getEndpoint("azure-files:host/share", FilesEndpoint.class);
    assertNull(endpoint.filesHost()); // TODO Camel added missing // , acceptable?
  }

  @Test
  void sasTokenForCopyPastedURIShouldBePreserved() {
    // it calls SAS setters on endpoint
    var endpoint = context.getEndpoint(
        "azure-files:host/share?sv=2021-12-02&ss=f&srt=sco&sp=rwdlc&se=2023-05-05T16:14:44Z&st=2023-04-28T08:14:44Z&spr=https&sig=95ZxXN3ST033Z4ym7quRqUUs2hjAtx63MAubaMKTyTg%3D", FilesEndpoint.class);
    assertEquals(
        "sv=2021-12-02&ss=f&srt=sco&sp=rwdlc&se=2023-05-05T16:14:44Z&st=2023-04-28T08:14:44Z&spr=https&sig=95ZxXN3ST033Z4ym7quRqUUs2hjAtx63MAubaMKTyTg%3D",
        endpoint.token()); // TODO Camel decoded trailing %3D to = , acceptable?
  }

}
