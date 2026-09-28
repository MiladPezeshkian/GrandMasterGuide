// Zorix Chess - in-app Stockfish bridge for iOS (see zorix_stockfish.h).
#include "zorix_stockfish.h"

#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <pthread.h>
#include <string>
#include <unistd.h>

// Stockfish's main(), renamed at compile time with -Dmain=zorix_stockfish_main.
int zorix_stockfish_main(int argc, char* argv[]);

namespace {

int              toEngine   = -1;  // write end of the engine's stdin
int              fromEngine = -1;  // read end of the engine's stdout
std::atomic<int> running{0};
std::mutex       writeMutex;
std::string      pending;          // bytes read but not yet returned as a line
std::once_flag   startOnce;
int              startResult = -1;

void* engineThread(void*) {
    char  name[] = "stockfish";
    char* argv[] = {name, nullptr};
    zorix_stockfish_main(1, argv);
    running = 0;
    return nullptr;
}

void start() {
    int in[2], out[2];
    if (pipe(in) != 0 || pipe(out) != 0)
        return;
    fflush(stdout);
    if (dup2(in[0], STDIN_FILENO) < 0 || dup2(out[1], STDOUT_FILENO) < 0)
        return;
    close(in[0]);
    close(out[1]);
    toEngine   = in[1];
    fromEngine = out[0];
    // stdout is a pipe now: keep it line buffered so every UCI line arrives immediately.
    setvbuf(stdout, nullptr, _IOLBF, 0);

    // Stockfish's UCI thread gets a macOS-sized 8 MB stack (iOS secondary threads default to 512 KB).
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 8 * 1024 * 1024);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_t thread;
    running = 1;
    if (pthread_create(&thread, &attr, engineThread, nullptr) != 0) {
        running = 0;
        pthread_attr_destroy(&attr);
        return;
    }
    pthread_attr_destroy(&attr);
    startResult = 0;
}

}  // namespace

extern "C" int zorix_sf_start(void) {
    std::call_once(startOnce, start);
    return startResult;
}

extern "C" int zorix_sf_send(const char* line) {
    if (toEngine < 0 || !running)
        return -1;
    std::string data(line);
    data.push_back('\n');
    std::lock_guard<std::mutex> lock(writeMutex);
    size_t written = 0;
    while (written < data.size()) {
        ssize_t n = write(toEngine, data.data() + written, data.size() - written);
        if (n < 0) {
            if (errno == EINTR)
                continue;
            return -1;
        }
        written += size_t(n);
    }
    return 0;
}

extern "C" int zorix_sf_read_line(char* buffer, int capacity) {
    if (fromEngine < 0 || capacity <= 0)
        return -1;
    while (true) {
        size_t newline = pending.find('\n');
        if (newline != std::string::npos) {
            size_t length = newline;
            if (length > 0 && pending[length - 1] == '\r')
                length--;
            size_t copy = length < size_t(capacity - 1) ? length : size_t(capacity - 1);
            std::memcpy(buffer, pending.data(), copy);
            buffer[copy] = '\0';
            pending.erase(0, newline + 1);
            return int(copy);
        }
        char    chunk[4096];
        ssize_t n = read(fromEngine, chunk, sizeof(chunk));
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            return -1;
        pending.append(chunk, size_t(n));
    }
}

extern "C" int zorix_sf_alive(void) { return running.load(); }
