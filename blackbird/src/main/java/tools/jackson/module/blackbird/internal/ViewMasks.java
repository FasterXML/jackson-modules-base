package tools.jackson.module.blackbird.internal;

import java.util.function.ToLongFunction;

/**
 * Copy-on-write cache of view to visibility bitmask (bit i: property arm i
 * is visible in the view) for one codec. View sets are small and stable per
 * application, so lookups are a reference scan. The cached view classes
 * unload with the codec and its mapper.
 */
final class ViewMasks
{
    private static final Class<?>[] NO_VIEWS = new Class<?>[0];
    private static final long[] NO_MASKS = new long[0];

    private final ToLongFunction<Class<?>> _compute;

    private volatile Class<?>[] _views = NO_VIEWS;
    private volatile long[] _masks = NO_MASKS;

    ViewMasks(ToLongFunction<Class<?>> compute) {
        _compute = compute;
    }

    long maskFor(Class<?> view) {
        Class<?>[] views = _views;
        long[] masks = _masks;
        for (int i = 0; i < views.length && i < masks.length; i++) {
            if (views[i] == view) {
                return masks[i];
            }
        }
        return add(view);
    }

    private synchronized long add(Class<?> view) {
        Class<?>[] views = _views;
        for (int i = 0; i < views.length; i++) {
            if (views[i] == view) {
                return _masks[i];
            }
        }
        long mask = _compute.applyAsLong(view);
        Class<?>[] newViews = new Class<?>[views.length + 1];
        long[] newMasks = new long[views.length + 1];
        System.arraycopy(views, 0, newViews, 0, views.length);
        System.arraycopy(_masks, 0, newMasks, 0, views.length);
        newViews[views.length] = view;
        newMasks[views.length] = mask;
        // Masks first: a reader that sees the new views array also sees them.
        _masks = newMasks;
        _views = newViews;
        return mask;
    }
}
