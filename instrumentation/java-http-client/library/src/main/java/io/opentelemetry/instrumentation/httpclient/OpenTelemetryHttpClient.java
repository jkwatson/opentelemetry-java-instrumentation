package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class OpenTelemetryHttpClient extends HttpClient {

  private final HttpClient delegate;
  private final Instrumenter<HttpRequest, HttpResponse<?>> instrumenter;
  private final HttpHeadersSetter httpHeadersSetter;

  public OpenTelemetryHttpClient(HttpClient delegate,
      Instrumenter<HttpRequest, HttpResponse<?>> instrumenter, ContextPropagators propagators) {
    this.delegate = delegate;
    this.instrumenter = instrumenter;
    this.httpHeadersSetter = new HttpHeadersSetter(propagators);
  }

  @Override
  public Optional<CookieHandler> cookieHandler() {
    return delegate.cookieHandler();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return delegate.connectTimeout();
  }

  @Override
  public Redirect followRedirects() {
    return delegate.followRedirects();
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return delegate.proxy();
  }

  @Override
  public SSLContext sslContext() {
    return delegate.sslContext();
  }

  @Override
  public SSLParameters sslParameters() {
    return delegate.sslParameters();
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return delegate.authenticator();
  }

  @Override
  public Version version() {
    return delegate.version();
  }

  @Override
  public Optional<Executor> executor() {
    return delegate.executor();
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request,
      HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
    Context parentContext = Context.current();
    boolean shouldInstrument = instrumenter.shouldStart(parentContext, request);
    Context childContext = shouldInstrument ? instrumenter.start(parentContext, request) : null;
    request = propagateContext(shouldInstrument, request);
    HttpResponse<T> response;
    try {
      response = delegate.send(request, responseBodyHandler);
    } catch (Throwable e) {
      if (shouldInstrument) {
        instrumenter.end(childContext, request, null, e);
      }
      throw e;
    }
    if (shouldInstrument) {
      instrumenter.end(childContext, request, response, null);
    }
    return response;
  }

  private HttpRequest propagateContext(boolean shouldInstrument, HttpRequest request) {
    if (!shouldInstrument) {
      return request;
    }
    HttpHeaders updatedHeaders = httpHeadersSetter.inject(request.headers());
    HttpRequest.Builder builder = newBuilder(request);
    updatedHeaders.map()
        .forEach((key, values) -> values.forEach(value -> builder.header(key, value)));
    return builder.build();
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
      HttpResponse.BodyHandler<T> responseBodyHandler) {
    Context parentContext = Context.current();
    boolean shouldInstrument = instrumenter.shouldStart(parentContext, request);
    Context childContext = shouldInstrument ? instrumenter.start(parentContext, request) : null;
    HttpRequest updatedRequest = propagateContext(shouldInstrument, request);

    CompletableFuture<HttpResponse<T>> response = delegate.sendAsync(request, responseBodyHandler);
    response.whenComplete((httpResponse, throwable) -> {
      if (shouldInstrument) {
        instrumenter.end(childContext, updatedRequest, httpResponse, throwable);
      }
    });
    return response;
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
      HttpResponse.BodyHandler<T> responseBodyHandler,
      HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
    Context parentContext = Context.current();
    boolean shouldInstrument = instrumenter.shouldStart(parentContext, request);
    Context childContext = shouldInstrument ? instrumenter.start(parentContext, request) : null;
    HttpRequest updatedRequest = propagateContext(shouldInstrument, request);

    CompletableFuture<HttpResponse<T>> response = delegate.sendAsync(request, responseBodyHandler,
        pushPromiseHandler);
    response.whenComplete((httpResponse, throwable) -> {
      if (shouldInstrument) {
        instrumenter.end(childContext, updatedRequest, httpResponse, throwable);
      }
    });
    return response;
  }

  @Override
  public WebSocket.Builder newWebSocketBuilder() {
    return delegate.newWebSocketBuilder();
  }

  // copied and simplified from the java 16+ implementation
  private static HttpRequest.Builder newBuilder(HttpRequest request) {
    HttpRequest.Builder builder = HttpRequest.newBuilder();
    builder.uri(request.uri());
    builder.expectContinue(request.expectContinue());

    request.headers().map().forEach((name, values) ->
        values.forEach(value -> builder.header(name, value)));

    request.version().ifPresent(builder::version);
    request.timeout().ifPresent(builder::timeout);
    var method = request.method();
    request.bodyPublisher().ifPresentOrElse(
        // if body is present, set it
        bodyPublisher -> builder.method(method, bodyPublisher),
        // otherwise, the body is absent, special case for GET/DELETE,
        // or else use empty body
        () -> {
          switch (method) {
            case "GET":
              builder.GET();
              break;
            case "DELETE":
              builder.DELETE();
              break;
            default:
              builder.method(method, HttpRequest.BodyPublishers.noBody());
          }
        }
    );
    return builder;
  }
}
