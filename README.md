A basic Android translator app that combines [firefox-translations-models](https://github.com/mozilla/firefox-translations-models/tree/main) with [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android),
to perform on-device translation of text and images.

There's automatic language detection as well, with [cld2](https://github.com/CLD2Owners/cld2).

The translation models run on [bergamot-translator](https://github.com/browsermt/bergamot-translator).

<img src="https://raw.github.com/davidventura/firefox-translator/master/screenshots/app-dark.png" width="400">


## Image translation overlay

The app also translates images directly on top of the previous content, similar to Google Translate:

Original | New
:-------:|:----:
![](https://raw.github.com/davidventura/firefox-translator/master/screenshots/original-image.png) | ![](https://raw.github.com/davidventura/firefox-translator/master/screenshots/translated-image.png)
![](https://raw.github.com/davidventura/firefox-translator/master/screenshots/kindle.jpg) | ![](https://raw.github.com/davidventura/firefox-translator/master/screenshots/translated-kindle.png)

It tries to detect background and foreground colors to blend in better with the original text, but it's not perfect.


## Running on x86-64 emulator

This app works fine on aarch64, and it "works" on x86-64 -- in quotes because it currently requires `AVX2`, which is not available on the standard emulator, nor in the ABI.

You can be cheeky and run a VM with a good CPU configuration like this

```bash
cd $ANDROID_SDK/emulator
export LD_LIBRARY_PATH=$PWD/lib64:$PWD/lib64/qt/lib
$ANDROID_SDK/emulator/qemu/linux-x86_64/qemu-system-x86_64 -netdelay none -netspeed full -avd Medium_Phone_API_35 -qt-hide-window -grpc-use-token -idle-grpc-timeout 300 -qemu -cpu max
# The important bit is
# `-qemu -cpu max`
```

If you don't do this, you will just get a `SIGILL` when trying to load the library.
