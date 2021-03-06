/*
 * Copyright 2011-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.container.javaagent;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import checkers.nullness.quals.Nullable;
import com.google.common.net.MediaType;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Cookie;
import com.ning.http.client.Response;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import org.fest.reflect.core.Reflection;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import org.glowroot.markers.ThreadSafe;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
class JavaagentHttpClient {

    private final AsyncHttpClient asyncHttpClient;
    private volatile int port;
    @Nullable
    private volatile Cookie sessionIdCookie;

    JavaagentHttpClient(int uiPort) {
        this.port = uiPort;
        this.asyncHttpClient = createAsyncHttpClient();
    }

    void updateUiPort(int uiPort) {
        this.port = uiPort;
    }

    String get(String path) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + port + path);
        Response response = execute(request);
        return validateAndReturnBody(response);
    }

    InputStream getAsStream(String path) throws Exception {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + port + path);
        Response response = execute(request);
        return validateAndReturnBodyAsStream(response);
    }

    String post(String path, String data) throws Exception {
        BoundRequestBuilder request =
                asyncHttpClient.preparePost("http://localhost:" + port + path);
        request.setBody(data);
        Response response = execute(request);
        return validateAndReturnBody(response);
    }

    void close() {
        asyncHttpClient.close();
    }

    private Response execute(BoundRequestBuilder request) throws InterruptedException,
            ExecutionException, IOException {
        populateSessionIdCookie(request);
        Response response = request.execute().get();
        extractSessionIdCookie(response);
        return response;
    }

    private void populateSessionIdCookie(BoundRequestBuilder request) {
        if (sessionIdCookie != null) {
            request.addCookie(sessionIdCookie);
        }
    }

    private void extractSessionIdCookie(Response response) {
        for (Cookie cookie : response.getCookies()) {
            if (cookie.getName().equals("GLOWROOT_SESSION_ID")) {
                if (cookie.getValue().equals("")) {
                    sessionIdCookie = null;
                } else {
                    sessionIdCookie = cookie;
                }
                return;
            }
        }
    }

    private static String validateAndReturnBody(Response response) throws Exception {
        if (wasUncompressed(response)) {
            throw new IllegalStateException("HTTP response was not compressed");
        }
        if (response.getStatusCode() == HttpResponseStatus.OK.getCode()) {
            return response.getResponseBody();
        } else {
            throw new IllegalStateException("Unexpected HTTP status code returned: "
                    + response.getStatusCode() + " (" + response.getStatusText() + ")");
        }
    }

    private static InputStream validateAndReturnBodyAsStream(Response response) throws Exception {
        if (wasUncompressed(response)) {
            throw new IllegalStateException("HTTP response was not compressed");
        }
        if (response.getStatusCode() == HttpResponseStatus.OK.getCode()) {
            return response.getResponseBodyAsStream();
        } else {
            throw new IllegalStateException("Unexpected HTTP status code returned: "
                    + response.getStatusCode() + " (" + response.getStatusText() + ")");
        }
    }

    // this method relies on SaveTheEncodingHandler being inserted into the Netty pipeline before
    // the decompression handler (which removes the Content-Encoding header after decompression) so
    // that the original Content-Encoding can be still be retrieved via the alternate http header
    // X-Original-Content-Encoding
    private static boolean wasUncompressed(Response response) {
        String contentType = response.getHeader(CONTENT_TYPE);
        if (MediaType.ZIP.toString().equals(contentType)) {
            // zip file downloads are never compressed (e.g. trace export)
            return false;
        }
        String contentLength = response.getHeader(CONTENT_LENGTH);
        if ("0".equals(contentLength)) {
            // zero-length responses are never compressed
            return false;
        }
        String contentEncoding = response.getHeader("Glowroot-Original-Encoding");
        return !"gzip".equals(contentEncoding);
    }

    private static AsyncHttpClient createAsyncHttpClient() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder()
                .setCompressionEnabled(true)
                .setMaxRequestRetry(0)
                .setExecutorService(executorService);
        NettyAsyncHttpProviderConfig providerConfig = new NettyAsyncHttpProviderConfig();
        providerConfig.addProperty(NettyAsyncHttpProviderConfig.BOSS_EXECUTOR_SERVICE,
                executorService);
        builder.setAsyncHttpClientProviderConfig(providerConfig);
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient(builder.build());
        addSaveTheEncodingHandlerToNettyPipeline(asyncHttpClient);
        return asyncHttpClient;
    }

    // Netty's HttpContentDecoder removes the Content-Encoding header during the decompression step
    // which makes it difficult to verify that the response from Glowroot was compressed
    //
    // this method adds a ChannelHandler to the netty pipeline, before the decompression handler,
    // and saves the original Content-Encoding header into another http header so it can be used
    // later to verify that the response was compressed
    private static void addSaveTheEncodingHandlerToNettyPipeline(AsyncHttpClient asyncHttpClient) {
        // the next major release of AsyncHttpClient (2.0) will include a hook to modify the
        // pipeline without having to resort to this reflection hack, see
        // https://github.com/AsyncHttpClient/async-http-client/pull/205
        ClientBootstrap plainBootstrap = Reflection.field("plainBootstrap")
                .ofType(ClientBootstrap.class).in(asyncHttpClient.getProvider()).get();
        final ChannelPipelineFactory pipelineFactory = plainBootstrap.getPipelineFactory();
        plainBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipelineFactory.getPipeline();
                pipeline.addBefore("inflater", "saveTheEncoding", new SaveTheEncodingHandler());
                return pipeline;
            }
        });
    }

    private static class SaveTheEncodingHandler extends SimpleChannelHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
            Object msg = e.getMessage();
            if (msg instanceof HttpMessage) {
                HttpMessage m = (HttpMessage) msg;
                String contentEncoding = m.headers().get(HttpHeaders.Names.CONTENT_ENCODING);
                if (contentEncoding != null) {
                    m.headers().set("Glowroot-Original-Encoding", contentEncoding);
                }
            }
            ctx.sendUpstream(e);
        }
    }
}
