/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class HttpHeadersSetter {

  private final ContextPropagators contextPropagators;

  public HttpHeadersSetter(ContextPropagators contextPropagators) {
    this.contextPropagators = contextPropagators;
  }

  public HttpHeaders inject(HttpHeaders original) {
    Map<String, List<String>> headerMap = new HashMap<>(original.map());

    contextPropagators
        .getTextMapPropagator()
        .inject(
            Context.current(),
            headerMap,
            (carrier, key, value) -> carrier.put(key, Collections.singletonList(value)));

    return HttpHeaders.of(headerMap, (k, v) -> true);
  }
}
