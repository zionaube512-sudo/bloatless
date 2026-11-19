#!/data/data/com.termux/files/usr/bin/bash
set -e
cd ~/bloatless
rm -rf build/gen/* build/obj/* build/apk/* build/res.zip build/unsigned.apk build/aligned.apk bloatless-zion-obi.apk
: "${ANDROID_JAR:=$HOME/android-sdk/platforms/android-36/android.jar}"
: "${KEYSTORE:=$HOME/keystore.jks}"
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml build/res.zip --java build/gen
find src build/gen -name "*.java" > build/sources.list
javac -cp "$ANDROID_JAR" -d build/obj @build/sources.list
d8 --dex --output build/apk build/obj
cp build/unsigned.apk build/aligned.apk
(cd build/apk && zip -u ../aligned.apk classes.dex)
apksigner sign --ks "$KEYSTORE" --out bloatless-zion-obi.apk build/aligned.apk
echo "BUILT: $(pwd)/bloatless-zion-obi.apk"
