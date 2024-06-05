package reptor.replct.common;


public interface WorkDistribution
{

    public interface WorkIterator
    {
        long nextUnit();
    }


    int             getPeriodLength();
    int             getStageForUnit(long unit);
    long            getSlotForUnit(int stage, long unit);
    long            getSlotForLocalUnit(int stage, long locunit);
    long            getUnitForSlot(int stage, long slot);
    long            getNextLocalUnit(int stage, long unit);
    WorkIterator    getUnitIterator(int stage, long startslot);


    public static final class Continuous implements WorkDistribution
    {
        public static final class ContIterator implements WorkIterator
        {
            private long m_curunit;

            public ContIterator(long startlocunit)
            {
                m_curunit = startlocunit;
            }

            @Override
            public long nextUnit()
            {
                return m_curunit++;
            }
        }

        private final int m_stage;

        public Continuous(int stage)
        {
            m_stage = stage;
        }

        @Override
        public int getPeriodLength()
        {
            return 1;
        }

        @Override
        public int getStageForUnit(long unit)
        {
            return m_stage;
        }

        @Override
        public long getSlotForUnit(int stage, long unit)
        {
            return unit;
        }

        @Override
        public long getSlotForLocalUnit(int stage, long locunit)
        {
            return locunit;
        }

        @Override
        public long getUnitForSlot(int stage, long slot)
        {
            return slot;
        }

        @Override
        public long getNextLocalUnit(int stage, long unit)
        {
            return unit;
        }

        @Override
        public WorkIterator getUnitIterator(int stage, long startslot)
        {
            return new ContIterator( startslot );
        }
    }


    public static final class RoundRobin implements WorkDistribution
    {
        public static final class RoundRobinIterator implements WorkIterator
        {
            private final int   NS;

            private long        m_curunit;

            public RoundRobinIterator(int nstages, long startlocunit)
            {
                NS = nstages;

                m_curunit = startlocunit - NS;
            }

            @Override
            public long nextUnit()
            {
                return m_curunit += NS;
            }
        }

        private final int NS;

        public RoundRobin(int nstages)
        {
            NS = nstages;
        }

        @Override
        public int getPeriodLength()
        {
            return NS;
        }

        @Override
        public int getStageForUnit(long unit)
        {
            return (int) ( unit % NS );
        }

        @Override
        public long getSlotForUnit(int stage, long unit)
        {
            return ( unit + NS - 1 - stage )/NS;
        }

        @Override
        public long getSlotForLocalUnit(int stage, long locunit)
        {
            return locunit / NS;
        }

        @Override
        public long getUnitForSlot(int stage, long slot)
        {
            return slot*NS + stage;
        }

        @Override
        public long getNextLocalUnit(int stage, long unit)
        {
            // -- Alt 1: base = unit - ( unit % NS ) + stage; base>=unit ? base : base + NS;
            // -- Alt 2: ( unit + NS-1 - stage )/NS*NS + unit;
            return unit + NS - 1 - ( unit + NS - 1 - stage ) % NS;
        }

        @Override
        public WorkIterator getUnitIterator(int stage, long startslot)
        {
            return new RoundRobinIterator( NS, getUnitForSlot( stage, startslot ) );
        }
    }


    public static final class Blockwise implements WorkDistribution
    {
        public static final class BlockIterator implements WorkIterator
        {
            private final int   NS;
            private final int   BS;

            private long m_curunit;
            private int  m_curpos;

            public BlockIterator(int nstages, int blocksize, long startlocunit)
            {
                NS = nstages;
                BS = blocksize;

                m_curunit = startlocunit - 1;
                m_curpos  = (int) ( startlocunit % BS ) - 1;
            }

            @Override
            public long nextUnit()
            {
                if( ++m_curpos < BS )
                    return ++m_curunit;
                else
                {
                    m_curpos = 0;
                    return m_curunit += BS*( NS - 1 ) + 1;
                }
            }
        }

        private final int NS;
        private final int BS;

        public Blockwise(int nstages, int blocksize)
        {
            NS = nstages;
            BS = blocksize;
        }

        @Override
        public int getPeriodLength()
        {
            return BS * NS;
        }

        @Override
        public int getStageForUnit(long unit)
        {
            return (int) (( unit / BS ) % NS );
        }

        @Override
        public long getSlotForLocalUnit(int stage, long locunit)
        {
            return ( locunit / ( BS * NS ))*BS + ( locunit % BS );
        }

        @Override
        public long getSlotForUnit(int stage, long unit)
        {
            return ( unit + BS*( NS - 1 - stage ))/( BS * NS )*BS + (( unit / BS ) % NS == stage ? unit % BS : 0 );
        }

        @Override
        public long getUnitForSlot(int stage, long slot)
        {
            return ( slot / BS )*BS*NS + ( slot % BS ) + stage*BS;
        }

        @Override
        public long getNextLocalUnit(int stage, long unit)
        {
            long base = ( unit + BS*( NS - 1 - stage ))/( BS * NS )*BS*NS + stage*BS;
            return Math.max( base, unit );
        }

        @Override
        public WorkIterator getUnitIterator(int stage, long startslot)
        {
            return new BlockIterator( NS, BS, getUnitForSlot( stage, startslot ) );
        }
    }


    public static final class Skewed implements WorkDistribution
    {
        public static final class SkewedIterator implements WorkIterator
        {
            private final int   NS;

            private long m_curunit;
            private int  m_curpos;

            public SkewedIterator(int nstages, int stage, long startlocunit, long startslot)
            {
                NS = nstages;

                m_curunit = startlocunit - NS + 1;
                m_curpos  = (int) (( stage - startslot ) % NS + NS ) % NS + 1;
            }

            @Override
            public long nextUnit()
            {
                if( m_curpos-- == 0 )
                {
                    m_curpos   = NS - 1;
                    m_curunit += NS;
                }

                return m_curunit += NS - 1;
            }
        }

        private final int NS;

        public Skewed(int nstages)
        {
            NS = nstages;
        }

        @Override
        public int getPeriodLength()
        {
            return NS * NS;
        }

        @Override
        public int getStageForUnit(long unit)
        {
            return (int) (( unit/NS + unit % NS ) % NS );
        }

        @Override
        public long getSlotForUnit(int stage, long unit)
        {
            return ( unit + NS - 1 - (( NS - unit/NS + stage ) % NS + NS ) % NS )/NS;
        }

        @Override
        public long getSlotForLocalUnit(int stage, long locunit)
        {
            return locunit / NS;
        }

        @Override
        public long getUnitForSlot(int stage, long slot)
        {
            return slot*NS + ( NS - ( slot - stage ) % NS ) % NS;
        }

        @Override
        public long getNextLocalUnit(int stage, long unit)
        {
            long l = ( unit / NS )*NS + ( NS - (( unit / NS ) - stage ) % NS ) % NS;

            return unit<=l ? l : (( unit + NS - 1 )/NS )*NS + ( NS - ((( unit + NS - 1 ) / NS ) - stage ) % NS ) % NS;
        }

        @Override
        public WorkIterator getUnitIterator(int stage, long startslot)
        {
            return new SkewedIterator( NS, stage, getUnitForSlot( stage, startslot ), startslot );
        }
    }

}
