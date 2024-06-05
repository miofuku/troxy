package reptor.distrbt.com.connect;

import reptor.chronos.Immutable;
import reptor.distrbt.com.MessageMapper;


@Immutable
public interface ConnectionConfiguration
{
    NetworkConnectionProvider connectionProvider(MessageMapper mapper);
}
