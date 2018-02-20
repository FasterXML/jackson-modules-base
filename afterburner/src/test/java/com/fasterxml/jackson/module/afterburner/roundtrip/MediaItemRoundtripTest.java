package com.fasterxml.jackson.module.afterburner.roundtrip;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

/**
 * Let's use a non-trivial POJO from "jvm-serializers" benchmark as
 * sort of sanity check.
 */
public class MediaItemRoundtripTest extends AfterburnerTestBase
{
    private final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testSimple() throws Exception
    {
        MediaItem input = buildItem();

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(input);
        MediaItem result = MAPPER.readValue(json, MediaItem.class);

        String json2 = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);

        assertEquals(json, json2);
    }

    private MediaItem buildItem() {
        MediaItem.Content content = new MediaItem.Content();
        content.setUri("http://javaone.com/keynote.mpg");
        content.setTitle("Javaone Keynote");
        content.setWidth(640);
        content.setHeight(480);
        content.setFormat("video/mpg4");
        content.setDuration(18000000);
        content.setSize(58982400L);
        content.setBitrate(262144);
        content.setPlayer(MediaItem.Player.JAVA);
        content.setCopyright("None");
        content.addPerson("Bill Gates");
        content.addPerson("Steve Jobs");

        MediaItem item = new MediaItem(content);
        item.addPhoto(new MediaItem.Photo("http://javaone.com/keynote_large.jpg", "Javaone Keynote",
                1024, 768, MediaItem.Size.LARGE));
        item.addPhoto(new MediaItem.Photo("http://javaone.com/keynote_small.jpg", "Javaone Keynote",
                320, 240, MediaItem.Size.SMALL));
        return item;
    }
}
