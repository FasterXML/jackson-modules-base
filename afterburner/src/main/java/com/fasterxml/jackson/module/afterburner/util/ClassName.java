package com.fasterxml.jackson.module.afterburner.util;

import java.util.zip.Adler32;

/**
 * Accessing various permutations of dotted/slashed representations gets
 * tiresome after a while, so here's an abstraction for hiding complexities,
 * and for performing lazy transformations as necessary.
 */
public class ClassName
{
    public final static String TEMPLATE_SUFFIX = actualClassName("", 0L);

    // Basenames with no checksum suffix
    protected final String _dottedBase;
    
    protected String _slashedBase;

    protected String _dottedName, _slashedName;
    
    protected long _checksum;
    
    private ClassName(String dottedBase) {
        _dottedBase = dottedBase;
    }

    public static ClassName constructFor(Class<?> baseClass, String suffix) {
        return new ClassName(baseClass.getName() + suffix);
    }

    public void assignChecksum(byte[] data) {
        long l = adler32(data);
        if (_checksum != 0L) {
            throw new IllegalStateException("Trying to re-assign checksum as 0x"+Long.toHexString(l)
                    +" (had 0x"+Long.toHexString(_checksum)+")");
        }
        // Need to mask unlikely checksum of 0
        if (l == 0L) {
            l = 1;
        }
        _checksum = l;
    }
    
    public String getDottedTemplate() {
        return _dottedBase + TEMPLATE_SUFFIX;
    }

    public String getSlashedTemplate() {
        return getSlashedBase() + TEMPLATE_SUFFIX;
    }

    public String getDottedName() {
        if (_dottedName == null) {
            if (_checksum == 0) {
                throw new IllegalStateException("No checksum assigned yet");
            }
            _dottedName = String.format("%s%08x", getDottedBase(), (int) _checksum);
        }
        return _dottedName;
    }

    public String getSlashedName() {
        if (_slashedName == null) {
            if (_checksum == 0) {
                throw new IllegalStateException("No checksum assigned yet");
            } 
            _slashedName = String.format("%s%08x", getSlashedBase(), (int) _checksum);
        }
        return _slashedName;
    }

    public String getSourceFilename() {
        return getSlashedBase() + ".java";
    }
    
    public String getDottedBase() {
        return _dottedBase;
    }

    public String getSlashedBase() {
        if (_slashedBase == null) {
            _slashedBase = dotsToSlashes(_dottedBase);
        }
        return _slashedBase;
    }

    @Override
    public String toString() {
        return getDottedName();
    }

    private static String actualClassName(String base, long checksum) {
        return String.format("%s%08x", base, (int) checksum);
    }

    protected static String dotsToSlashes(String className) {
        return className.replace(".", "/");
    }

    protected static long adler32(byte[] data)
    {
        Adler32 adler = new Adler32();
        adler.update(data);
        return adler.getValue();
    }
}
