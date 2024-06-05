package reptor.replct.secure;

import reptor.replct.common.modules.AbstractProtocolComponent;
import reptor.replct.common.settings.SettingsReader;


public class Certification extends AbstractProtocolComponent
{

    private boolean m_verify = false;


    public Certification()
    {
        m_nworkers = 0;
    }


    public Certification load(SettingsReader reader)
    {
        loadBasicSettings( reader, "workr" );

        m_verify = reader.getBool( getKey( "workr", "use_for_verification" ), m_verify );

        return this;
    }


    @Override
    public Certification activate()
    {
        super.activate();

        return this;
    }

    public boolean getUseForVerification()
    {
        return m_verify;
    }

}
