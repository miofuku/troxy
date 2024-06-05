package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCertificationCommand;
import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.MutableData;


public class DummyTrinxImplementation implements TrinxImplementation
{

    private final boolean m_movepos;
    private final int     m_tmcertsize;
    private final int     m_tccertsize;


    public DummyTrinxImplementation(int certsize, boolean movepos)
    {
        m_movepos    = movepos;
        m_tmcertsize = certsize;
        m_tccertsize = certsize + Integer.BYTES;
    }


    @Override
    public DummyTrinx createTrinx(short tssid, int ncounters)
    {
        return new DummyTrinx( tssid, ncounters );
    }


    @Override
    public String toString()
    {
        return m_movepos ? "DummyTrinx(P)" : "DummyTrinx";
    }


    @Override
    public int getMacCertificateSize()
    {
        return m_tmcertsize;
    }


    @Override
    public int getCounterCertificateSize()
    {
        return m_tccertsize;
    }


    public class DummyTrinx implements Trinx
    {

        private final short  m_tssid;
        private final int    m_ncounters;


        public DummyTrinx(short tssid, int ncounters)
        {
            m_tssid     = tssid;
            m_ncounters = ncounters;
        }


        @Override
        public void close()
        {
        }


        @Override
        public String getImplementationName()
        {
            return DummyTrinxImplementation.this.toString();
        }


        @Override
        public short getID()
        {
            return m_tssid;
        }


        @Override
        public int getCounterCertificateSize()
        {
            return m_tccertsize;
        }


        @Override
        public int getNumberOfCounters()
        {
            return m_ncounters;
        }


        @Override
        public TrustedCounterValue counterValue(int ctrno)
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public int getMacCertificateSize()
        {
            return m_tmcertsize;
        }


        @Override
        public void executeCommand(Message msg)
        {
            TrinxCertificationCommand cmd = (TrinxCertificationCommand) msg;

            if( TrinxCommands.isVerification( cmd.getTypeID() ) )
                cmd.result( TrinxCommands.CERTIFICATE_VALID );
            else
                movePosition( cmd.getCertificateBuffer() );
        }


        private void movePosition(MutableData out)
        {
            if( m_movepos )
                out.adaptSlice( m_tccertsize );
        }


        @Override
        public void touch()
        {
        }

    }
}
