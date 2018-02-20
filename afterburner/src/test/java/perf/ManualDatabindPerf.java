package perf;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

public class ManualDatabindPerf
{
    protected int hash;
    
    @SuppressWarnings("resource")
    private <T1, T2> void test(ObjectMapper mapper1, ObjectMapper mapper2,
            T1 inputValue, Class<T1> inputClass)
        throws Exception
    {
        final byte[] output = mapper1.writeValueAsBytes(inputValue);

        final int REPS;
        // sanity check, verify identical output
        {
            final byte[] output2 = mapper2.writeValueAsBytes(inputValue);

            int ix = 0;
            int end = Math.min(output.length, output2.length);
            
            for (; ix < end; ++ix) {
                if (output[ix] != output2[ix]) {
                    break;
                }
            }

            if (ix != output.length) {
                System.err.printf("FAIL: input 1 and 2 differ at byte #%d (lengths: %d / %d)\n",
                        ix, output.length, output2.length);
                System.exit(1);
            }
            
            // Let's try to guestimate suitable size, N megs of output
            REPS = (int) ((double) (9 * 1000 * 1000) / (double) output.length);
            System.out.printf("Read %d bytes to bind, will do %d repetitions\n",
                    output.length, REPS);
        }

        final ObjectReader reader1 = mapper1.readerFor(inputClass);
        final ObjectReader reader2 = mapper2.readerFor(inputClass);
        
        final ObjectWriter writer1 = mapper1.writerFor(inputClass);
        final ObjectWriter writer2 = mapper2.writerFor(inputClass);

        
        
        int i = 0;
        int roundsDone = 0;
        final int TYPES = 4;
        final int WARMUP_ROUNDS = 5;

        final long[] times = new long[TYPES];

        while (true) {
            final NopOutputStream out = new NopOutputStream();
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
            int round = (i++ % TYPES);

            String msg;
            boolean lf = (round == 0);

round = 1;

            long msecs;
            ObjectReader reader = null;
            ObjectWriter writer = null;
            
            switch (round) {
            case 0:
                msg = "Read, vanilla";
                reader = reader1;
                break;
            case 1:
                msg = "Read, Afterburner";
                reader = reader2;
                break;
            case 2:
                msg = "Write, vanilla";
                writer = writer1;
                break;
            case 3:
                msg = "Write, Afterburner";
                writer = writer2;
                break;
            default:
                throw new Error();
            }
            
            if (reader != null) {
                msecs = testRead(REPS, output, reader);
            } else {
                msecs = testWrite(REPS, inputValue, writer, out);
            }

            // skip first 5 rounds to let results stabilize
            if (roundsDone >= WARMUP_ROUNDS) {
                times[round] += msecs;
            }
            
            System.out.printf("Test '%s' [hash: 0x%s] -> %d msecs\n", msg, this.hash, msecs);
            if (lf) {
                ++roundsDone;
                if ((roundsDone % 3) == 0 && roundsDone > WARMUP_ROUNDS) {
                    double den = (double) (roundsDone - WARMUP_ROUNDS);
                    System.out.printf("Averages after %d rounds (vanilla/AB-read, vanilla/AB-write): "
                            +"%.1f/%.1f (%.1f%%), %.1f/%.1f (%.1f%%) msecs\n",
                            (int) den
                            ,times[0] / den, times[1] / den, 100.0 * times[1] / times[0]
                            ,times[2] / den, times[3] / den, 100.0 * times[3] / times[2]
                            );
                            
                }
                System.out.println();
            }
            if ((i % 17) == 0) {
                System.out.println("[GC]");
                Thread.sleep(100L);
                System.gc();
                Thread.sleep(100L);
            }
        }
    }

    private final long testRead(int REPS, byte[] input, ObjectReader reader) throws Exception
    {
        long start = System.currentTimeMillis();
        Object ob = null;
        while (--REPS >= 0) {
            ob = reader.readValue(input);
        }
        long time = System.currentTimeMillis() - start;
        hash = ob.hashCode();
        return time;
    }

    private final long testWrite(int REPS, Object value, ObjectWriter writer, NopOutputStream out) throws Exception
    {
        long start = System.currentTimeMillis();
        while (--REPS >= 0) {
            writer.writeValue(out, value);
        }
        return System.currentTimeMillis() - start;
    }
    
    public static void main(String[] args) throws Exception
    {
        if (args.length != 0) {
            System.err.println("Usage: java ...");
            System.exit(1);
        }
        ObjectMapper vanilla = new ObjectMapper();
        ObjectMapper burnt = ObjectMapper.builder()
                .addModule(new AfterburnerModule())
                .build();

        /*
        TestPojo input = new TestPojo(1245, -99, "Billy-Bob",
                new Value(27, 116));

        new ManualDatabindPerf().test(vanilla, burnt, input, TestPojo.class);
        */

        MediaItem media = MediaItem.buildItem();
        new ManualDatabindPerf().test(vanilla, burnt, media, MediaItem.class);
    }
}
