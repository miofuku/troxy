package reptor.measr;

import java.util.function.Consumer;


public interface ValueSink<V> extends Consumer<V>, ValueCollector
{
}
