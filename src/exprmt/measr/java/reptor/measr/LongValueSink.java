package reptor.measr;

import java.util.function.LongConsumer;


// -- Another possibility would be to derive LongValueCollector from ValueCollector<Long> and
// -- to provide a default method for newValue(Long value) that calls newValue(long value).
// -- Since ValueCollector does not specify any methods (yet), the style of for instance
// -- java.util.function is adopted where primitives are completely separated from objects/generics.
public interface LongValueSink extends LongConsumer, ValueCollector
{
    static final LongValueSink EMPTY = new LongValueSink()
                                     {
                                         @Override
                                         public void reset()
                                         {
                                         }


                                         @Override
                                         public void accept(long value)
                                         {
                                         }
                                     };
}
