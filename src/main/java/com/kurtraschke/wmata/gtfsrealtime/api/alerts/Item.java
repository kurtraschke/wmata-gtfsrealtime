package com.kurtraschke.wmata.gtfsrealtime.api.alerts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.kurtraschke.wmata.gtfsrealtime.DateTimeUtils;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

@JacksonXmlRootElement(localName = "item")
@JsonIgnoreProperties(ignoreUnknown=true)
public class Item implements Serializable {
  private static final long serialVersionUID = 1L;
  private String title;
  private String link;
  private String description;
  private String source;
  private Date pubDate;
  private UUID guid; //dependent on WMATA's implementation using a proper UUID as the guid

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

  public void setPubDate(String pubDate) throws ParseException {
    this.pubDate = DateTimeUtils.parseRssTimestamp(pubDate);
  }

  public UUID getGuid() {
    return guid;
  }

  public void setGuid(String guid) {
    this.guid = UUID.fromString(guid);
  }

  @Override
  public String toString() {
    return "Item [title=" + title + ", link=" + link + ", description="
        + description + ", source=" + source + ", pubDate=" + pubDate
        + ", guid=" + guid + "]";
  }

}
