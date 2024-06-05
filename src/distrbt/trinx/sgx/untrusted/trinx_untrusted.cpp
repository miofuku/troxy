#include <iostream>
#include "trinx_u.h"


void ocall_print_string(const char* str)
{
    std::cout << str << std::endl;
}

void ocall_print_value(const char* str, uint64_t value)
{
    std::cout << str << value << std::endl;
}
