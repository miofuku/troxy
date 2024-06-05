package reptor.test.distrbt.trusted;

import java.security.MessageDigest;

import reptor.jlib.hash.HashAlgorithm;
import reptor.test.bench.MultiCoreTestObject;


public class HashBenchmark extends MessageBenchmark
{

    private final HashAlgorithm m_hashalgo;


    public HashBenchmark(HashAlgorithm hashalgo)
    {
        m_hashalgo = hashalgo;
    }


    @Override
    public MultiCoreTestObject apply(int value)
    {
        return new Hash( m_hashalgo.digester(), getMessageSize() );
    }


    private static class Hash implements MultiCoreTestObject
    {
        private final MessageDigest m_digester;
        private final byte[]        m_data;

        public Hash(MessageDigest digester, int msgsize)
        {
            m_digester = digester;
            m_data     = new byte[ msgsize ];
        }

        @Override
        public void invoke()
        {
            m_digester.digest( m_data );
        }
    }

}
