package net.heartbyte.lerpc11client;

import net.heartbyte.lerpc.net.Client;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Http implements Client {
    public final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(net.heartbyte.lerpc.net.Http.service)
            .build();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T Execute(
            String                    method,
            String                    url,
            byte[]                    body,
            Map<String, List<String>> headers,
            Type                      type,
            boolean                   async
    ) {
        CompletableFuture<T> future = new CompletableFuture<>();

        Runnable runnable = () -> {
            try {
                var requestBuilder = HttpRequest.newBuilder();

                requestBuilder.method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));

                requestBuilder.uri(new URI(url));

                headers.forEach((key, val) -> {
                    requestBuilder.setHeader(key, val.get(0));
                });

                var requestFuture = this.client.sendAsync(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                requestFuture.whenComplete((response, exception) -> {
                    if (exception != null) {
                        future.completeExceptionally(exception);
                        return;
                    }

                    var responseBody = response.body();

                    future.complete((T) net.heartbyte.lerpc.net.Http.gson.fromJson(responseBody, type));
                });

            } catch (URISyntaxException exception) {
                future.completeExceptionally(exception);
            }
        };

        if (async) {
            net.heartbyte.lerpc.net.Http.service.submit(runnable);
        } else {
            runnable.run();
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException exception) {
                exception.printStackTrace(System.err);
                return null;
            }
        }

        return (T) future;
    }
}