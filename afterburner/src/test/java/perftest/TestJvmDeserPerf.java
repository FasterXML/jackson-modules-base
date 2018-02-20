package perftest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonFactory;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Micro-benchmark for comparing performance of bean deserialization
 */
public final class TestJvmDeserPerf
{
    /*
    /**********************************************************
    /* Actual test
    /**********************************************************
     */

    private final int REPS;

    private TestJvmDeserPerf() {
        // Let's try to guestimate suitable size
        REPS = 35000;
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
        int sum = 0;

        final MediaItem item = buildItem();
        JsonFactory jsonF =
//            new org.codehaus.jackson.smile.SmileFactory();
            new JsonFactory()
        ;
        
        final ObjectMapper jsonMapper = ObjectMapper.builder(jsonF)
                .addModule(new com.fasterxml.jackson.module.afterburner.AfterburnerModule())
                .build();

        byte[] json = jsonMapper.writeValueAsBytes(item);
        System.out.println("Warmed up: data size is "+json.length+" bytes; "+REPS+" reps -> "
                +((REPS * json.length) >> 10)+" kB per iteration");
        System.out.println();

        int round = 0;
        while (true) {
//            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }

            round = (round + 1) % 2;
            long curr = System.currentTimeMillis();
            String msg;
            boolean lf = (round == 0);

            switch (round) {

            case 0:
                msg = "Deserialize/databind, JSON";
                sum += testDeser(jsonMapper, json, REPS);
                break;

            case 1:
                msg = "Deserialize/manual, JSON";
                sum += testDeserStreaming(jsonMapper, json, REPS);
                break;

            default:
                throw new Error("Internal error");
            }

            curr = System.currentTimeMillis() - curr;
            if (lf) {
                System.out.println();
            }
            System.out.println("Test '"+msg+"' -> "+curr+" msecs ("
                               +(sum & 0xFF)+").");
        }
    }

    protected int testDeser(ObjectMapper mapper, byte[] input, int reps)
        throws Exception
    {
        JavaType type = TypeFactory.defaultInstance().constructType(MediaItem.class);
        MediaItem item = null;
        for (int i = 0; i < reps; ++i) {
            item = mapper.readValue(input, 0, input.length, type);
        }
        return item.hashCode(); // just to get some non-optimizable number
    }

    protected int testDeserStreaming(ObjectMapper mapper, byte[] input, int reps)
        throws Exception
    {
        MediaItem item = null;
        for (int i = 0; i < reps; ++i) {
            JsonParser jp = mapper.createParser(input);
            item = MediaItem.deserialize(jp);
            jp.close();
        }
        return item.hashCode(); // just to get some non-optimizable number
    }
    
    public static void main(String[] args) throws Exception
    {
        new TestJvmDeserPerf().test();
    }
}
