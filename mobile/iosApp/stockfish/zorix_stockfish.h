// Zorix Chess - in-app Stockfish bridge for iOS.
// iOS does not allow launching a separate engine process, so Stockfish runs on a
// background thread inside the app and its stdin/stdout are connected to pipes.
#ifndef ZORIX_STOCKFISH_H
#define ZORIX_STOCKFISH_H

#ifdef __cplusplus
extern "C" {
#endif

/** Starts the engine thread once. Returns 0 on success. Safe to call repeatedly. */
int zorix_sf_start(void);

/** Sends one UCI command (without newline). Returns 0 on success. */
int zorix_sf_send(const char* line);

/** Blocks until the next engine output line is available; returns its length or -1 when the engine ended. */
int zorix_sf_read_line(char* buffer, int capacity);

/** 1 while the engine thread is running. */
int zorix_sf_alive(void);

#ifdef __cplusplus
}
#endif

#endif
