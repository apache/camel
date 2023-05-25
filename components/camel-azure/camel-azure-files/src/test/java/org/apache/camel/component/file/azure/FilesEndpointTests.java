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
    var plainToken = "sv=2022-11-02&ss=f&srt=sco&sp=rwdlc&se=2023-05-28T22:50:04Z&st=2023-05-24T14:50:04Z&spr=https&sig=gj%2BUKSiCWSHmcubvGhyJhatkP8hkbXkrmV%2B%2BZme%2BCxI%3D";
    // context while resolving calls SAS setters on endpoint
    // by observation Camel decoded sig=gj UKSiCWSHmcubvGhyJhatkP8hkbXkrmV  Zme CxI=
    // using URISupport sig=gj+UKSiCWSHmcubvGhyJhatkP8hkbXkrmV++Zme+CxI=
    //  leads to "Signature size is invalid" response from server
    // likely need to post-process replacing + by %2B 
    // Camel also sorted params befor calling setters
    var endpoint = context.getEndpoint(
        "azure-files://host/share?" + plainToken, FilesEndpoint.class);
    assertEquals(
        plainToken,
        endpoint.token());
  }

  
  @Test
  void shareForValidURIShouldBeExtracted() {
    var endpoint = context.getEndpoint("azure-files://host/share", FilesEndpoint.class);
    assertEquals("share", endpoint.getShare());
  }
  
  @Test
  void shareForValidURIShouldBeExtracted2() {
    var endpoint = context.getEndpoint("azure-files://host/share/", FilesEndpoint.class);
    assertEquals("share", endpoint.getShare());
  }

  @Test
  void shareForValidURIShouldBeExtracted3() {
    var endpoint = context.getEndpoint("azure-files://host/share/path", FilesEndpoint.class);
    assertEquals("share", endpoint.getShare());
  }
}
