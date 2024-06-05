#ifndef _COUNTER_ENDIAN_H_
#define _COUNTER_ENDIAN_H_


template<typename T> static void put_bigendian(uint8_t *dst, T val)
{
    dst += sizeof( T )-1;

    for( size_t i=0; i<sizeof( T ); i++, dst--, val>>=8 )
        *dst = (val & 0xFF);
}


template<typename T> static T get_bigendian(const uint8_t *src)
{
    T val = 0;

    for( size_t i=0; i<sizeof( T ); i++, src++ )
        val = ( val<<8 ) + *src;

    return val;
}


#endif /* _COUNTER_ENDIAN_H_ */
