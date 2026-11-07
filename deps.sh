set -e
echo "== pkg update =="
pkg update -y
echo "== pkg install =="
pkg install -y openjdk-17 aapt dx apksigner zip unzip curl
echo "== sdk =="
mkdir -p /sdcard/Download/woodfish/sdk
cd /sdcard/Download/woodfish/sdk
if [ ! -f android.jar ]; then
  curl -sL -o platform.zip https://dl.google.com/android/repository/platform-36_r01.zip
  unzip -qo platform.zip
  mv android-36/android.jar .
  rm -rf platform.zip android-36
fi
ls -l android.jar
java -version 2>&1 | head -1
aapt version
echo DEPS_OK
