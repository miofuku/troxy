package reptor.chronos.orphics;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.CommunicationEndpoint;

// Actors can be typed or untyped, at least they are asynchronous.
public interface Actor extends ChronosTask, CommunicationEndpoint
{

}
