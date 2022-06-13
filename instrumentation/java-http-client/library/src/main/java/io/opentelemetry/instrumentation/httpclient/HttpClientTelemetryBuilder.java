/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/** A builder of {@link HttpClientTelemetry}. */
public final class HttpClientTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<HttpRequest, HttpResponse<?>>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(new JdkHttpAttributesGetter());

  HttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public HttpClientTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpRequest, HttpResponse<?>> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public HttpClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public HttpClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link HttpClientTelemetry} with the settings of this {@link HttpClientTelemetryBuilder}.
   */
  public HttpClientTelemetry build() {
    HttpClientAttributesGetter<HttpRequest, HttpResponse<?>> httpAttributesGetter = new JdkHttpAttributesGetter();
    NetClientAttributesGetter<HttpRequest, HttpResponse<?>> attributesGetter = new JdkHttpNetAttributesGetter();

    Instrumenter<HttpRequest, HttpResponse<?>> instrumenter =
        Instrumenter.<HttpRequest, HttpResponse<?>>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(attributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get())
            .newInstrumenter(alwaysClient());
    return new HttpClientTelemetry(instrumenter, openTelemetry.getPropagators());
  }
}
