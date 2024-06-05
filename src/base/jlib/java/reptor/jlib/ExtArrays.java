package reptor.jlib;

import java.lang.reflect.Array;

public final class ExtArrays
{

    public static boolean equalsUnchecked(byte[] a, int offa, byte[] b, int offb, int len)
    {
        int i = 0;

        while( i++ < len )
            if( a[offa++] != b[offb++] )
                return false;

        return true;
    }


    public static int[] convertToIntArray(byte[] a)
    {
        int[] ret = new int[ a.length ];

        for (int i=0; i<a.length; i++)
        {
            ret[i] = a[i] & 0xff;
        }

        return ret;
    }


    public static <T> T[] copyOfSelection(T[] array, int[] indices)
    {
        @SuppressWarnings("unchecked")
        T[] copy = (T[]) Array.newInstance( array.getClass(), indices.length );

        return copyOfSelection( array, indices, copy );
    }


    public static <T, F extends T> T[] copyOfSelection(F[] from, int[] indices, T[] to)
    {
        for( int i=0; i<indices.length; i++ )
            to[ i ] = from[ indices[ i ] ];

        return to;
    }

}
