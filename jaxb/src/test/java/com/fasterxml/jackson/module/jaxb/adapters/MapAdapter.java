package com.fasterxml.jackson.module.jaxb.adapters;

import javax.xml.bind.annotation.adapters.XmlAdapter;


import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MapAdapter<K, V> extends XmlAdapter<MapType<K, V>, Map<K, V>>
{
  @Override
  public MapType<K, V> marshal(Map<K, V> v) throws Exception
  {
     final List<EntryType<K, V>> theEntries = new LinkedList<EntryType<K, V>>();
     for (final Map.Entry<K, V> anEntry : v.entrySet()) {
        theEntries.add(new EntryType<K, V>(anEntry.getKey(), anEntry.getValue()));
     }
     return new MapType<K, V>(theEntries);
  }

  @Override
  public Map<K, V> unmarshal(MapType<K, V> v) throws Exception
  {
     final Map<K, V> theMap = new HashMap<K, V>();
     for (final EntryType<K, V> anEntry : v.getEntries()) {
        theMap.put(anEntry.getKey(), anEntry.getValue());
     }
     return theMap;
  }
}