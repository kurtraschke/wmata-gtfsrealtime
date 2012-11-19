/**
 * Copyright (C) 2012 Kurt Raschke
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.kurtraschke.wmatagtfsrealtime.api;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 *
 * @author kurt
 */
public class WMATAAlert implements Serializable {

    private static final long serialVersionUID = 1L;
    private String title;
    private String link;
    private String description;
    private String source;
    private Date pubDate;
    private UUID guid;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Date getPubDate() {
        return pubDate;
    }

    public void setPubDate(String pubDate) {
        //this.pubDate = pubDate;
    }

    public UUID getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = UUID.fromString(guid);
    }
}
