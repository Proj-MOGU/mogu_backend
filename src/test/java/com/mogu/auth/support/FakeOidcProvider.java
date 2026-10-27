package com.mogu.auth.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class FakeOidcProvider implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<JWK> keys = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile RSAKey signingKey;
    private volatile int status = 200;
    private volatile CountDownLatch entered = new CountDownLatch(1);
    private volatile CountDownLatch release = new CountDownLatch(1);
    private volatile boolean delayed;

    public FakeOidcProvider() {
        try {
            rotate("initial");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/jwks", exchange -> {
                requests.incrementAndGet();
                try (exchange) {
                    if (delayed) {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    byte[] body = new JWKSet(List.copyOf(keys)).toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
        } catch (Exception exception) {
            throw new IllegalStateException("Fake provider setup failed", exception);
        }
    }

    public URI jwkSetUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
    }

    public int requests() {
        return requests.get();
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public void rotate(String kid) throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID(kid).generate();
        keys.add(signingKey.toPublicJWK());
    }

    public void fail() {
        status = 500;
    }

    public void delay() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        delayed = true;
    }

    public boolean awaitDelayedRequest() throws InterruptedException {
        return entered.await(10, TimeUnit.SECONDS);
    }

    public void resume() {
        delayed = false;
        release.countDown();
    }

    public String sign(JWTClaimsSet claims) throws Exception {
        return sign(claims, signingKey);
    }

    public static String sign(JWTClaimsSet claims, RSAKey key) throws Exception {
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }
}
