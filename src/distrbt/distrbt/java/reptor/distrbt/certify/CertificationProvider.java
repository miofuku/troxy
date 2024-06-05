package reptor.distrbt.certify;

import java.util.Collection;

import reptor.chronos.Commutative;

@Commutative
public interface CertificationProvider<K>
{

    ConnectionCertifier      createUnicastCertifier(K key);
    GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends K> keys);
    GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends K> keys);
    ConnectionCertifier      createNtoICertifier(int locidx, int nprocs, K key);

}
