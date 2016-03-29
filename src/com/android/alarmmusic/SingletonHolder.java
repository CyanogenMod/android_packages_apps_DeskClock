package com.android.alarmmusic;

/**
 * Encapsulates a threadsafe singleton pattern.
 *
 * This class is designed to be used as a public constant, living within a class that has a private constructor.
 * It defines a {@link #create(I)} method that will only ever be called once, upon the first call of {@link #get(I)}.
 * That method is responsible for creating the actual singleton instance, and that instance will be returned for all
 * future calls of {@link #get(I)}.
 *
 * Example:
 * <code>
 *     public class FooSingleton {
 *         public static final SingletonHolder&lt;FooSingleton, ParamObject&gt; HOLDER =
 *                 new SingletonHolder&lt;FooSingleton, ParamObject&gt;() {
 *                     @Override
 *                     protected FooSingleton create(ParamObject param) {
 *                         return new FooSingleton(param);
 *                     }
 *                 };
 *
 *         private FooSingleton(ParamObject param) {
 *
 *         }
 *     }
 *
 *     // somewhere else
 *     FooSingleton.HOLDER.get(params).doStuff();
 * </code>
 * @param <E> The type of the class to hold as a singleton.
 * @param <I> A parameter object to use during creation of the singleton object.
 */
public abstract class SingletonHolder<E, I> {
    private E mInstance;
    private final Object LOCK = new Object();

    public final E get(I initializer) {
        if (null == mInstance) {
            synchronized (LOCK) {
                if (null == mInstance) {
                    mInstance = create(initializer);
                }
            }
        }

        return mInstance;
    }

    protected abstract E create(I initializer);
}
