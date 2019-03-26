package org.reso;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.olingo.client.core.http.DefaultHttpClientFactory;
import org.apache.olingo.commons.api.http.HttpMethod;

import java.net.URI;

public class TokenHttpClientFactory extends DefaultHttpClientFactory {
  String token;

  /**
   * Constructor for use with tokens.
   * @param token the token to be used for server requests.
   */
  public TokenHttpClientFactory(String token) {
    this.token = token;
  }

  @Override
  public DefaultHttpClient create(final HttpMethod method, final URI uri) {
    final DefaultHttpClient client = new DefaultHttpClient();
    client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);

    // add auth token using the vendor's bearer token
    client.addRequestInterceptor((request, context) ->
      request.addHeader("Authorization", "Bearer " + token));

    return client;
  }

  @Override
  public void close(final HttpClient httpClient) {
    httpClient.getConnectionManager().shutdown();
  }
}
