package reptor.tbft.compose;

import java.lang.reflect.Constructor;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.replct.common.settings.SettingsReader;
import reptor.tbft.TroxyImplementation;
import reptor.tbft.invoke.TransBFTInvocation;
import reptor.tbft.jtroxy.JavaTroxyImplementation;

public class Troxies
{

    private final TransBFTInvocation    m_invoke;

    private TroxyImplementation         m_troxyimpl;
    private boolean                     m_isactive;


    public Troxies(TransBFTInvocation invoke)
    {
        m_invoke = Objects.requireNonNull( invoke );
    }


    public Troxies load(SettingsReader reader)
    {
        String troxyimpl = reader.getString( "tbft.troxy", "jtroxy" );

        switch( troxyimpl )
        {
        case "jtroxy":
            m_troxyimpl = new JavaTroxyImplementation( m_invoke );
            break;
        case "ctroxy":
            try
            {
                Class<? extends TroxyImplementation> ctroxycls = (Class<? extends TroxyImplementation>) Class.forName("reptor.tbftc.ctroxy.CTroxyImplementation");
                Constructor<? extends TroxyImplementation> ctroxyconstr = ctroxycls.getConstructor(TransBFTInvocation.class, boolean.class);
                m_troxyimpl = ctroxyconstr.newInstance(m_invoke, false);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                System.exit(-1);
            }
            break;
        case "etroxy":
            try
            {
                Class<? extends TroxyImplementation> ctroxycls = (Class<? extends TroxyImplementation>) Class.forName("reptor.tbftc.ctroxy.CTroxyImplementation");
                Constructor<? extends TroxyImplementation> ctroxyconstr = ctroxycls.getConstructor(TransBFTInvocation.class, boolean.class);
                m_troxyimpl = ctroxyconstr.newInstance(m_invoke, true);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                System.exit(-1);
            }
            break;
        default:
            throw new IllegalArgumentException( "Unknown Troxy implementation " + troxyimpl );
        }

        return this;
    }


    public Troxies activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    public TroxyImplementation getTroxyImplementation()
    {
        return m_troxyimpl;
    }

}
