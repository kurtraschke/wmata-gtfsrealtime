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

import com.google.common.util.concurrent.RateLimiter;
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

/**
 * All calls to the WMATA API go through this class, which ensures that the
 * API rate limit is respected.
 *
 * @author bdferris
 */
@Singleton
public class DownloaderService {

    private DefaultHttpClient _client = new DefaultHttpClient();
    private RateLimiter _limiter = RateLimiter.create(5.0);

    public synchronized InputStream openUrl(String uri) throws IOException {

        _limiter.acquire();

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
}
