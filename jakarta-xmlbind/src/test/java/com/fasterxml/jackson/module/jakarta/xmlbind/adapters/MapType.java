package com.fasterxml.jackson.module.jakarta.xmlbind.adapters;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MapType<K,V>
{
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
