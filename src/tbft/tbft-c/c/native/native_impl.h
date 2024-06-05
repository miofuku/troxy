/**
 * @author weichbr
 */

#ifndef TBFT_C_NATIVE_IMPL_H
#define TBFT_C_NATIVE_IMPL_H

#include <vector>
#include <set>
#include <map>
#include <queue>
#include "CTroxy.h"
#include "quorum.h"
#include "protocolparser.h"
#include <openssl/hmac.h>
#include <openssl/ssl.h>

#ifndef SGX
#include <tuple>
#endif

#define APP_HANDSHAKE_IN_BYTES 5
#define APP_HANDSHAKE_OUT_BYTES 1

// Different protocol parsers
#define PROTOCOL_DEFAULT 1
#define PROTOCOL_HTTP 2

#define USED_PROTOCOL PROTOCOL_HTTP // PROTOCOL_DEFAULT, PROTOCOL_HTTP

#define WITH_CACHE 1

enum class handshake_state_t
{
	NONE,
	SSL,
	APP_P1,
	APP_P2,
	DONE
};

typedef struct __shared_data
{
	SSL *ssl;
	BIO *bio;
	int16_t clino;
	int32_t to_recv;
	buffer_t *recvbuf;
	//int32_t to_send;
	buffer_t *sendbuf;
} shared_data_t;

typedef struct __handshake_data
{
	shared_data_t data;
	handshake_state_t state;
} handshake_data_t;

typedef struct __connection_data
{
	shared_data_t data;
	int64_t lastinv;
	int64_t nextinv;
	bool i_am_contact;
	HMAC_CTX hmac_ctx;
	std::vector<QuorumCollector *> collectors;
	ProtocolParser *pp;
	uint8_t _lock;
	std::set<uint64_t> read_reqs;
	std::map<uint64_t, buffer_t *> reqs;
	std::map<uint64_t, buffer_t *> pending;
	uint64_t  nextres = 1;
	std::priority_queue<uint64_t, std::vector<uint64_t>, std::greater<uint64_t>> complete;
} connection_data_t;

class NativeTroxy : public CTroxy
{
public:
	NativeTroxy(troxy_conf_t &conf);
	virtual ~NativeTroxy() {};
	virtual void reset_handshake(handshake_result_t &result, uint16_t hsno, bool clear);
	virtual void accept(handshake_result_t &result, uint16_t hsno);
	virtual void get_handshake_inbound_minimum_buffer_size(int *result, uint16_t hsno);
	virtual void get_handshake_outbound_minimum_buffer_size(int *result, uint16_t hsno);
	virtual void process_handshake_inbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &source);
	virtual void retrieve_handshake_outbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &destination);
	virtual void save_state(short hsno);
	virtual void open(client_result_t &result, uint16_t clino);
	virtual void init_client(uint16_t clino, buffer_t *sendbuffer);
	virtual void get_client_inbound_minimum_buffer_size(int *result, uint16_t clino);
	virtual void get_client_outbound_minimum_buffer_size(int *result, uint16_t clino);
	virtual void process_client_inbound_data(client_result_t &result, uint16_t clino, buffer_t &src, buffer_t &dest);
	virtual void retrieve_client_outbound_data(client_result_t &result, uint16_t clino, buffer_t &dst);
	virtual void retrieve_outbound_messages(client_result_t &result, uint16_t clino);
	virtual void handle_forwarded_request(client_result_t &cliresult, verification_result_t &result, uint16_t clino, buffer_t &request);
	virtual void handle_request_executed(client_result_t &result, uint16_t clino, uint64_t invno, buffer_t &reply, bool replyfull);
	virtual bool handle_reply(client_result_t &result, uint16_t clino, buffer_t &reply);
	void verify_proposal(verification_result_t &result, uint32_t verifier, buffer_t &to_verify);
	void verify_proposals(verification_result_t &result, uint32_t verifier, buffer_t &to_verify);

	void savePointer(uint16_t clino, uint8_t type, uint8_t *ptr);
	uint8_t* getPointer(uint16_t clino, uint8_t type);

	int readBuffer(shared_data_t &sdata, buffer_t &dest, uint32_t len);
	int sendBuffer(shared_data_t &sdata, buffer_t &src);

	uint32_t get_hash_size() { return hash_size; }

	void addReadRequest(uint16_t clino, uint64_t invno, buffer_t *req);
	bool isReadRequest(uint16_t clino, uint64_t invno);
	void deleteRequest(uint16_t clino, uint64_t invno);

	void add_hmac_to_request(HMAC_CTX *ctx, buffer_t &req, uint32_t start, uint32_t pkt_len);

	void getResend(uint16_t clino, client_result_t &result, uint64_t nextres);
	std::map<uint64_t, buffer_t *>::iterator getIterator(connection_data_t &condata, uint64_t current);
	uint64_t clearComplete(uint16_t clino, uint64_t next);

private:
	uint8_t replica_number;
	uint32_t client_number_offset;
	uint32_t inv_window;
	bool distributed_contacts;

	ptr_data_t *client_pointers;

	// Handshake related stuff
	std::vector<handshake_data_t> handshakes;
	bool use_app_handshake;
	uint16_t next_clino;

	// Connection related stuff
	std::vector<connection_data_t> connections;
	bool use_ssl;

	// Verifiers
	std::vector<HMAC_CTX> verifiers;

	// Read-opt
	void lock(uint8_t *lock);
	void unlock(uint8_t *lock);

	// SSL stuff
	SSL_CTX *ssl_ctx;

	// HMAC
	const EVP_MD *hash_function;
	uint32_t hash_size;
	const char *key = "SECRET";
	uint32_t key_size = 6;

	uint16_t next_client_number();

	bool verify_message(buffer_t &msg, HMAC_CTX *ctx);

	int readBytesSSL(shared_data_t &sdata, buffer_t &dest, uint32_t toread);
	int readBytesSSL(shared_data_t &sdata, buffer_t &dest);
	int writeBytesSSL(shared_data_t &sdata, buffer_t &src, uint32_t towrite);
	int writeBytesSSL(shared_data_t &sdata, buffer_t &src);

	uint64_t isconflict = 0;
	uint64_t readrequest = 0;
	bool usereadopt = true;
};

#endif //TBFT_C_NATIVE_IMPL_H
