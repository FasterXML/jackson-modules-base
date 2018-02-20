package perftest;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.JsonFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

public final class TestJvmSerPerf
{
    /*
    /**********************************************************
    /* Actual test
    /**********************************************************
     */

    private final int REPS;

    private TestJvmSerPerf() throws Exception
    {
        // Let's try to guestimate suitable size...
        REPS = 20000;
    }

    private MediaItem buildItem()
    {
        MediaItem.Content content = new MediaItem.Content();
        content.setPlayer(MediaItem.Content.Player.JAVA);
        content.setUri("http://javaone.com/keynote.mpg");
        content.setTitle("Javaone Keynote");
        content.setWidth(640);
        content.setHeight(480);
        content.setFormat("video/mpeg4");
        content.setDuration(18000000L);
        content.setSize(58982400L);
        content.setBitrate(262144);
        content.setCopyright("None");
        content.addPerson("Bill Gates");
        content.addPerson("Steve Jobs");

        MediaItem item = new MediaItem(content);

        item.addPhoto(new MediaItem.Photo("http://javaone.com/keynote_large.jpg", "Javaone Keynote", 1024, 768, MediaItem.Photo.Size.LARGE));
        item.addPhoto(new MediaItem.Photo("http://javaone.com/keynote_small.jpg", "Javaone Keynote", 320, 240, MediaItem.Photo.Size.SMALL));

        return item;
    }
    
    public void test()
        throws Exception
    {
        int i = 0;
        int sum = 0;

        ByteArrayOutputStream result = new ByteArrayOutputStream();

        final MediaItem item = buildItem();
        final JsonFactory jsonF =
            new JsonFactory()
//            new org.codehaus.jackson.smile.SmileFactory();
        ;
//        ((SmileFactory) jsonF).configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, false);
            
        final ObjectMapper jsonMapper = ObjectMapper.builder(jsonF)
                .addModule(new AfterburnerModule())
//      .configure(SerializationConfig.Feature.USE_STATIC_TYPING, true)
                .build();

        /*
        final SmileFactory smileFactory = new SmileFactory();
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, true);
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, false);
        final ObjectMapper smileMapper = new ObjectMapper(smileFactory);
        */
        
        while (true) {
//            Thread.sleep(150L);
            ++i;
            int round = (i % 2);

            // override?
//            round = 0;

            long curr = System.currentTimeMillis();
            String msg;

            switch (round) {

            case 0:
                msg = "Serialize, JSON/databind";
                sum += testObjectSer(jsonMapper, item, REPS+REPS, result);
                break;

            case 1:
                msg = "Serialize, JSON/manual";
                sum += testObjectSer(jsonMapper, item, REPS+REPS, result);
                break;

                /*
            case 2:
                msg = "Serialize, Smile";
                sum += testObjectSer(smileMapper, item, REPS, result);
                break;

            case 3:
                msg = "Serialize, Smile/manual";
                sum += testObjectSer(smileFactory, item, REPS+REPS, result);
                break;
*/
                
            default:
                throw new Error("Internal error");
            }

            curr = System.currentTimeMillis() - curr;
//            if (round == 0) {  System.out.println(); }
            System.out.println("Test '"+msg+"' -> "+curr+" msecs ("+(sum & 0xFF)+").");
            if ((i & 0x1F) == 0) { // GC every 64 rounds
                System.out.println("[GC]");
                Thread.sleep(20L);
                System.gc();
                Thread.sleep(20L);
            }
        }
    }

    protected int testObjectSer(ObjectMapper mapper, Object value, int reps, ByteArrayOutputStream result)
        throws Exception
    {
        for (int i = 0; i < reps; ++i) {
            result.reset();
            mapper.writeValue(result, value);
        }
        return result.size(); // just to get some non-optimizable number
    }

    protected int testObjectSerStreaming(ObjectMapper mapper, MediaItem value, int reps,
            ByteArrayOutputStream result)
        throws Exception
    {
        for (int i = 0; i < reps; ++i) {
            result.reset();
            JsonGenerator g = mapper.createGenerator(result, JsonEncoding.UTF8);
            value.serialize(g);
            g.close();
        }
        return result.size(); // just to get some non-optimizable number
    }
    
    public static void main(String[] args) throws Exception
    {
        new TestJvmSerPerf().test();
    }
}
