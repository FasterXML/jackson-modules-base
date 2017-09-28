package com.fasterxml.jackson.module.paranamer;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import com.thoughtworks.paranamer.CachingParanamer;
import com.thoughtworks.paranamer.Paranamer;

/**
 * Convenience module that registers stand-alone {@link ParanamerOnJacksonAnnotationIntrospector}
 * after existing introspectors, to add support for discovering names of
 * creator (constructor, factory method) parameters automatically, without
 * explicit annotations.
 *<p>
 * Note that use of this module is optional: the only thing it does is register
 * annotation introspector; so you can instead choose to do this from your
 * custom module, or directly configure {@link ObjectMapper}.
 */
public class ParanamerModule
    extends SimpleModule
{
    private static final long serialVersionUID = 1L;

    /**
     * Caller may specify alternate {@link Paranamer} to use, over
     * default <code>BytecodeReadingParanamer</code>
     */
    protected final Paranamer _paranamer;

    public ParanamerModule() {
        this(new CachingParanamer(new BytecodeReadingParanamer()));
    }

    /**
     * @param paranamer Paranamer instance to use for introspection
     */
    public ParanamerModule(Paranamer paranamer) {
        super(PackageVersion.VERSION);
        _paranamer = paranamer;
    }
    
    @Override
    public void setupModule(com.fasterxml.jackson.databind.Module.SetupContext context)
    {
        super.setupModule(context);
        // Append after other introspectors (instead of before) since
        // explicit annotations should have precedence
        context.appendAnnotationIntrospector(new ParanamerAnnotationIntrospector(_paranamer));
    }
}
