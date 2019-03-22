package org.reso;

import org.apache.olingo.client.api.domain.ClientEntitySet;

public class Main {
  public static void main(String[] params) {
    String serviceRoot = "http://rts-api.mlsgrid.com/";
    String bearerToken = "64ed09cc5876671fec76776232213f96fc40d4eb";

    Commander app = new Commander(serviceRoot, bearerToken);
    System.out.println("Metadata is: " + app.getMetadata().toString());

    ClientEntitySet entities = app.readEntities("PropertyResi");

    System.out.println(entities.getEntities().stream().map(e -> e.toString()));

  }
}
