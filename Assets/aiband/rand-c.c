// https://en.wikipedia.org/wiki/Xorshift
// https://stackoverflow.com/questions/9225567/how-to-print-a-int64-t-type-in-c

#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

uint64_t x; /* The state must be seeded with a nonzero value. */

uint64_t xorshift64star(void) {
	x ^= x >> 12; // a
	x ^= x << 25; // b
	x ^= x >> 27; // c
	return x * UINT64_C(2685821657736338717);
}

int main() {
	x = 8675309;
	for (int i = 0; i < 10; i++) {
		printf("%" PRIu64 "\n", xorshift64star());
	}
	return 0;
}
