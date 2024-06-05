package reptor.replct.common.modules;

import reptor.replct.common.settings.SettingsReader;


public enum WorkerRoutingMode
{

    STANDALONE,
    DIRECT,
    INDIRECT;

    public static WorkerRoutingMode load(SettingsReader reader, String key, WorkerRoutingMode defmode)
    {
        String routename = reader.getString( key, null );

        if( routename==null )
            return defmode;
        else
        {
            switch( routename )
            {
            case "standalone":
                return WorkerRoutingMode.STANDALONE;
            case "direct":
                return WorkerRoutingMode.DIRECT;
            case "indirect":
                return WorkerRoutingMode.INDIRECT;
            default:
                throw new IllegalArgumentException( routename );
            }
        }
    }

}
