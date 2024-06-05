package reptor.jlib.collect;


@FunctionalInterface
public interface LongBiConsumer<U>
{
    void accept(long t, U u);
}
