/*
 * Copyright (C) 2012 OneBusAway.
 * Copyright (C) 2012 Kurt Raschke
 * 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kurtraschke.wmatagtfsrealtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import javax.inject.Singleton;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All calls to the WMATA API go through this class, which keeps a running tab
 * on our downloading throughput to make sure we don't exceed the call rate
 * limit set for the API.
 *
 * @author bdferris
 */
@Singleton
public class DownloaderService {

    private static final Logger _log = LoggerFactory.getLogger(DownloaderService.class);
    private DefaultHttpClient _client = new DefaultHttpClient();

    /**
     * Time, in seconds
     */
    private long lastCall = 0;
    private int callsSoFar;

    public synchronized InputStream openUrl(String uri) throws IOException {

        stallIfNeeded();

        HttpUriRequest request = new HttpGet(uri);
        request.addHeader("Accept-Encoding", "gzip");
        HttpResponse response = _client.execute(request);
        HttpEntity entity = response.getEntity();

        InputStream in = entity.getContent();
        Header contentEncoding = response.getFirstHeader("Content-Encoding");
        if (contentEncoding != null
                && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
            in = new GZIPInputStream(in);
        }
        return in;
    }

    private void stallIfNeeded() {

        long now = System.currentTimeMillis() / 1000;

        if (now > lastCall) {
            lastCall = now;
            callsSoFar = 1;
        } else {

            if (callsSoFar >= 5) {
                int delay = (int) (1.5 * 1000);
                _log.info("thottling: delay=" + delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            } else {
                callsSoFar++;
            }
        }
    }
}
