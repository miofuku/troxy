#include <iomanip>
#include <sstream>
#include <iostream>

#include "format.h"

namespace format
{

std::string data_to_hex_string(const uint8_t *data, size_t size)
{
    std::ostringstream ss;
    ss << std::hex << std::setfill( '0' );

    for( size_t i=0; i<size; i++ )
    {
        if( i%8==0 && i && i+1<size )
            ss << "_";

        ss << std::setw( 2 ) << (int) data[ i ];
    }

    return ss.str();
}


void print_hex_string(const uint8_t *data, size_t size)
{
    std::cout << data_to_hex_string( data, size ) << std::endl;
}

}
