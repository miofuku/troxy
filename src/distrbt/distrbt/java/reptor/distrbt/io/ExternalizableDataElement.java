package reptor.distrbt.io;

import java.nio.ByteBuffer;

import reptor.chronos.Notifying;


public interface ExternalizableDataElement
{

    int         getStateSize();

    void        saveState(ByteBuffer dst);
    @Notifying
    void        installState(ByteBuffer src);

    default ByteBuffer saveState()
    {
        int size = getStateSize();

        if( size==0 )
            return null;

        ByteBuffer state = ByteBuffer.allocate( size );
        saveState( state );
        state.flip();

        return state;
    }

    default boolean installStateIfRequired(ByteBuffer state)
    {
        if( state==null || state.remaining()==0 )
            return false;
        else
        {
            installState( state );

            return true;
        }
    }

}
