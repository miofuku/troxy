package reptor.replct.map;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.distrbt.com.map.BasicMessageDigestionStrategy;
import reptor.distrbt.com.map.BasicMessageMapper;
import reptor.distrbt.com.map.BasicMessageDigestionStrategy.Variant;
import reptor.distrbt.com.map.HTTPMessageMapper;
import reptor.jlib.hash.HashAlgorithm;
import reptor.replct.common.settings.SettingsReader;


public class Mapping
{

    private final NetworkMessageRegistry            m_climsgs;
    private final NetworkMessageRegistry            m_repmsgs;
    private final HashAlgorithm                     m_msgdig;

    private BasicMessageDigestionStrategy.Variant   m_digstrat = Variant.Plain;

    private boolean                 m_isactive = false;

    private String                  m_bench    = "";


    public Mapping(NetworkMessageRegistry climsgs, NetworkMessageRegistry repmsgs, HashAlgorithm msgdig)
    {
        m_climsgs = Objects.requireNonNull( climsgs );
        m_repmsgs = Objects.requireNonNull( repmsgs );
        m_msgdig  = Objects.requireNonNull( msgdig );
    }


    public Mapping load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        String digstratname = reader.getString( "crypto.digestion_strategy", null );
        m_bench = reader.getString("benchmark.application", "zero");

        if( digstratname!=null )
        {
            switch( digstratname )
            {
            case "plain":
                m_digstrat = BasicMessageDigestionStrategy.Variant.Plain;
                break;
            case "content":
                m_digstrat = BasicMessageDigestionStrategy.Variant.DigestedContent;
                break;
            case "message":
                m_digstrat = BasicMessageDigestionStrategy.Variant.DigestedMessage;
                break;
            case "content_and_message":
                m_digstrat = BasicMessageDigestionStrategy.Variant.DigestedMessageOverDigestedContent;
                break;
            default:
                throw new IllegalArgumentException( "Digestion strategy unknown: " + digstratname );
            }
        }

        return this;
    }


    public Mapping activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    public BasicMessageMapper createClientMessageMapper()
    {
        Preconditions.checkState( m_isactive );

        switch ( m_bench )
        {
        case "zero":
            return new BasicMessageMapper( m_climsgs, m_digstrat, m_msgdig );
        case "http":
            return new HTTPMessageMapper( m_climsgs, m_digstrat, m_msgdig );
//            return new BasicMessageMapper( m_climsgs, m_digstrat, m_msgdig );
        default:
            throw new IllegalArgumentException( "Benchmark unknown: " + m_bench );
        }
    }


    public BasicMessageMapper createReplicaMessageMapper()
    {
        Preconditions.checkState( m_isactive );

        return new BasicMessageMapper( m_repmsgs, m_digstrat, m_msgdig );
    }


    public BasicMessageDigestionStrategy.Variant getDigestionStrategy()
    {
        return m_digstrat;
    }


    public HashAlgorithm getDigestionAlgorithm()
    {
        return m_msgdig;
    }

}
