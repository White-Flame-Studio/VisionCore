package com.xnexusacs.visioncore.common.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.Executor;

public final class HttpClientProvider {

    private final HttpClient client;

    public HttpClientProvider(Executor executor, Duration connectTimeout) {
        this.client = HttpClient.newBuilder().executor(executor).connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public HttpClient raw() {
        return client;
    }

    public HttpRequest.Builder newRequest(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
    }
}
