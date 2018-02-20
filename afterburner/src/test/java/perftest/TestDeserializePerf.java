package perftest;

import java.io.IOException;

import com.fasterxml.jackson.core.json.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

public class TestDeserializePerf
{
    public final static class Bean
    {
        public int a, b, c123, d;
        public int e, foobar, g, habitus;

        public Bean setUp() {
            a = 1;
            b = 999;
            c123 = -1000;
            d = 13;
            e = 6;
            foobar = -33;
            g = 0;
            habitus = 123456789;
            return this;
        }
        
        public void setA(int v) { a = v; }
        public void setB(int v) { b = v; }
        public void setC(int v) { c123 = v; }
        public void setD(int v) { d = v; }

        public void setE(int v) { e = v; }
        public void setF(int v) { foobar = v; }
        public void setG(int v) { g = v; }
        public void setH(int v) { habitus = v; }

        @Override
        public int hashCode() {
            return a + b + c123 + d + e + foobar + g + habitus;
        }
    }
    
    public static void main(String[] args) throws Exception
    {
//        JsonFactory f = new org.codehaus.jackson.smile.SmileFactory();
        JsonFactory f = new JsonFactory();
        ObjectMapper mapperSlow = new ObjectMapper(f);
        
        // !!! TEST -- to get profile info, comment out:
//        mapperSlow.registerModule(new AfterburnerModule());

        ObjectMapper mapperFast = ObjectMapper.builder(f)
                .addModule(new AfterburnerModule())
                .build();
        new TestDeserializePerf().testWith(mapperSlow, mapperFast);
    }

    private void testWith(ObjectMapper slowMapper, ObjectMapper fastMapper)
        throws IOException
    {
        byte[] json = slowMapper.writeValueAsBytes(new Bean().setUp());
        boolean fast = true;
        
        while (true) {
            long now = System.currentTimeMillis();

            ObjectMapper m = fast ? fastMapper : slowMapper;
            Bean bean = null;
            
            for (int i = 0; i < 199999; ++i) {
                bean = m.readValue(json, Bean.class);
            }
            long time = System.currentTimeMillis() - now;
            
            System.out.println("Mapper (fast: "+fast+"; "+bean.hashCode()+"); took "+time+" msecs");

            fast = !fast;
        }
    }
   }
