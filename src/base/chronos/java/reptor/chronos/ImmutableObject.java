package reptor.chronos;

// This declares a complete type as immutable. That is: All implemented interfaces/types have to be immutable.
// Contrary, the @Immutable annotation declares only an immutable view of an object. Other views/types can
// change state.
@Immutable
@Commutative
public interface ImmutableObject extends MultiDomainObject
{

}
