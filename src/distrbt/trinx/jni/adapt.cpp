#include "adapt.h"

namespace java
{

jmethodID ByteBuffer::s_array     = 0;
jmethodID ByteBuffer::s_offset    = 0;
jmethodID ByteBuffer::s_position  = 0;
jmethodID ByteBuffer::s_remaining = 0;

jmethodID Data::s_array  = 0;
jmethodID Data::s_offset = 0;
jmethodID Data::s_size   = 0;

/*
jmethodID TrinxCommand::s_type    = 0;
jfieldID  TrinxCommand::s_msgdata = 0;
jfieldID  TrinxCommand::s_buffer  = 0;

jfieldID  CreateCertificate::s_certout = 0;

jfieldID  VerifyCertificate::s_certdata = 0;
*/
}
