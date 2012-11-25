/*
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

import com.caucho.hessian.client.HessianProxyFactory;
import java.net.MalformedURLException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.onebusaway.transit_data.services.TransitDataService;

/**
 *
 * @author kurt
 */
@Singleton
public class TransitDataServiceService {

    @Inject
    @Named("TDS.url")
    private String TDS_URL;
    private TransitDataService service;

    public TransitDataService getService() {
        return service;
    }

    @PostConstruct
    public void start() throws MalformedURLException {
        HessianProxyFactory factory = new HessianProxyFactory();
        service = (TransitDataService) factory.create(TransitDataService.class, TDS_URL);
    }
}
