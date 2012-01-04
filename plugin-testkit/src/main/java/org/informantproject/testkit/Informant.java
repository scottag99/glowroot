/**
 * Copyright 2011 the original author or authors.
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
package org.informantproject.testkit;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.informantproject.testkit.Configuration.CoreConfiguration;
import org.informantproject.testkit.GetTracesResponse.Trace;

import com.google.gson.Gson;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class Informant {

    private final int uiPort;
    private final AsyncHttpClient asyncHttpClient;

    private long baselineTime;

    Informant(int uiPort, AsyncHttpClient asyncHttpClient) {
        this.uiPort = uiPort;
        this.asyncHttpClient = asyncHttpClient;
    }

    public void setThresholdMillis(int thresholdMillis) throws Exception {
        CoreConfiguration coreConfiguration = getCoreConfiguration();
        coreConfiguration.setEnabled(true);
        coreConfiguration.setThresholdMillis(thresholdMillis);
        updateCoreConfiguration(coreConfiguration);
    }

    public String get(String path) throws InterruptedException, ExecutionException, IOException {
        BoundRequestBuilder request = asyncHttpClient.prepareGet("http://localhost:" + uiPort
                + path);
        Response response = request.execute().get();
        return response.getResponseBody();
    }

    public String post(String path, String data) throws InterruptedException, ExecutionException,
            IOException {

        BoundRequestBuilder request = asyncHttpClient.preparePost("http://localhost:" + uiPort
                + path);
        request.setBody(data);
        Response response = request.execute().get();
        return response.getResponseBody();
    }

    public CoreConfiguration getCoreConfiguration() throws InterruptedException,
            ExecutionException, IOException {

        String json = get("/configuration/read");
        return new Gson().fromJson(json, Configuration.class).getCoreConfiguration();
    }

    public void updateCoreConfiguration(CoreConfiguration coreConfiguration) throws Exception {
        post("/configuration/update",
                "{\"coreConfiguration\":" + new Gson().toJson(coreConfiguration) + "}");
    }

    // returns all traces since since the last call to InformantContainer.executeAppUnderTest()
    public List<Trace> getAllTraces() throws Exception {
        return getTraces(baselineTime, 0);
    }

    public List<Trace> getTraces(long start, long end) throws Exception {
        String json = get("/traces?start=" + start + "&end=" + end);
        return new Gson().fromJson(json, GetTracesResponse.class).getTraces();
    }

    void resetBaselineTime() throws InterruptedException {
        if (baselineTime != 0) {
            // guarantee that there is no possible overlap
            Thread.sleep(1);
        }
        this.baselineTime = System.currentTimeMillis();
    }
}