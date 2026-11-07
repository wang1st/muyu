set -e
cd /sdcard/Download/woodfish
rm -rf build && mkdir -p build && cd build
tar xzf ../src.tar.gz
ANDROID_JAR=../sdk/android.jar
mkdir -p gen
aapt package -f -M AndroidManifest.xml -S res -A assets -I $ANDROID_JAR -J gen \
  -F base.apk --version-code 2 --version-name 1.1
find java gen -name '*.java' > sources.txt
javac --release 8 -cp $ANDROID_JAR -d classes @sources.txt
dx --dex --output=classes.dex classes
zip -q base.apk classes.dex
if [ ! -f ../woodfish.jks ]; then
  keytool -genkeypair -keystore ../woodfish.jks -storepass minis123 -keypass minis123 \
    -alias minis -keyalg RSA -keysize 2048 -validity 3650 \
    -dname "CN=Minis Woodfish" >/dev/null 2>&1
fi
apksigner sign --ks ../woodfish.jks --ks-pass pass:minis123 --out woodfish.apk base.apk
apksigner verify --print-certs woodfish.apk | head -3
ls -l woodfish.apk
cp woodfish.apk ../woodfish.apk
echo BUILD_OK
