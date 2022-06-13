/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Entrypoint for instrumenting OkHttp clients. */
public final class HttpClientTelemetry {

  /** Returns a new {@link HttpClientTelemetry} configured with the given {@link OpenTelemetry}. */
  public static HttpClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link HttpClientTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static HttpClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new HttpClientTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpRequest, HttpResponse<?>> instrumenter;
  private final ContextPropagators propagators;

  HttpClientTelemetry(Instrumenter<HttpRequest, HttpResponse<?>> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  public OpenTelemetryHttpClient createClient(HttpClient delegate) {
    return new OpenTelemetryHttpClient(delegate, instrumenter, propagators);
  }

}
