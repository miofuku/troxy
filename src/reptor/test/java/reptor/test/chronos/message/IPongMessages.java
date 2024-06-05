package reptor.test.chronos.message;

import reptor.distrbt.com.Message;


public class IPongMessages
{

    public static final int     PONG_ID = 1;

    public static class InternalCount implements Message
    {
        private final int   m_sender;
        private long        m_number;
        private long        m_starttime;

        public InternalCount(int sender)
        {
            m_sender = sender;
        }

        public void init(long number, long starttime)
        {
            m_number    = number;
            m_starttime = starttime;
        }

        @Override
        public int getTypeID()
        {
            return PONG_ID;
        }

        public int getSender()
        {
            return m_sender;
        }

        public long getNumber()
        {
            return m_number;
        }

        public long getStartTime()
        {
            return m_starttime;
        }
    }

}
