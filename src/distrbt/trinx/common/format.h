#ifndef _COMMON_FORMAT_H_
#define _COMMON_FORMAT_H_

#include <cstring>


namespace format
{

std::string data_to_hex_string(const uint8_t *data, size_t size);
void        print_hex_string(const uint8_t *data, size_t size);

}


#endif /* _COMMON_FORMAT_H_ */
