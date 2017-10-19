package com.fasterxml.jackson.module.afterburner.deser;

import java.util.*;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.*;

public class SuperSonicDeserializerBuilder extends BeanDeserializerBuilder
{
    public SuperSonicDeserializerBuilder(BeanDeserializerBuilder base) {
        super(base);
    }

    @Override
    public JsonDeserializer<?> build()
    {
        BeanDeserializer deser = (BeanDeserializer) super.build();
        // only create custom one, if existing one is standard deserializer;
        if (deser.getClass() == BeanDeserializer.class) {
            BeanDeserializer beanDeser = (BeanDeserializer) deser;
            Iterator<SettableBeanProperty> it = getProperties();
            // also: only build custom one for non-empty beans:
            if (it.hasNext()) {
                // So let's find actual order of properties, necessary for optimal access
                ArrayList<SettableBeanProperty> props = new ArrayList<SettableBeanProperty>();
                do {
                    props.add(it.next());
                } while (it.hasNext());
                if (props.size() > 6) {
                    return new SuperSonicBeanDeserializer(beanDeser, props);
                }
                return new SuperSonicUnrolledDeserializer(beanDeser, props);
            }
        }
        return deser;
    }
}
