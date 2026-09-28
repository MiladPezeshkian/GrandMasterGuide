# Stockfish engine sources

This folder contains an unmodified copy of the `src/` directory of
[Stockfish 19](https://github.com/official-stockfish/Stockfish/tree/sf_19)
(tag `sf_19`, commit `edb0d9db6731067ec50ce619ff372b463bc4dd5d`).

Stockfish is free software licensed under the **GNU General Public License v3**
(see `Copying.txt`); the authors are listed in `AUTHORS`.

Zorix Chess builds these sources into a standalone executable
(`libstockfish.so` / `libstockfish_dotprod.so`) with the Android NDK and talks to it
over the UCI protocol, exactly like the desktop version talks to `stockfish.exe`.
The build is done by the `buildStockfish` Gradle task (see `buildSrc/`).

## Neural network (NNUE)

The engine needs the network named in `src/evaluate.h`
(`EvalFileDefaultName`, currently `nn-1a298aa575a0.nnue`, ~94 MB).
The Gradle task `downloadStockfishNet` downloads it **once** into `nets/`
(ignored by git) and packs it into the APK as an asset, so the app works
completely **offline**.

If your build machine has no internet access, download the file manually from
`https://tests.stockfishchess.org/api/nn/nn-1a298aa575a0.nnue` and put it in
`android/stockfish/nets/`.

## Updating Stockfish

Replace `src/` with the `src/` folder of a newer Stockfish release. The build reads
the source list from `src/Makefile` and the network name from `src/evaluate.h`,
so no build script changes are needed.
