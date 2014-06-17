/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
 * Copyright (C) 2012 OneBusAway.
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Singleton;

/**
 * All calls to the WMATA API go through this class, which ensures that the API
 * rate limit is respected.
 *
 */
@Singleton
public class DownloaderService {

  private HttpClient _client = new DecompressingHttpClient(
      new DefaultHttpClient());
  private RateLimiter _limiter = RateLimiter.create(4);

  public synchronized InputStream openUrl(String uri) throws IOException {
    _limiter.acquire();

    HttpUriRequest request = new HttpGet(uri);
    HttpResponse response = _client.execute(request);
    HttpEntity entity = response.getEntity();

    InputStream in = entity.getContent();
    return in;
  }
}
