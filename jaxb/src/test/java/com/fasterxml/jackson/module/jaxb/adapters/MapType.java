package com.fasterxml.jackson.module.jaxb.adapters;

import com.fasterxml.jackson.module.jaxb.adapters.EntryType;

import java.util.List;

/**
 * @author Ryan Heaton
 */
public class MapType<K,V> {

  public List<EntryType<K, V>> entries;

  public MapType() {
  }

  public MapType(List<EntryType<K, V>> theEntries) {
    this.entries = theEntries;
  }

  public List<EntryType<K, V>> getEntries() {
    return entries;
  }

  public void setEntries(List<EntryType<K, V>> entries) {
    this.entries = entries;
  }
}
