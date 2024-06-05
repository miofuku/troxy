package reptor.distrbt.com;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedBytes;

import reptor.chronos.ImmutableObject;


public class NetworkMessageRegistry implements ImmutableObject
{

    public static class NetworkMessageRegistryBuilder
    {
        private ArrayList<TypeEntry> m_entries = new ArrayList<>();

        public NetworkMessageRegistryBuilder addMessageType(int msgtypeid, TypedMessageDeserializer deserializer)
        {
            Preconditions.checkState( m_entries.size()<256 );

            m_entries.add( new TypeEntry( msgtypeid, deserializer ) );

            return this;
        }

        public NetworkMessageRegistry createRegistry()
        {
            Preconditions.checkState( !m_entries.isEmpty() );

            TypeEntry[] msgtypes = m_entries.toArray( new TypeEntry[ m_entries.size() ] );

            NetworkMessageRegistry msgreg = new NetworkMessageRegistry( msgtypes );

            m_entries.clear();

            return msgreg;
        }
    }


    protected static class TypeEntry
    {
        private final int                      m_msgtypeid;
        private final TypedMessageDeserializer m_deserializer;

        public TypeEntry(int msgtypeid, TypedMessageDeserializer deserializer)
        {
            m_msgtypeid    = msgtypeid;
            m_deserializer = deserializer;
        }

        public int getTypeID()
        {
            return m_msgtypeid;
        }

        public TypedMessageDeserializer getDeserializer()
        {
            return m_deserializer;
        }
    }


    protected final TypeEntry[]        m_msgtypes;
    // TODO: Native - FastUtil or Koloboke
    protected final Map<Integer, Byte> m_magics;


    protected NetworkMessageRegistry(TypeEntry[] msgtypes)
    {
        m_msgtypes = msgtypes;
        m_magics   = new HashMap<>( msgtypes.length );

        for( int magic=0; magic<msgtypes.length; magic++ )
            m_magics.put( msgtypes[ magic ].getTypeID(), (byte) magic );
    }


    public int capacity()
    {
        return m_msgtypes.length;
    }


    public NetworkMessage createMessage(byte magic, ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
    {
        return typeDeserializer( magic ).readMessageFrom( in, mapper, extcntxt );
    }


    public int typeID(byte magic) throws IOException
    {
        return entry( magic ).getTypeID();
    }


    public TypedMessageDeserializer typeDeserializer(byte magic) throws IOException
    {
        return entry( magic ).getDeserializer();
    }


    public byte magic(int msgtypeid)
    {
        return m_magics.get( msgtypeid );
    }


    private TypeEntry entry(byte magic) throws IOException
    {
        int idx = UnsignedBytes.toInt( magic );

        if( idx>=m_msgtypes.length )
            throw new IOException();

        TypeEntry entry = m_msgtypes[ idx ];

        if( entry==null )
            throw new IOException();

        return entry;
    }

}
