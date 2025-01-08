A collection of hacks to get [firefox-translations-models](https://github.com/mozilla/firefox-translations-models/tree/main) working on Android without WASM.

These models run on [bergamot-translator](https://github.com/browsermt/bergamot-translator), which supports compiling to many targets, but apparently Android is not one.


The patches are collected in `mozilla-translate` file.

Right now, I have tested this on `x86-64` and "it works" -- it's not using OpenBLAS, but ONNX, though I think I know how to get OpenBLAS to work.

The bigger issue is that _something_ in the stack is ignoring the requests to **not** require `AVX2`, so the resulting shared library **does** depend on `AVX2`.

This is a problem, because the Android emulator inside Android studio does not support `AVX2` by default. You can be cheeky and run a VM with a good CPU configuration like this

```bash
cd $ANDROID_SDK/emulator
export LD_LIBRARY_PATH=$PWD/lib64:$PWD/lib64/qt/lib
$ANDROID_SDK/emulator/qemu/linux-x86_64/qemu-system-x86_64 -netdelay none -netspeed full -avd Medium_Phone_API_35 -qt-hide-window -grpc-use-token -idle-grpc-timeout 300 -qemu -cpu max
# The important bit is
# `-qemu -cpu max`
```

If you don't do this, you will just get a `SIGILL` when trying to load the library.


The very bare app takes some (hardcoded) text in Spanish and translates it to English. Translation takes ~200ms in the emulator, which is not representative of anything

![](https://raw.github.com/davidventura/firefox-translator/master/screenshots/working.jpg)
