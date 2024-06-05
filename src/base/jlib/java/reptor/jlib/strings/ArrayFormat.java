package reptor.jlib.strings;

import java.util.Arrays;
import java.util.List;


public interface ArrayFormat
{
    <T> String valuesToString(T[] array);
    <T> String valuesToString(List<T> array);

    static final ArrayFormat DEFAULT = new ArrayFormat()
    {
        @Override
        public <T> String valuesToString(T[] array)
        {
            return valuesToString( Arrays.asList( array ) );
        }

        @Override
        public <T> String valuesToString(List<T> array)
        {
            if( array==null )
                return "<null>";

            StringBuffer sb = new StringBuffer();

            sb.append( "[" );
            if( !array.isEmpty() )
            {
                for( T e : array )
                {
                    sb.append( e==null ? "<null>" : e.toString() );
                    sb.append( ", " );
                }
                sb.delete( sb.length()-2, sb.length() );
            }
            sb.append( "]" );

            return sb.toString();
        }
    };
}
