package reptor.test.distrbt.com.connect;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.MessageMapper;


public class PongMessages
{

    public static final int PONG_COUNT_ID = 1;


    public static class Count extends AbstractNetworkMessage
    {

        private final long m_number;
        private final int  m_payload;


        public Count(long number, int payload)
        {
            m_number  = number;
            m_payload = payload;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.putLong( m_number );
            out.position( out.position()+m_payload );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypeContentSize( mapper ) + Long.BYTES + m_payload;
        }


        public Count(ByteBuffer in, MessageMapper mapper, Object extcontext) throws IOException
        {
            super( in );

            m_number  = in.getLong();
            m_payload = in.remaining();
        }


        @Override
        public int getTypeID()
        {
            return PONG_COUNT_ID;
        }


        @Override
        public String toString()
        {
            return String.format( "{COUNT|%d|%d}", m_number, m_payload );
        }


        @Override
        public short getSender()
        {
            return 0;
        }


        public long getNumber()
        {
            return m_number;
        }


        @Override
        public boolean equals(Object obj)
        {
            if( obj==this )
                return true;

            if( obj==null || !( obj instanceof Count ) )
                return false;

            Count other = (Count) obj;

            return other.m_number==m_number;
        }


        @Override
        public int hashCode()
        {
            return Objects.hash( m_number );
        }

    }

}
