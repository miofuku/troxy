package reptor.jlib.strings;

import java.io.IOException;
import java.util.Formatter;


// TODO: Are there already existing classes in Commons or Guava?
public class StringBuilderExtender implements Appendable, CharSequence
{
    private final StringBuilder m_sb;

    private Formatter           m_formatter = null;


    public StringBuilderExtender()
    {
        m_sb = new StringBuilder();
    }


    public StringBuilderExtender(String str)
    {
        m_sb = new StringBuilder( str );
    }


    public StringBuilderExtender(int capacity)
    {
        m_sb = new StringBuilder( capacity );
    }


    public StringBuilder base()
    {
        return m_sb;
    }


    public Formatter formatter()
    {
        if( m_formatter == null )
            m_formatter = new Formatter( m_sb );

        return m_formatter;
    }


    public StringBuilderExtender format(String format, Object... args)
    {
        formatter().format( format, args );

        return this;
    }


    public StringBuilderExtender formatln(String format, Object... args)
    {
        formatter().format( format, args );

        return appendln();
    }


    public StringBuilderExtender append(StringBuilder sb)
    {
        m_sb.append( sb );

        return this;
    }


    public StringBuilderExtender append(String str)
    {
        m_sb.append( str );

        return this;
    }


    public StringBuilderExtender appendln(String str)
    {
        m_sb.append( str );
        return appendln();
    }


    public StringBuilderExtender append(int i)
    {
        m_sb.append( i );

        return this;
    }


    public StringBuilderExtender append(long l)
    {
        m_sb.append( l );

        return this;
    }


    public StringBuilderExtender appendln()
    {
        m_sb.append( System.lineSeparator() );

        return this;
    }


    @Override
    public int length()
    {
        return m_sb.length();
    }


    @Override
    public char charAt(int index)
    {
        return m_sb.charAt( index );
    }


    @Override
    public CharSequence subSequence(int start, int end)
    {
        return m_sb.subSequence( start, end );
    }


    @Override
    public StringBuilderExtender append(CharSequence s) throws IOException
    {
        m_sb.append( s );

        return this;
    }


    @Override
    public StringBuilderExtender append(CharSequence s, int start, int end) throws IOException
    {
        m_sb.append( s, start, end );

        return this;
    }


    @Override
    public StringBuilderExtender append(char c) throws IOException
    {
        m_sb.append( c );

        return this;
    }


    @Override
    public String toString()
    {
        return m_sb.toString();
    }
}
