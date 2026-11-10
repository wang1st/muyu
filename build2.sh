set -e
SD=/sdcard/Download/woodfish
H=$HOME/wb_$(date +%s)
mkdir -p $H && cd $H
tar xzf $SD/src.tar.gz
cp $SD/sdk/android.jar .
mkdir -p gen
aapt package -f -M AndroidManifest.xml -S res -A assets -I android.jar -J gen -F base.apk
find java gen -name '*.java' > sources.txt
javac --release 8 -cp android.jar -d classes @sources.txt
dx --dex --output=classes.dex classes
zip -q base.apk classes.dex
cp $SD/woodfish.jks .
apksigner sign --ks woodfish.jks --ks-pass pass:minis123 --out woodfish.apk base.apk
cp woodfish.apk $SD/woodfish.apk
cd / && rm -rf $H
echo BUILD_OK
