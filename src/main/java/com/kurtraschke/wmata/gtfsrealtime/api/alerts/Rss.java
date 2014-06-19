/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
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
package com.kurtraschke.wmata.gtfsrealtime.api.alerts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@JacksonXmlRootElement(localName = "rss")
@JsonIgnoreProperties(ignoreUnknown=true)
public class Rss implements Serializable {
  private static final long serialVersionUID = 1L;

  private Channel channel;
  private String version;

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return "Rss [channel=" + channel + ", version=" + version + "]";
  }

  @JacksonXmlRootElement(localName = "channel")
  @JsonIgnoreProperties(ignoreUnknown=true)
  public static class Channel implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String link;
    private String description;
    private String lastBuildDate;

    @JsonProperty("item")
    @JacksonXmlElementWrapper(useWrapping=false)
    private List<Item> items;

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

    public String getLastBuildDate() {
      return lastBuildDate;
    }

    public void setLastBuildDate(String lastBuildDate) {
      this.lastBuildDate = lastBuildDate;
    }

    public List<Item> getItems() {
      return (items != null) ? items : Collections.<Item>emptyList();
    }

    public void setItems(List<Item> items) {
      this.items = items;
    }

    @Override
    public String toString() {
      return "Channel [title=" + title + ", description=" + description
          + ", lastBuildDate=" + lastBuildDate + ", items=" + items + "]";
    }

  }

}
