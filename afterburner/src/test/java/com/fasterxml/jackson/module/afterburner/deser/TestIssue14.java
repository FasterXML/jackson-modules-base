package com.fasterxml.jackson.module.afterburner.deser;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestIssue14 extends AfterburnerTestBase
{
    public void testIssue() throws Exception
    {
        // create this ridiculously complicated object
        ItemData data = new ItemData();
        data.denomination = 100;
        Item item = new Item();
        item.data = data;
        item.productId = 123;
        
        List<Item> itemList = new ArrayList<Item>();
        itemList.add(item);
    
        PlaceOrderRequest order = new PlaceOrderRequest();
        order.orderId = 68723496;
        order.userId = "123489043";
        order.amount = 250;
        order.status = "placed";
        order.items = itemList;
        
        final Date now = new Date(999999L);
        
        order.createdAt = now;
        order.updatedAt = now;

        ObjectMapper vanillaMapper = new ObjectMapper();
        ObjectMapper abMapper = ObjectMapper.builder()
                .addModule(new AfterburnerModule())
                .build();

        // First: ensure that serialization produces identical output
        
        String origJson = vanillaMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(order);

        String abJson = abMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(order);

        assertEquals(origJson, abJson);
        
        // Then read the string and turn it back into an object
        // this will cause an exception unless the AfterburnerModule is commented out
        order = abMapper.readValue(abJson, PlaceOrderRequest.class);
        assertNotNull(order);
        assertEquals(250, order.amount);
    }
}

class PlaceOrderRequest {
     @JsonProperty("id") public long orderId;
     @JsonProperty("from") public String userId;
     public int amount;
     public String status;
     public List<Item> items;
     @JsonProperty("created_at") public Date createdAt;
     @JsonProperty("updated_at") public Date updatedAt;
}
     
class Item {
      @JsonProperty("product_id") public int productId;
      public int quantity;
      public ItemData data;
}

@JsonInclude(Include.NON_NULL)
class ItemData {
     public int denomination;
     public List<VLTBet> bets;
}

class VLTBet {
      public int index;
      public String selection;
      public int stake;
      public int won;
}
